package net.lumalyte.armbridge.listeners;

import net.alex9849.arm.events.PreShopTransactionEvent;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.models.EnemyAccessMode;
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.armbridge.storage.ShopRegionInfo;
import net.lumalyte.lg.application.services.MemberService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;
import java.util.UUID;

/**
 * Handles shop item transactions to apply upcharges for enemy guild members
 * Requires ARM fork with PreShopTransactionEvent
 */
public class ShopTransactionListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildRegionRepository repository;
    private final MemberService memberService;
    private final RelationFlagService relationFlagService;

    public ShopTransactionListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.repository = plugin.getGuildRegionRepository();
        this.memberService = plugin.getMemberService();
        this.relationFlagService = plugin.getRelationFlagService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopTransaction(PreShopTransactionEvent event) {
        Player buyer = event.getBuyer();

        // Get shop info
        ShopRegionInfo shopInfo = repository.getShopRegionInfo(event.getRegionId(), event.getWorldName());
        if (shopInfo == null) {
            return; // Not a guild shop
        }

        // Get buyer's guild
        Set<UUID> playerGuilds = memberService.getPlayerGuilds(buyer.getUniqueId());
        if (playerGuilds.isEmpty()) {
            return; // No guild - normal pricing
        }

        UUID buyerGuildId = playerGuilds.iterator().next();

        // Allow if buyer is in the shop owner guild
        if (shopInfo.getGuildId().equals(buyerGuildId)) {
            return; // Guild member - normal pricing
        }

        // Check if buyer's guild is enemy
        if (!relationFlagService.isGuildBlockedFromRegion(
                event.getRegionId(),
                event.getWorldName(),
                buyerGuildId)) {
            return; // Not an enemy - normal pricing
        }

        // Check access mode
        EnemyAccessMode accessMode = shopInfo.getEnemyAccessMode();

        if (accessMode == EnemyAccessMode.BAN) {
            // This shouldn't happen since they can't enter, but block just in case
            event.setCancelled(true);
            buyer.sendMessage("§cYou cannot purchase from this shop - enemies are banned!");
            return;
        }

        if (accessMode == EnemyAccessMode.WINDOW_SHOP) {
            // Block purchase
            event.setCancelled(true);
            buyer.sendMessage("");
            buyer.sendMessage("§c§l⚠ PURCHASE BLOCKED ⚠");
            buyer.sendMessage("§7This is an enemy guild's shop.");
            buyer.sendMessage("§cYou can only window shop - purchases are not allowed!");
            buyer.sendMessage("");
            return;
        }

        if (accessMode == EnemyAccessMode.UPCHARGE) {
            // Apply upcharge
            double upchargePercentage = shopInfo.getUpchargePercentage();
            double originalPrice = event.getOriginalPrice();
            double upchargeMultiplier = 1.0 + (upchargePercentage / 100.0);
            double newPrice = originalPrice * upchargeMultiplier;

            String reason = "§6Enemy Guild Upcharge: §c+" + String.format("%.0f", upchargePercentage) + "%";
            event.setModifiedPrice(newPrice, reason);

            plugin.getLogger().info("Applied " + String.format("%.0f", upchargePercentage) +
                "% upcharge to " + buyer.getName() + " (enemy guild) - " +
                originalPrice + " -> " + newPrice);
        }

        // ALLOW mode - no price modification
    }
}
