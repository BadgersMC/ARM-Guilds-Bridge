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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Enforces EDIT_SHOP_STOCK permission for modifying guild-owned shop chest contents
 */
public class InventoryModificationListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildShopService shopService;
    private final MemberService memberService;
    private final RankService rankService;

    public InventoryModificationListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.shopService = plugin.getGuildShopService();
        this.memberService = plugin.getMemberService();
        this.rankService = plugin.getRankService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if the clicked inventory is a chest
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || clickedInventory.getType() != InventoryType.CHEST) {
            return;
        }

        // Get chest location
        InventoryHolder holder = clickedInventory.getHolder();
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
            // Player not in shop owner guild - deny modification
            player.sendMessage("§cYou cannot modify inventory in another guild's shop!");
            event.setCancelled(true);
            return;
        }

        // Check if player has EDIT_SHOP_STOCK permission
        if (!hasShopStockPermission(player.getUniqueId(), shopGuildId)) {
            player.sendMessage("§cYou don't have permission to modify guild shop inventory!");
            player.sendMessage("§7Required permission: §eEDIT_SHOP_STOCK");
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
     * Check if player has EDIT_SHOP_STOCK permission in guild
     */
    private boolean hasShopStockPermission(UUID playerId, UUID guildId) {
        Rank rank = rankService.getPlayerRank(playerId, guildId);
        if (rank == null) {
            return false;
        }

        return rank.getPermissions().contains(RankPermission.EDIT_SHOP_STOCK);
    }
}
