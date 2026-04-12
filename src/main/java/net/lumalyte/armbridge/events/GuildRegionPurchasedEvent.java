package net.lumalyte.armbridge.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Called after a guild successfully purchases an ARM shop region.
 */
public class GuildRegionPurchasedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final String regionId;
    private final String worldName;
    private final UUID guildId;
    private final UUID buyerId;
    private final double purchasePrice;

    public GuildRegionPurchasedEvent(String regionId, String worldName, UUID guildId, UUID buyerId, double purchasePrice) {
        this.regionId = regionId;
        this.worldName = worldName;
        this.guildId = guildId;
        this.buyerId = buyerId;
        this.purchasePrice = purchasePrice;
    }

    public String getRegionId() { return regionId; }
    public String getWorldName() { return worldName; }
    public UUID getGuildId() { return guildId; }
    public UUID getBuyerId() { return buyerId; }
    public double getPurchasePrice() { return purchasePrice; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
