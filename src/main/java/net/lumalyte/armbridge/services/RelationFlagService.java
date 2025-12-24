package net.lumalyte.armbridge.services;

import net.lumalyte.lg.domain.entities.RelationType;

import java.util.UUID;

/**
 * Service for managing WorldGuard flags based on guild relations
 */
public interface RelationFlagService {

    /**
     * Update WorldGuard flags for all shop regions when guild relation changes
     *
     * @param guild1 First guild UUID
     * @param guild2 Second guild UUID
     * @param newRelationType New relation type between guilds
     */
    void updateRegionFlagsForRelation(UUID guild1, UUID guild2, RelationType newRelationType);

    /**
     * Update flags for a specific shop region
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @param ownerGuildId Guild that owns the shop
     */
    void updateShopRegionFlags(String regionId, String worldName, UUID ownerGuildId);

    /**
     * Block a guild from accessing a shop region
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @param blockedGuildId Guild to block
     */
    void blockGuildFromRegion(String regionId, String worldName, UUID blockedGuildId);

    /**
     * Unblock a guild from accessing a shop region
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @param unblockedGuildId Guild to unblock
     */
    void unblockGuildFromRegion(String regionId, String worldName, UUID unblockedGuildId);

    /**
     * Check if a guild is blocked from a shop region
     *
     * @param regionId ARM region ID
     * @param worldName World name
     * @param guildId Guild to check
     * @return true if blocked
     */
    boolean isGuildBlockedFromRegion(String regionId, String worldName, UUID guildId);
}
