package net.lumalyte.armbridge.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.models.EnemyAccessMode;
import net.lumalyte.armbridge.services.GuildShopService;
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.armbridge.storage.ShopRegionInfo;
import net.lumalyte.lg.application.services.MemberService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Blocks players from enemy guilds from entering guild-owned shop regions (if BAN mode is enabled)
 */
public class ShopEntryListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildShopService shopService;
    private final RelationFlagService relationFlagService;
    private final MemberService memberService;
    private final GuildRegionRepository repository;

    public ShopEntryListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.shopService = plugin.getGuildShopService();
        this.relationFlagService = plugin.getRelationFlagService();
        this.memberService = plugin.getMemberService();
        this.repository = plugin.getGuildRegionRepository();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check when player crosses block boundaries
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() &&
                          from.getBlockY() == to.getBlockY() &&
                          from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();

        // Get player's guild
        Set<UUID> playerGuilds = memberService.getPlayerGuilds(player.getUniqueId());
        if (playerGuilds.isEmpty()) {
            return; // No guild - allow movement
        }

        UUID playerGuildId = playerGuilds.iterator().next();

        // Check if player is entering a guild shop region
        ShopRegionInfo shopInfo = getShopRegionInfoForLocation(to);
        if (shopInfo == null) {
            return; // Not in a guild shop
        }

        // Allow if player is in the shop owner guild
        if (shopInfo.getGuildId().equals(playerGuildId)) {
            return;
        }

        // Check if player's guild is blocked from this region
        if (isGuildBlockedFromLocation(to, playerGuildId)) {
            // Get the access mode for this shop
            EnemyAccessMode accessMode = shopInfo.getEnemyAccessMode();

            // Only block entry for BAN mode
            if (accessMode == EnemyAccessMode.BAN) {
                event.setCancelled(true);
                player.sendMessage("");
                player.sendMessage("§c§l⚠ ENEMY SHOP - ACCESS DENIED ⚠");
                player.sendMessage("§7This shop belongs to an enemy guild.");
                player.sendMessage("§7You are not allowed to enter.");
                player.sendMessage("");
                player.teleport(from);
            } else if (accessMode == EnemyAccessMode.UPCHARGE) {
                player.sendMessage("");
                player.sendMessage("§6§l⚠ ENEMY SHOP - UPCHARGE NOTICE ⚠");
                player.sendMessage("§7This shop belongs to an enemy guild.");
                player.sendMessage("§e§lWARNING: §eAll purchases cost §c+" + String.format("%.0f", shopInfo.getUpchargePercentage()) + "% §emore!");
                player.sendMessage("§7You will pay §c" + String.format("%.0f", 100 + shopInfo.getUpchargePercentage()) + "% §7of the listed price.");
                player.sendMessage("");
            } else if (accessMode == EnemyAccessMode.WINDOW_SHOP) {
                player.sendMessage("");
                player.sendMessage("§e§l⚠ ENEMY SHOP - WINDOW SHOPPING ONLY ⚠");
                player.sendMessage("§7This shop belongs to an enemy guild.");
                player.sendMessage("§c§lYou can view items but CANNOT purchase!");
                player.sendMessage("§7All purchase attempts will be blocked.");
                player.sendMessage("");
            }
            // ALLOW mode - no message, full access
        }
    }

    /**
     * Get the shop region info for location (if any)
     */
    private ShopRegionInfo getShopRegionInfoForLocation(Location location) {
        try {
            // Get WorldGuard RegionContainer
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regions == null) {
                return null;
            }

            // Get all regions at this location
            BlockVector3 position = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regionSet = regions.getApplicableRegions(position);

            // Check each region to see if it's registered as a guild shop
            for (ProtectedRegion region : regionSet) {
                ShopRegionInfo shopInfo = repository.getShopRegionInfo(region.getId(), location.getWorld().getName());
                if (shopInfo != null) {
                    return shopInfo;
                }
            }

            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error looking up WorldGuard region: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if a guild is blocked from entering a location
     */
    private boolean isGuildBlockedFromLocation(Location location, UUID guildId) {
        try {
            // Get WorldGuard RegionContainer
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regions == null) {
                return false;
            }

            // Get all regions at this location
            BlockVector3 position = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regionSet = regions.getApplicableRegions(position);

            // Check each region to see if guild is blocked
            for (ProtectedRegion region : regionSet) {
                if (relationFlagService.isGuildBlockedFromRegion(
                        region.getId(),
                        location.getWorld().getName(),
                        guildId)) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking if guild is blocked: " + e.getMessage());
            return false;
        }
    }
}
