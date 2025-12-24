package net.alex9849.arm.events;

import net.alex9849.arm.regions.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Stub for ARM's PreBuyEvent
 * This is a compile-time stub - the actual ARM plugin must be present at runtime
 */
public class PreBuyEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private Region region;
    private Player buyer;
    private boolean isNoMoneyTransfer;

    public PreBuyEvent(Region region, Player buyer, boolean isPlayerInLimit) {
        this.region = region;
        this.buyer = buyer;
        this.isNoMoneyTransfer = false;
    }

    public Region getRegion() {
        return region;
    }

    public Player getBuyer() {
        return buyer;
    }

    public void setNoMoneyTransfer() {
        this.isNoMoneyTransfer = true;
    }

    public boolean isNoMoneyTransfer() {
        return isNoMoneyTransfer;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
