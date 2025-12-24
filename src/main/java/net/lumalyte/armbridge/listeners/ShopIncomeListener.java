package net.lumalyte.armbridge.listeners;

import net.alex9849.arm.events.PostShopTransactionEvent;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.armbridge.storage.ShopRegionInfo;
import net.lumalyte.lg.application.services.GuildService;
import net.lumalyte.lg.application.services.GuildVaultService;
import net.lumalyte.lg.application.services.VaultResult;
import net.lumalyte.lg.domain.entities.BankMode;
import net.lumalyte.lg.domain.entities.Guild;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Handles shop income routing for guild-owned shops.
 * Reroutes income from Vault economy to guild vault service for proper RAW_GOLD conversion in PHYSICAL mode.
 *
 * REQUIRES: ItemShops fork with PostShopTransactionEvent
 */
public class ShopIncomeListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildService guildService;
    private final GuildVaultService guildVaultService;
    private final GuildRegionRepository repository;
    private final Economy economy;

    public ShopIncomeListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.guildService = plugin.getGuildService();
        this.guildVaultService = plugin.getGuildVaultService();
        this.repository = plugin.getGuildRegionRepository();

        // Get Vault economy provider
        this.economy = Bukkit.getServer().getServicesManager().getRegistration(Economy.class).getProvider();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopSale(PostShopTransactionEvent event) {
        UUID landlordId = event.getLandlordId();
        double pricePaid = event.getPricePaid();

        // Check if this is a guild shop
        ShopRegionInfo shopInfo = repository.getShopRegionInfo(event.getRegionId(), event.getWorldName());
        if (shopInfo == null) {
            return; // Not a guild shop
        }

        // Verify landlord matches shop owner
        if (!shopInfo.getGuildId().equals(landlordId)) {
            plugin.getLogger().warning("Shop landlord mismatch! Region " + event.getRegionId() +
                " owned by " + shopInfo.getGuildId() + " but sale credited to " + landlordId);
            return;
        }

        // Get guild
        Guild guild = guildService.getGuild(landlordId);
        if (guild == null) {
            plugin.getLogger().warning("Guild not found for shop income: " + landlordId);
            return;
        }

        // Check if we need to reroute (only needed for PHYSICAL mode)
        // In VIRTUAL mode, Vault economy deposits work fine as-is
        BankMode bankMode = getBankMode(guild);
        if (bankMode == BankMode.VIRTUAL) {
            // Vault economy deposit from ItemShops is sufficient for VIRTUAL mode
            plugin.getLogger().fine("Shop sale income for guild " + guild.getName() +
                " using VIRTUAL mode - no rerouting needed");
            return;
        }

        // PHYSICAL or BOTH mode - need to reroute through guild vault service
        // This ensures proper conversion to RAW_GOLD items

        // Withdraw from Vault economy (where ItemShops deposited it)
        OfflinePlayer landlord = Bukkit.getOfflinePlayer(landlordId);
        if (!economy.has(landlord, pricePaid)) {
            plugin.getLogger().warning("Cannot reroute shop income - landlord doesn't have balance in Vault economy");
            return;
        }

        economy.withdrawPlayer(landlord, pricePaid);
        plugin.getLogger().fine("Withdrew " + pricePaid + " from Vault economy for guild " + guild.getName());

        // Deposit through guild vault service (handles RAW_GOLD conversion for PHYSICAL mode)
        VaultResult<Double> result = guildVaultService.depositToVault(
            guild,
            pricePaid,
            "Shop sale: " + event.getItem().getType().name() + " x" + event.getItem().getAmount()
        );

        if (result.isSuccess()) {
            plugin.getLogger().info("Routed shop income of " + pricePaid + " RAW_GOLD to guild " +
                guild.getName() + " vault (mode: " + bankMode + ")");
        } else {
            // Failed to deposit - refund to Vault economy
            economy.depositPlayer(landlord, pricePaid);
            plugin.getLogger().severe("Failed to deposit shop income to guild vault: " +
                result.getMessageOrNull() + " - refunded to Vault economy");
        }
    }

    /**
     * Get the bank mode for a guild from config
     */
    private BankMode getBankMode(Guild guild) {
        String bankModeStr = plugin.getConfig().getString("vault.bank-mode", "BOTH");
        try {
            return BankMode.valueOf(bankModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid bank mode '" + bankModeStr + "', defaulting to BOTH");
            return BankMode.BOTH;
        }
    }
}
