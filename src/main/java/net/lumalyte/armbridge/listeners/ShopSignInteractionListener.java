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
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.armbridge.storage.ShopRegionInfo;
import net.lumalyte.lg.application.services.MemberService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Handles WINDOW_SHOP mode for enemy guild members clicking shop signs
 */
public class ShopSignInteractionListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildRegionRepository repository;
    private final MemberService memberService;
    private final RelationFlagService relationFlagService;

    public ShopSignInteractionListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.repository = plugin.getGuildRegionRepository();
        this.memberService = plugin.getMemberService();
        this.relationFlagService = plugin.getRelationFlagService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignClick(PlayerInteractEvent event) {
        // Only check right-clicks on signs
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) {
            return;
        }

        Player player = event.getPlayer();
        Location location = block.getLocation();

        // Get shop region info
        ShopRegionInfo shopInfo = getShopRegionInfoForLocation(location);
        if (shopInfo == null) {
            return; // Not in a guild shop
        }

        // Get player's guild
        Set<UUID> playerGuilds = memberService.getPlayerGuilds(player.getUniqueId());
        if (playerGuilds.isEmpty()) {
            return; // No guild
        }

        UUID playerGuildId = playerGuilds.iterator().next();

        // Allow if player is in the shop owner guild
        if (shopInfo.getGuildId().equals(playerGuildId)) {
            return;
        }

        // Check if player's guild is enemy
        if (!isGuildBlockedFromRegion(location, playerGuildId)) {
            return; // Not an enemy
        }

        // Check access mode and enforce it
        EnemyAccessMode accessMode = shopInfo.getEnemyAccessMode();

        if (accessMode == EnemyAccessMode.BAN) {
            // Block completely - they might be reaching through region boundary
            event.setCancelled(true);
            player.sendMessage("");
            player.sendMessage("§c§l⚠ ACCESS DENIED ⚠");
            player.sendMessage("§7This shop belongs to an enemy guild.");
            player.sendMessage("§cYou are banned from this shop!");
            player.sendMessage("");
        } else if (accessMode == EnemyAccessMode.WINDOW_SHOP) {
            // Block interaction - viewing only
            event.setCancelled(true);
            player.sendMessage("");
            player.sendMessage("§c§l⚠ PURCHASE BLOCKED ⚠");
            player.sendMessage("§7This is an enemy guild's shop.");
            player.sendMessage("§cYou can only window shop - purchases are not allowed!");
            player.sendMessage("");
        } else if (accessMode == EnemyAccessMode.UPCHARGE) {
            // Show reminder about upcharge before purchase
            // The actual upcharge is applied automatically in ShopTransactionListener
            player.sendMessage("§6⚠ §eEnemy shop - §c+" + String.format("%.0f", shopInfo.getUpchargePercentage()) + "% §eupcharge applies");
            // Don't cancel - let ShopTransactionListener apply the upcharge
        }
        // ALLOW mode has no restrictions
    }

    /**
     * Get the shop region info for location (if any)
     */
    private ShopRegionInfo getShopRegionInfoForLocation(Location location) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regions == null) {
                return null;
            }

            BlockVector3 position = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regionSet = regions.getApplicableRegions(position);

            for (ProtectedRegion region : regionSet) {
                ShopRegionInfo shopInfo = repository.getShopRegionInfo(region.getId(), location.getWorld().getName());
                if (shopInfo != null) {
                    return shopInfo;
                }
            }

            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error looking up shop region: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if a guild is blocked from a location
     */
    private boolean isGuildBlockedFromRegion(Location location, UUID guildId) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regions == null) {
                return false;
            }

            BlockVector3 position = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regionSet = regions.getApplicableRegions(position);

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
