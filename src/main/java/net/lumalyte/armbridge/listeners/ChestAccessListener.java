package net.lumalyte.armbridge.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.services.GuildShopService;
import net.lumalyte.lg.application.services.MemberService;
import net.lumalyte.lg.application.services.RankService;
import net.lumalyte.lg.domain.entities.Rank;
import net.lumalyte.lg.domain.entities.RankPermission;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Enforces ACCESS_SHOP_CHESTS permission for guild-owned shop regions
 */
public class ChestAccessListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildShopService shopService;
    private final MemberService memberService;
    private final RankService rankService;

    public ChestAccessListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.shopService = plugin.getGuildShopService();
        this.memberService = plugin.getMemberService();
        this.rankService = plugin.getRankService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // Check if inventory is a chest
        if (event.getInventory().getType() != InventoryType.CHEST) {
            return;
        }

        // Get chest location
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) {
            return;
        }

        Location location = null;
        if (holder instanceof org.bukkit.block.Chest) {
            location = ((org.bukkit.block.Chest) holder).getLocation();
        } else if (holder instanceof org.bukkit.block.DoubleChest) {
            location = ((org.bukkit.block.DoubleChest) holder).getLocation();
        }

        if (location == null) {
            return;
        }

        // Check if chest is in a guild-owned shop region
        UUID shopGuildId = getShopGuildForLocation(location);
        if (shopGuildId == null) {
            // Not in a guild shop - allow normal access
            return;
        }

        // Check if player is in the shop owner guild
        java.util.Set<UUID> playerGuilds = memberService.getPlayerGuilds(player.getUniqueId());
        if (!playerGuilds.contains(shopGuildId)) {
            // Player not in shop owner guild - deny access
            player.sendMessage("§cThis chest belongs to another guild's shop!");
            event.setCancelled(true);
            return;
        }

        // Check if player has ACCESS_SHOP_CHESTS permission
        if (!hasShopChestPermission(player.getUniqueId(), shopGuildId)) {
            player.sendMessage("§cYou don't have permission to access guild shop chests!");
            player.sendMessage("§7Required permission: §eACCESS_SHOP_CHESTS");
            event.setCancelled(true);
        }
    }

    /**
     * Get the guild ID for shop at location (if any)
     */
    private UUID getShopGuildForLocation(Location location) {
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
                UUID guildId = shopService.getGuildForShopRegion(region.getId(), location.getWorld().getName());
                if (guildId != null) {
                    return guildId;
                }
            }

            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error looking up WorldGuard region: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if player has ACCESS_SHOP_CHESTS permission in guild
     */
    private boolean hasShopChestPermission(UUID playerId, UUID guildId) {
        Rank rank = rankService.getPlayerRank(playerId, guildId);
        if (rank == null) {
            return false;
        }

        return rank.getPermissions().contains(RankPermission.ACCESS_SHOP_CHESTS);
    }
}
