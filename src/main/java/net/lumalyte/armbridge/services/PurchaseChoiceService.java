package net.lumalyte.armbridge.services;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track player's purchase choice (personal vs guild).
 * When a player chooses, we store their choice and let them retry /arm buy.
 */
public class PurchaseChoiceService {

    public enum PurchaseChoice {
        PERSONAL,  // Buy with personal money
        GUILD      // Buy with guild money
    }

    // Map of player UUID -> chosen purchase type
    private final Map<UUID, PurchaseChoice> playerChoices = new ConcurrentHashMap<>();

    /**
     * Set player's purchase choice
     */
    public void setChoice(UUID playerId, PurchaseChoice choice) {
        playerChoices.put(playerId, choice);
    }

    /**
     * Get player's purchase choice (and consume it)
     */
    public PurchaseChoice getAndConsumeChoice(UUID playerId) {
        return playerChoices.remove(playerId);
    }

    /**
     * Check if player has a pending choice
     */
    public boolean hasChoice(UUID playerId) {
        return playerChoices.containsKey(playerId);
    }

    /**
     * Get player's choice without consuming it
     */
    public PurchaseChoice peekChoice(UUID playerId) {
        return playerChoices.get(playerId);
    }

    /**
     * Clear player's choice
     */
    public void clearChoice(UUID playerId) {
        playerChoices.remove(playerId);
    }
}
