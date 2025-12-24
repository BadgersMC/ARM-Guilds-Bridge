package net.lumalyte.armbridge.services;

import net.lumalyte.armbridge.storage.ShopRegionInfo;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing guild shop regions
 */
public interface GuildShopService {

    /**
     * Register a shop region as owned by a guild
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @param guildId Guild UUID
     * @param purchasePrice Price paid for the region
     * @return true if successfully registered
     */
    boolean registerGuildShopRegion(String regionId, String worldName, UUID guildId, double purchasePrice);

    /**
     * Get the guild ID for a shop region
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @return Guild UUID or null if not a guild shop
     */
    UUID getGuildForShopRegion(String regionId, String worldName);

    /**
     * Get all shop regions owned by a guild
     *
     * @param guildId Guild UUID
     * @return List of ShopRegionInfo
     */
    List<ShopRegionInfo> getGuildShopRegions(UUID guildId);

    /**
     * Check if a guild has reached maximum shop limit
     *
     * @param guildId Guild UUID
     * @return true if at or over limit
     */
    boolean hasReachedShopLimit(UUID guildId);

    /**
     * Remove a shop region from guild ownership
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @return true if successfully removed
     */
    boolean removeGuildShopRegion(String regionId, String worldName);

    /**
     * Update enemy access mode for a shop region
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @param mode Enemy access mode
     * @param upchargePercentage Upcharge percentage (only used for UPCHARGE mode)
     * @return true if successfully updated
     */
    boolean updateEnemyAccessMode(String regionId, String worldName,
                                   net.lumalyte.armbridge.models.EnemyAccessMode mode,
                                   double upchargePercentage);
}
