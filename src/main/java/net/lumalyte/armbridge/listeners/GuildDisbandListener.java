package net.lumalyte.armbridge.listeners;

import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.services.GuildShopService;
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.armbridge.storage.ShopRegionInfo;
import net.lumalyte.lg.domain.events.GuildDisbandedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.UUID;

/**
 * Cleans up guild shop regions when a guild is disbanded.
 * Removes all DB records and clears WorldGuard flags for the dissolved guild.
 */
public class GuildDisbandListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildShopService shopService;
    private final RelationFlagService flagService;
    private final GuildRegionRepository repository;

    public GuildDisbandListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.shopService = plugin.getGuildShopService();
        this.flagService = plugin.getRelationFlagService();
        this.repository = plugin.getGuildRegionRepository();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGuildDisbanded(GuildDisbandedEvent event) {
        UUID guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();

        // Get all shop regions before removal (need region IDs for WG flag cleanup)
        List<ShopRegionInfo> regions = repository.getGuildShopRegions(guildId);

        if (regions.isEmpty()) {
            return;
        }

        // Remove all DB records
        int removed = repository.removeAllGuildShopRegions(guildId);
        plugin.getLogger().info("Guild '" + guildName + "' disbanded — removed " + removed + " shop region(s)");

        // Clear WorldGuard flags for each region
        for (ShopRegionInfo region : regions) {
            try {
                flagService.updateShopRegionFlags(region.getRegionId(), region.getWorldName(), null);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to clear WG flags for region " + region.getRegionId() +
                    " during guild disband cleanup: " + e.getMessage());
            }
        }

        // Log cleanup transaction
        repository.logShopTransaction(
            guildId,
            "ALL",
            "DISBAND_CLEANUP",
            0.0,
            "Guild disbanded — all " + removed + " shop region(s) released",
            event.getActorId()
        );
    }
}
