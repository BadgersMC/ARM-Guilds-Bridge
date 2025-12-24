package net.lumalyte.armbridge.storage;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for guild shop region data
 */
public interface GuildRegionRepository {

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
     * Remove a shop region from guild ownership
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @return true if successfully removed
     */
    boolean removeGuildShopRegion(String regionId, String worldName);

    /**
     * Remove all shop regions for a guild (when guild is disbanded)
     *
     * @param guildId Guild UUID
     * @return Number of regions removed
     */
    int removeAllGuildShopRegions(UUID guildId);

    /**
     * Log a shop transaction
     *
     * @param guildId Guild UUID
     * @param regionId ARM region ID
     * @param transactionType Type of transaction (PURCHASE, EXTENSION, INCOME, EXPENSE)
     * @param amount Amount
     * @param description Transaction description
     * @param actorId Player UUID who initiated the transaction (optional)
     * @return true if successfully logged
     */
    boolean logShopTransaction(UUID guildId, String regionId, String transactionType,
                               double amount, String description, UUID actorId);

    /**
     * Get transaction history for a guild
     *
     * @param guildId Guild UUID
     * @param limit Maximum number of transactions to return
     * @return List of transaction records
     */
    List<ShopTransaction> getTransactionHistory(UUID guildId, int limit);

    /**
     * Initialize database schema (create tables if they don't exist)
     */
    void initializeSchema();

    /**
     * Get enemy access mode for a shop region
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @return ShopRegionInfo or null if not found
     */
    ShopRegionInfo getShopRegionInfo(String regionId, String worldName);

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
