package net.lumalyte.armbridge.models;

/**
 * Defines how enemy guilds can interact with a shop region
 */
public enum EnemyAccessMode {
    /**
     * Enemy guilds are completely blocked from entering the region
     */
    BAN,

    /**
     * Enemy guilds can purchase from the shop but pay an upcharge percentage
     * (e.g., 50% upcharge means they pay 150% of normal price)
     */
    UPCHARGE,

    /**
     * Enemy guilds can enter and view the shop but cannot purchase
     * (window shopping only)
     */
    WINDOW_SHOP,

    /**
     * Enemy guilds have full access with no restrictions
     */
    ALLOW;

    /**
     * Get the default enemy access mode
     */
    public static EnemyAccessMode getDefault() {
        return BAN;
    }

    /**
     * Parse from string, returns default if invalid
     */
    public static EnemyAccessMode fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return getDefault();
        }
    }

    /**
     * Get user-friendly description
     */
    public String getDescription() {
        switch (this) {
            case BAN:
                return "Enemies are blocked from entering";
            case UPCHARGE:
                return "Enemies pay extra when purchasing";
            case WINDOW_SHOP:
                return "Enemies can view but not purchase";
            case ALLOW:
                return "Enemies have full access";
            default:
                return "Unknown";
        }
    }
}
