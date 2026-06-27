package net.lumalyte.armbridge.services;

import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.lg.application.services.GuildVaultService;
import net.lumalyte.lg.application.services.VaultResult;
import net.lumalyte.lg.application.services.WithdrawalInfo;
import net.lumalyte.lg.domain.entities.BankMode;
import net.lumalyte.lg.domain.entities.Guild;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Implementation of PaymentRoutingService
 * Routes shop payments through guild vaults using LumaGuilds' vault system
 */
public class PaymentRoutingServiceImpl implements PaymentRoutingService {

    private final ARMGuildsBridge plugin;
    private final GuildVaultService vaultService;
    private final GuildRegionRepository repository;
    private final double minimumBalanceAfter;

    public PaymentRoutingServiceImpl(ARMGuildsBridge plugin, GuildVaultService vaultService,
                                     GuildRegionRepository repository) {
        this.plugin = plugin;
        this.vaultService = vaultService;
        this.repository = repository;
        this.minimumBalanceAfter = plugin.getConfig().getDouble("shop-purchase.minimum-balance-after", 1000.0);
    }

    @Override
    public WithdrawalResult withdrawFromGuild(UUID guildId, double amount, String reason) {
        try {
            // Get guild
            Guild guild = plugin.getGuildService().getGuild(guildId);
            if (guild == null) {
                return WithdrawalResult.failure("Guild not found");
            }

            BankMode bankMode = resolveBankMode();
            if (wouldViolateMinimumBalance(guild, amount, bankMode)) {
                return WithdrawalResult.failure(
                    "Withdrawal would leave guild balance below minimum required (" + minimumBalanceAfter + ")"
                );
            }

            // Call LumaGuilds' withdrawForShopPurchase method
            VaultResult<WithdrawalInfo> result = vaultService.withdrawForShopPurchase(guild, amount, reason);

            if (result instanceof VaultResult.Success) {
                @SuppressWarnings("unchecked")
                VaultResult.Success<WithdrawalInfo> success = (VaultResult.Success<WithdrawalInfo>) result;
                WithdrawalInfo info = success.getData();

                plugin.getLogger().info("Withdrew " + amount + " from guild " + guild.getName() +
                    " for: " + reason + " (remaining: " + info.getRemainingBalance() + ")");

                return WithdrawalResult.success(info);
            } else if (result instanceof VaultResult.Failure) {
                VaultResult.Failure failure = (VaultResult.Failure) result;
                plugin.getLogger().warning("Failed to withdraw from guild " + guild.getName() +
                    ": " + failure.getMessage());
                return WithdrawalResult.failure(failure.getMessage());
            } else {
                return WithdrawalResult.failure("Unknown vault result type");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error during guild withdrawal: " + e.getMessage());
            e.printStackTrace();
            return WithdrawalResult.failure("Internal error: " + e.getMessage());
        }
    }

    @Override
    public boolean depositToGuild(UUID guildId, double amount, String reason) {
        try {
            // Get guild
            Guild guild = plugin.getGuildService().getGuild(guildId);
            if (guild == null) {
                plugin.getLogger().warning("Cannot deposit - guild not found: " + guildId);
                return false;
            }

            // Use LumaGuilds' depositToVault method
            VaultResult<Double> result = vaultService.depositToVault(guild, amount, reason);

            if (result instanceof VaultResult.Success) {
                @SuppressWarnings("unchecked")
                VaultResult.Success<Double> success = (VaultResult.Success<Double>) result;
                double newBalance = success.getData();

                plugin.getLogger().info("Deposited " + amount + " to guild " + guild.getName() +
                    " for: " + reason + " (new balance: " + newBalance + ")");

                // Log transaction
                repository.logShopTransaction(
                    guildId,
                    "shop_income",
                    "INCOME",
                    amount,
                    reason,
                    null
                );

                return true;
            } else if (result instanceof VaultResult.Failure) {
                VaultResult.Failure failure = (VaultResult.Failure) result;
                plugin.getLogger().warning("Failed to deposit to guild " + guild.getName() +
                    ": " + failure.getMessage());
                return false;
            } else {
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error during guild deposit: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Resolve bank mode from LumaGuilds config (same source as GuildVaultService).
     */
    private BankMode resolveBankMode() {
        org.bukkit.plugin.Plugin lumaGuildsPlugin = plugin.getServer().getPluginManager().getPlugin("LumaGuilds");
        String bankModeStr = "BOTH";
        if (lumaGuildsPlugin != null) {
            bankModeStr = lumaGuildsPlugin.getConfig().getString("vault.bank_mode", "BOTH");
        }
        try {
            return BankMode.valueOf(bankModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid bank mode '" + bankModeStr + "', defaulting to BOTH");
            return BankMode.BOTH;
        }
    }

    private boolean wouldViolateMinimumBalance(Guild guild, double amount, BankMode bankMode) {
        switch (bankMode) {
            case VIRTUAL: {
                double balance = guild.getBankBalance();
                return (balance - amount) < minimumBalanceAfter;
            }
            case PHYSICAL: {
                double balance = getPhysicalBalance(guild);
                return balance < amount || (balance - amount) < minimumBalanceAfter;
            }
            case BOTH: {
                double virtualBalance = guild.getBankBalance();
                if (virtualBalance >= amount) {
                    return (virtualBalance - amount) < minimumBalanceAfter;
                }
                double physicalBalance = getPhysicalBalance(guild);
                return physicalBalance < amount || (physicalBalance - amount) < minimumBalanceAfter;
            }
            default:
                return false;
        }
    }

    private double getPhysicalBalance(Guild guild) {
        Map<Integer, ItemStack> inventory = vaultService.getVaultInventory(guild);
        return calculateRawGoldValue(inventory);
    }

    private double calculateRawGoldValue(Map<Integer, ItemStack> inventory) {
        int total = 0;
        for (ItemStack item : inventory.values()) {
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.RAW_GOLD_BLOCK) {
                total += item.getAmount() * 9;
            } else if (item.getType() == Material.RAW_GOLD) {
                total += item.getAmount();
            }
        }
        return total;
    }
}