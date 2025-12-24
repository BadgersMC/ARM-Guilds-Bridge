package net.lumalyte.armbridge.services;

import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.lg.application.services.GuildVaultService;
import net.lumalyte.lg.application.services.VaultResult;
import net.lumalyte.lg.application.services.WithdrawalInfo;
import net.lumalyte.lg.domain.entities.Guild;

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

            // Call LumaGuilds' withdrawForShopPurchase method
            VaultResult<WithdrawalInfo> result = vaultService.withdrawForShopPurchase(guild, amount, reason);

            if (result instanceof VaultResult.Success) {
                @SuppressWarnings("unchecked")
                VaultResult.Success<WithdrawalInfo> success = (VaultResult.Success<WithdrawalInfo>) result;
                WithdrawalInfo info = success.getData();

                // Check if remaining balance meets minimum requirement
                if (info.getRemainingBalance() < minimumBalanceAfter) {
                    plugin.getLogger().warning("Guild " + guild.getName() + " balance (" +
                        info.getRemainingBalance() + ") below minimum (" + minimumBalanceAfter + ") after withdrawal");
                    // Note: withdrawal already happened, this is just a warning
                }

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
}
