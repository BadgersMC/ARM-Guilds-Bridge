package net.lumalyte.armbridge.services;

import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.armbridge.storage.ShopRegionInfo;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of GuildShopService
 * Manages guild shop regions using the database repository
 */
public class GuildShopServiceImpl implements GuildShopService {

    private final ARMGuildsBridge plugin;
    private final GuildRegionRepository repository;
    private final int maxShopsPerGuild;

    public GuildShopServiceImpl(ARMGuildsBridge plugin, GuildRegionRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.maxShopsPerGuild = plugin.getConfig().getInt("shop-purchase.max-shops-per-guild", 0);
    }

    @Override
    public boolean registerGuildShopRegion(String regionId, String worldName, UUID guildId, double purchasePrice) {
        // Check if guild has reached max shop limit
        if (hasReachedShopLimit(guildId)) {
            plugin.getLogger().warning("Guild " + guildId + " has reached maximum shop limit (" + maxShopsPerGuild + ")");
            return false;
        }

        boolean registered = repository.registerGuildShopRegion(regionId, worldName, guildId, purchasePrice);

        if (registered) {
            plugin.getLogger().info("Registered shop region " + regionId + " for guild " + guildId);

            // Log transaction
            repository.logShopTransaction(
                guildId,
                regionId,
                "PURCHASE",
                purchasePrice,
                "Shop region purchased",
                null
            );
        }

        return registered;
    }

    @Override
    public UUID getGuildForShopRegion(String regionId, String worldName) {
        return repository.getGuildForShopRegion(regionId, worldName);
    }

    @Override
    public List<ShopRegionInfo> getGuildShopRegions(UUID guildId) {
        return repository.getGuildShopRegions(guildId);
    }

    @Override
    public boolean hasReachedShopLimit(UUID guildId) {
        // 0 = unlimited
        if (maxShopsPerGuild <= 0) {
            return false;
        }

        int currentShopCount = repository.getGuildShopRegions(guildId).size();
        return currentShopCount >= maxShopsPerGuild;
    }

    @Override
    public boolean removeGuildShopRegion(String regionId, String worldName) {
        UUID guildId = repository.getGuildForShopRegion(regionId, worldName);

        boolean removed = repository.removeGuildShopRegion(regionId, worldName);

        if (removed && guildId != null) {
            plugin.getLogger().info("Removed shop region " + regionId + " from guild " + guildId);

            // Log transaction
            repository.logShopTransaction(
                guildId,
                regionId,
                "REMOVAL",
                0.0,
                "Shop region removed",
                null
            );
        }

        return removed;
    }

    @Override
    public boolean updateEnemyAccessMode(String regionId, String worldName,
                                        net.lumalyte.armbridge.models.EnemyAccessMode mode,
                                        double upchargePercentage) {
        boolean updated = repository.updateEnemyAccessMode(regionId, worldName, mode, upchargePercentage);

        if (updated) {
            plugin.getLogger().info("Updated enemy access mode for shop region " + regionId +
                " to " + mode.name() + " (upcharge: " + upchargePercentage + "%)");
        }

        return updated;
    }
}
