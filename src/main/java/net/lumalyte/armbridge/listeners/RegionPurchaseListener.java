package net.lumalyte.armbridge.listeners;

import net.alex9849.arm.events.PreBuyEvent;
import net.alex9849.arm.regions.Region;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.services.GuildShopService;
import net.lumalyte.armbridge.services.PaymentRoutingService;
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.lg.application.services.GuildService;
import net.lumalyte.lg.application.services.MemberService;
import net.lumalyte.lg.application.services.RankService;
import net.lumalyte.lg.domain.entities.Guild;
import net.lumalyte.lg.domain.entities.Rank;
import net.lumalyte.lg.domain.entities.RankPermission;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Listens to ARM's PreBuyEvent to intercept shop region purchases
 * Routes payment through guild vault and sets guild as landlord
 */
public class RegionPurchaseListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final GuildService guildService;
    private final MemberService memberService;
    private final RankService rankService;
    private final GuildShopService shopService;
    private final PaymentRoutingService paymentService;
    private final RelationFlagService flagService;

    private final String requiredPermission;
    private final boolean notifyGuild;

    public RegionPurchaseListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.guildService = plugin.getGuildService();
        this.memberService = plugin.getMemberService();
        this.rankService = plugin.getRankService();
        this.shopService = plugin.getGuildShopService();
        this.paymentService = plugin.getPaymentRoutingService();
        this.flagService = plugin.getRelationFlagService();

        // Load config settings
        this.requiredPermission = plugin.getConfig().getString("shop-purchase.required-permission", "MANAGE_GUILD_SETTINGS");
        this.notifyGuild = plugin.getConfig().getBoolean("shop-purchase.notify-guild", true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRegionPurchase(PreBuyEvent event) {
        Player buyer = event.getBuyer();
        Region region = event.getRegion();

        // Check if player is in a guild
        // Get all guilds the player is in (LumaGuilds supports multiple guilds)
        java.util.Set<UUID> playerGuilds = memberService.getPlayerGuilds(buyer.getUniqueId());
        if (playerGuilds.isEmpty()) {
            // Not in a guild - let ARM handle normally
            return;
        }

        // Use the first guild (TODO: Add guild selection UI if player is in multiple guilds)
        UUID guildId = playerGuilds.iterator().next();
        Guild guild = guildService.getGuild(guildId);
        if (guild == null) {
            buyer.sendMessage("§cError: Could not find your guild!");
            event.setCancelled(true);
            return;
        }

        // Check if player has permission to buy for guild
        if (!hasShopPurchasePermission(buyer.getUniqueId(), guild)) {
            buyer.sendMessage("§cYou don't have permission to purchase shop regions for your guild!");
            buyer.sendMessage("§7Required permission: §e" + requiredPermission);
            event.setCancelled(true);
            return;
        }

        // Check if guild has reached shop limit
        if (shopService.hasReachedShopLimit(guild.getId())) {
            int maxShops = plugin.getConfig().getInt("shop-purchase.max-shops-per-guild", 0);
            buyer.sendMessage("§cYour guild has reached the maximum shop limit (" + maxShops + ")!");
            event.setCancelled(true);
            return;
        }

        // Get purchase price
        double price = getPurchasePrice(region);

        // Tell ARM not to handle money
        event.setNoMoneyTransfer();

        // Withdraw from guild vault
        String reason = "Shop region purchase: " + region.getId();
        PaymentRoutingService.WithdrawalResult result = paymentService.withdrawFromGuild(
            guild.getId(),
            price,
            reason
        );

        if (!result.isSuccess()) {
            buyer.sendMessage("§cFailed to withdraw from guild vault: §f" + result.getError());
            buyer.sendMessage("§7Your guild needs §6" + price + " §7to purchase this shop region.");
            event.setCancelled(true);
            return;
        }

        // Set guild as landlord (all shop income will route to guild)
        region.setLandlord(guild.getId());

        // Register shop region in our database
        boolean registered = shopService.registerGuildShopRegion(
            region.getId(),
            region.getRegionworld().getName(),
            guild.getId(),
            price
        );

        if (!registered) {
            plugin.getLogger().warning("Failed to register shop region " + region.getId() + " for guild " + guild.getName());
        }

        // Update WorldGuard flags (stub for now)
        flagService.updateShopRegionFlags(region.getId(), region.getRegionworld().getName(), guild.getId());

        // Success messages
        buyer.sendMessage("§aSuccessfully purchased shop region for your guild!");
        buyer.sendMessage("§7Region: §e" + region.getId());
        buyer.sendMessage("§7Price: §6" + price);
        if (result.getInfo() != null) {
            buyer.sendMessage("§7Remaining balance: §6" + result.getInfo().getRemainingBalance());
        }

        // Notify guild members
        if (notifyGuild) {
            notifyGuildMembers(guild, buyer, region, price);
        }
    }

    /**
     * Check if player has permission to purchase shop for guild
     */
    private boolean hasShopPurchasePermission(UUID playerUuid, Guild guild) {
        try {
            RankPermission permission = RankPermission.valueOf(requiredPermission);
            Rank rank = rankService.getPlayerRank(playerUuid, guild.getId());

            if (rank == null) {
                return false;
            }

            return rank.getPermissions().contains(permission);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid permission in config: " + requiredPermission);
            // Default to MANAGE_GUILD_SETTINGS
            Rank rank = rankService.getPlayerRank(playerUuid, guild.getId());
            return rank != null && rank.getPermissions().contains(RankPermission.MANAGE_GUILD_SETTINGS);
        }
    }

    /**
     * Get the purchase price for the region
     */
    private double getPurchasePrice(Region region) {
        // ARM regions can be rent or buy
        // For our purposes, we use the per-period price
        return region.getPricePerPeriod();
    }

    /**
     * Notify online guild members about the shop purchase
     */
    private void notifyGuildMembers(Guild guild, Player buyer, Region region, double price) {
        String message = "§6[Guild Shop] §e" + buyer.getName() + " §7purchased shop region §e" +
                        region.getId() + " §7for §6" + price;

        // Get all online guild members
        memberService.getGuildMembers(guild.getId()).forEach(member -> {
            Player player = plugin.getServer().getPlayer(member.getPlayerId());
            if (player != null && player.isOnline() && !player.getUniqueId().equals(buyer.getUniqueId())) {
                player.sendMessage(message);
            }
        });
    }
}
