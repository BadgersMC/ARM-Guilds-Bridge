package net.alex9849.arm.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Stub for ItemShops' PostShopTransactionEvent (to be added in ItemShops fork)
 * Fired after a successful item purchase from a shop
 * Allows external plugins to track sales and route income
 */
public class PostShopTransactionEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player buyer;
    private final String regionId;
    private final String worldName;
    private final ItemStack item;
    private final double pricePaid;
    private final UUID landlordId;

    public PostShopTransactionEvent(Player buyer, String regionId, String worldName,
                                    ItemStack item, double pricePaid, UUID landlordId) {
        this.buyer = buyer;
        this.regionId = regionId;
        this.worldName = worldName;
        this.item = item;
        this.pricePaid = pricePaid;
        this.landlordId = landlordId;
    }

    public Player getBuyer() {
        return buyer;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getWorldName() {
        return worldName;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getPricePaid() {
        return pricePaid;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
