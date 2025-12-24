package net.lumalyte.armbridge.services;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.lg.domain.entities.Guild;
import net.lumalyte.lg.domain.entities.RelationType;

import java.util.*;

/**
 * Implementation of RelationFlagService
 * Manages WorldGuard flags based on guild relations
 */
public class RelationFlagServiceImpl implements RelationFlagService {

    private final ARMGuildsBridge plugin;
    private final boolean enemyBlockingEnabled;

    // Custom WorldGuard flag to store comma-separated list of blocked guild UUIDs
    public static StringFlag BLOCKED_GUILDS_FLAG;

    public RelationFlagServiceImpl(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.enemyBlockingEnabled = plugin.getConfig().getBoolean("enemy-blocking.enabled", true);

        // Register custom WorldGuard flag
        registerCustomFlags();
    }

    /**
     * Register custom WorldGuard flags
     */
    private void registerCustomFlags() {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            StringFlag blockedGuildsFlag = new StringFlag("blocked-guilds");
            registry.register(blockedGuildsFlag);
            BLOCKED_GUILDS_FLAG = blockedGuildsFlag;
            plugin.getLogger().info("Registered WorldGuard custom flag: blocked-guilds");
        } catch (FlagConflictException e) {
            // Flag already registered, get existing instance
            Flag<?> existing = WorldGuard.getInstance().getFlagRegistry().get("blocked-guilds");
            if (existing instanceof StringFlag) {
                BLOCKED_GUILDS_FLAG = (StringFlag) existing;
                plugin.getLogger().info("Using existing WorldGuard flag: blocked-guilds");
            } else {
                plugin.getLogger().severe("Flag 'blocked-guilds' exists but is wrong type!");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register WorldGuard flags: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void updateRegionFlagsForRelation(UUID guild1, UUID guild2, RelationType newRelationType) {
        if (!enemyBlockingEnabled) {
            return;
        }

        // Get all shop regions owned by guild1 and guild2
        List<net.lumalyte.armbridge.storage.ShopRegionInfo> guild1Regions =
            plugin.getGuildRegionRepository().getGuildShopRegions(guild1);
        List<net.lumalyte.armbridge.storage.ShopRegionInfo> guild2Regions =
            plugin.getGuildRegionRepository().getGuildShopRegions(guild2);

        // Update flags based on new relation type
        if (newRelationType == RelationType.ENEMY) {
            // Block each guild from the other's shops
            for (net.lumalyte.armbridge.storage.ShopRegionInfo region : guild1Regions) {
                blockGuildFromRegion(region.getRegionId(), region.getWorldName(), guild2);
            }
            for (net.lumalyte.armbridge.storage.ShopRegionInfo region : guild2Regions) {
                blockGuildFromRegion(region.getRegionId(), region.getWorldName(), guild1);
            }
            plugin.getLogger().info("Blocked enemy guilds from each other's shops: " + guild1 + " <-> " + guild2);
        } else {
            // Unblock for ALLY, TRUCE, or NEUTRAL
            for (net.lumalyte.armbridge.storage.ShopRegionInfo region : guild1Regions) {
                unblockGuildFromRegion(region.getRegionId(), region.getWorldName(), guild2);
            }
            for (net.lumalyte.armbridge.storage.ShopRegionInfo region : guild2Regions) {
                unblockGuildFromRegion(region.getRegionId(), region.getWorldName(), guild1);
            }
            plugin.getLogger().info("Unblocked guilds from each other's shops: " + guild1 + " <-> " + guild2 + " (relation: " + newRelationType + ")");
        }
    }

    @Override
    public void updateShopRegionFlags(String regionId, String worldName, UUID ownerGuildId) {
        if (!enemyBlockingEnabled) {
            return;
        }

        // Get owner guild
        Guild ownerGuild = plugin.getGuildService().getGuild(ownerGuildId);
        if (ownerGuild == null) {
            plugin.getLogger().warning("Cannot update shop region flags - owner guild not found: " + ownerGuildId);
            return;
        }

        // Get all enemy guilds
        Set<UUID> enemyGuilds = new HashSet<>();
        Set<net.lumalyte.lg.domain.entities.Relation> enemyRelations =
            plugin.getRelationService().getGuildRelationsByType(ownerGuildId, RelationType.ENEMY);

        for (net.lumalyte.lg.domain.entities.Relation relation : enemyRelations) {
            // Get the other guild in the relation
            if (relation.getGuildA().equals(ownerGuildId)) {
                enemyGuilds.add(relation.getGuildB());
            } else {
                enemyGuilds.add(relation.getGuildA());
            }
        }

        // Block all enemy guilds from this shop region
        for (UUID enemyGuildId : enemyGuilds) {
            blockGuildFromRegion(regionId, worldName, enemyGuildId);
        }

        plugin.getLogger().info("Updated shop region flags for " + regionId + " - blocked " + enemyGuilds.size() + " enemy guilds");
    }

    @Override
    public void blockGuildFromRegion(String regionId, String worldName, UUID blockedGuildId) {
        if (!enemyBlockingEnabled || BLOCKED_GUILDS_FLAG == null) {
            return;
        }

        try {
            // Get WorldGuard region
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(plugin.getServer().getWorld(worldName)));

            if (regions == null) {
                plugin.getLogger().warning("RegionManager not found for world: " + worldName);
                return;
            }

            ProtectedRegion region = regions.getRegion(regionId);
            if (region == null) {
                plugin.getLogger().warning("Region not found: " + regionId + " in world " + worldName);
                return;
            }

            // Get current blocked guilds list
            String currentValue = region.getFlag(BLOCKED_GUILDS_FLAG);
            Set<String> blockedGuilds = new HashSet<>();
            if (currentValue != null && !currentValue.isEmpty()) {
                blockedGuilds.addAll(Arrays.asList(currentValue.split(",")));
            }

            // Add new blocked guild
            blockedGuilds.add(blockedGuildId.toString());

            // Set updated flag
            region.setFlag(BLOCKED_GUILDS_FLAG, String.join(",", blockedGuilds));

            plugin.getLogger().fine("Blocked guild " + blockedGuildId + " from region " + regionId);
        } catch (Exception e) {
            plugin.getLogger().warning("Error blocking guild from region: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void unblockGuildFromRegion(String regionId, String worldName, UUID unblockedGuildId) {
        if (!enemyBlockingEnabled || BLOCKED_GUILDS_FLAG == null) {
            return;
        }

        try {
            // Get WorldGuard region
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(plugin.getServer().getWorld(worldName)));

            if (regions == null) {
                plugin.getLogger().warning("RegionManager not found for world: " + worldName);
                return;
            }

            ProtectedRegion region = regions.getRegion(regionId);
            if (region == null) {
                plugin.getLogger().warning("Region not found: " + regionId + " in world " + worldName);
                return;
            }

            // Get current blocked guilds list
            String currentValue = region.getFlag(BLOCKED_GUILDS_FLAG);
            if (currentValue == null || currentValue.isEmpty()) {
                return; // Nothing to unblock
            }

            Set<String> blockedGuilds = new HashSet<>(Arrays.asList(currentValue.split(",")));

            // Remove unblocked guild
            blockedGuilds.remove(unblockedGuildId.toString());

            // Set updated flag (empty string if no guilds blocked)
            if (blockedGuilds.isEmpty()) {
                region.setFlag(BLOCKED_GUILDS_FLAG, null);
            } else {
                region.setFlag(BLOCKED_GUILDS_FLAG, String.join(",", blockedGuilds));
            }

            plugin.getLogger().fine("Unblocked guild " + unblockedGuildId + " from region " + regionId);
        } catch (Exception e) {
            plugin.getLogger().warning("Error unblocking guild from region: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean isGuildBlockedFromRegion(String regionId, String worldName, UUID guildId) {
        if (!enemyBlockingEnabled || BLOCKED_GUILDS_FLAG == null) {
            return false;
        }

        try {
            // Get WorldGuard region
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(plugin.getServer().getWorld(worldName)));

            if (regions == null) {
                return false;
            }

            ProtectedRegion region = regions.getRegion(regionId);
            if (region == null) {
                return false;
            }

            // Get blocked guilds list
            String currentValue = region.getFlag(BLOCKED_GUILDS_FLAG);
            if (currentValue == null || currentValue.isEmpty()) {
                return false;
            }

            Set<String> blockedGuilds = new HashSet<>(Arrays.asList(currentValue.split(",")));
            return blockedGuilds.contains(guildId.toString());
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking if guild is blocked: " + e.getMessage());
            return false;
        }
    }
}
