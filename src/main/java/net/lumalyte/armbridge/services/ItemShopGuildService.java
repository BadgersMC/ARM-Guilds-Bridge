package net.lumalyte.armbridge.services;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

/**
 * Service for tracking individual ItemShops that belong to guilds.
 * This is separate from GuildShopService which tracks ARM regions.
 *
 * ItemShops are individual chest+sign combos that can be converted to guild ownership.
 */
public interface ItemShopGuildService {

    /**
     * Register an ItemShop as guild-owned
     *
     * @param shopLocation Location of the chest (unique identifier)
     * @param guildId Guild UUID
     * @param playerUuid Player who converted the shop
     * @return true if successfully registered
     */
    boolean registerGuildItemShop(Location shopLocation, UUID guildId, UUID playerUuid);

    /**
     * Get the guild ID for an ItemShop at a specific location
     *
     * @param shopLocation Location of the chest
     * @return Guild UUID or null if not a guild shop
     */
    UUID getGuildForItemShop(Location shopLocation);

    /**
     * Check if an ItemShop is guild-owned
     *
     * @param shopLocation Location of the chest
     * @return true if this is a guild-owned shop
     */
    boolean isGuildItemShop(Location shopLocation);

    /**
     * Get all ItemShops owned by a guild
     *
     * @param guildId Guild UUID
     * @return List of shop locations
     */
    List<Location> getGuildItemShops(UUID guildId);

    /**
     * Remove an ItemShop from guild ownership (convert back to player-owned)
     *
     * @param shopLocation Location of the chest
     * @return true if successfully removed
     */
    boolean removeGuildItemShop(Location shopLocation);

    /**
     * Get the owner who created the ItemShop (for reference)
     *
     * @param shopLocation Location of the chest
     * @return Player UUID or null
     */
    UUID getItemShopCreator(Location shopLocation);
}
