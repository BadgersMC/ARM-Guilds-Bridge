package net.lumalyte.armbridge.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether players want to buy regions personally or for their guild.
 * Simple toggle system - when in guild mode, next purchase uses guild vault.
 */
public class PurchaseMode {

    // Players who have guild mode enabled (TRUE = guild, FALSE = personal)
    private final Map<UUID, ModeChoice> playerChoices = new ConcurrentHashMap<>();

    private static final long MODE_TIMEOUT_MS = 60000; // 1 minute timeout

    /**
     * Result of consuming a player's purchase mode choice.
     */
    public static class PurchaseModeChoice {
        private final boolean guildMode;
        private final UUID guildId;

        public PurchaseModeChoice(boolean guildMode, UUID guildId) {
            this.guildMode = guildMode;
            this.guildId = guildId;
        }

        public boolean isGuildMode() {
            return guildMode;
        }

        /**
         * Selected guild for guild-mode purchases; null for personal mode.
         */
        public UUID getGuildId() {
            return guildId;
        }
    }

    private static class ModeChoice {
        final boolean isGuildMode;
        final UUID guildId;
        final long timestamp;

        ModeChoice(boolean isGuildMode, UUID guildId) {
            this.isGuildMode = isGuildMode;
            this.guildId = guildId;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Enable guild purchase mode for player with explicit guild selection.
     */
    public void enableGuildMode(UUID playerId, UUID guildId) {
        playerChoices.put(playerId, new ModeChoice(true, guildId));
    }

    /**
     * Enable personal purchase mode for player (explicitly chosen)
     */
    public void enablePersonalMode(UUID playerId) {
        playerChoices.put(playerId, new ModeChoice(false, null));
    }

    /**
     * Check if player has made a choice and it hasn't expired
     */
    private ModeChoice getActiveChoice(UUID playerId) {
        ModeChoice choice = playerChoices.get(playerId);
        if (choice == null) {
            return null;
        }

        // Check if expired
        if (System.currentTimeMillis() - choice.timestamp > MODE_TIMEOUT_MS) {
            playerChoices.remove(playerId);
            return null;
        }

        return choice;
    }

    /**
     * Check if player has chosen a mode (returns null if no choice or expired)
     */
    public PurchaseModeChoice consumePurchaseMode(UUID playerId) {
        ModeChoice choice = getActiveChoice(playerId);
        if (choice == null) {
            return null; // No choice made
        }

        // Remove choice after consumption
        playerChoices.remove(playerId);

        return new PurchaseModeChoice(choice.isGuildMode, choice.guildId);
    }

    /**
     * Show purchase mode prompt to player
     */
    public void showPrompt(Player player, String regionId, double price) {
        player.sendMessage("§6§l=== Purchase Region: §e" + regionId + " §6§l===");
        player.sendMessage("§7Price: §6" + price);
        player.sendMessage("");
        player.sendMessage("§eHow do you want to purchase this region?");
        player.sendMessage("");

        // Personal purchase button
        Component personalBtn = Component.text("[ Personal Purchase ]")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/guildshop purchasemode personal"));

        // Guild purchase button
        Component guildBtn = Component.text("[ Guild Purchase ]")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/guildshop purchasemode guild"));

        player.sendMessage(Component.text("  ")
            .append(personalBtn)
            .append(Component.text("   "))
            .append(guildBtn));

        player.sendMessage("");
        player.sendMessage("§7Or type:");
        player.sendMessage("§f/guildshop purchasemode personal §7- Use your money");
        player.sendMessage("§f/guildshop purchasemode guild [guild] §7- Use guild vault");
        player.sendMessage("");
        player.sendMessage("§7Then §eclick the shop sign again §7to complete the purchase.");
        player.sendMessage("§8(Mode expires in 60 seconds)");
    }
}