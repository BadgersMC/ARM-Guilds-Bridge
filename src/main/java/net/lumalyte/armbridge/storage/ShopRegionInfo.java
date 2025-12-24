package net.lumalyte.armbridge.storage;

import net.lumalyte.armbridge.models.EnemyAccessMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Information about a guild-owned shop region
 */
public class ShopRegionInfo {
    private final String regionId;
    private final String worldName;
    private final UUID guildId;
    private final double purchasePrice;
    private final Instant purchasedAt;
    private final EnemyAccessMode enemyAccessMode;
    private final double upchargePercentage;

    public ShopRegionInfo(String regionId, String worldName, UUID guildId,
                          double purchasePrice, Instant purchasedAt,
                          EnemyAccessMode enemyAccessMode, double upchargePercentage) {
        this.regionId = regionId;
        this.worldName = worldName;
        this.guildId = guildId;
        this.purchasePrice = purchasePrice;
        this.purchasedAt = purchasedAt;
        this.enemyAccessMode = enemyAccessMode;
        this.upchargePercentage = upchargePercentage;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getWorldName() {
        return worldName;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public double getPurchasePrice() {
        return purchasePrice;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public EnemyAccessMode getEnemyAccessMode() {
        return enemyAccessMode;
    }

    public double getUpchargePercentage() {
        return upchargePercentage;
    }
}

