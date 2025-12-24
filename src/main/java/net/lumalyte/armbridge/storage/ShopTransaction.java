package net.lumalyte.armbridge.storage;

import java.time.Instant;
import java.util.UUID;

/**
 * Record of a guild shop transaction
 */
public class ShopTransaction {
    private final int id;
    private final UUID guildId;
    private final String regionId;
    private final String transactionType;
    private final double amount;
    private final String description;
    private final UUID actorId;
    private final Instant createdAt;

    public ShopTransaction(int id, UUID guildId, String regionId, String transactionType,
                           double amount, String description, UUID actorId, Instant createdAt) {
        this.id = id;
        this.guildId = guildId;
        this.regionId = regionId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.description = description;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public double getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
