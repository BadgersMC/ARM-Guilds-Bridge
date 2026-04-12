package net.lumalyte.armbridge.listeners;

import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.events.GuildRegionPurchasedEvent;
import net.lumalyte.armbridge.services.GuildShopService;
import net.lumalyte.armbridge.services.PaymentRoutingService;
import net.lumalyte.armbridge.services.PurchaseMode;
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.lg.application.services.GuildService;
import net.lumalyte.lg.application.services.MemberService;
import net.lumalyte.lg.application.services.RankService;
import net.lumalyte.lg.domain.entities.Guild;
import net.lumalyte.lg.domain.entities.Rank;
import net.lumalyte.lg.domain.entities.RankPermission;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Listens to ARM's PreBuyEvent to intercept shop region purchases.
 * Uses reflection and dynamic event registration to avoid classloader issues.
 * Prompts player to choose personal vs guild purchase.
 */
public class RegionPurchaseListener implements Listener, EventExecutor {

    private final ARMGuildsBridge plugin;
    private final GuildService guildService;
    private final MemberService memberService;
    private final RankService rankService;
    private final GuildShopService shopService;
    private final PaymentRoutingService paymentService;
    private final RelationFlagService flagService;
    private final PurchaseMode purchaseMode;

    private final String requiredPermission;
    private final boolean notifyGuild;

    // Cached reflection objects (initialized once at startup)
    private Class<?> class_PreBuyEvent;
    private Class<?> class_Region;
    private Class<?> class_WGRegion;
    private Method method_getBuyer;
    private Method method_getRegionFromEvent;
    private Method method_setCancelled;
    private Method method_setNoMoneyTransfer;
    private Method method_getRegionFromRegion;
    private Method method_getId;
    private Method method_getRegionworld;
    private Method method_getPricePerPeriod;
    private Method method_setLandlord;

    public RegionPurchaseListener(ARMGuildsBridge plugin, PurchaseMode purchaseMode) {
        this.plugin = plugin;
        this.guildService = plugin.getGuildService();
        this.memberService = plugin.getMemberService();
        this.rankService = plugin.getRankService();
        this.shopService = plugin.getGuildShopService();
        this.paymentService = plugin.getPaymentRoutingService();
        this.flagService = plugin.getRelationFlagService();
        this.purchaseMode = purchaseMode;

        // Load config settings
        this.requiredPermission = plugin.getConfig().getString("shop-purchase.required-permission", "MANAGE_GUILD_SETTINGS");
        this.notifyGuild = plugin.getConfig().getBoolean("shop-purchase.notify-guild", true);

        // Cache all reflection objects at initialization
        initializeReflection();
    }

    /**
     * Initialize all reflection objects once at startup.
     * This avoids reflection overhead during event handling.
     */
    private void initializeReflection() {
        try {
            // Get ARM plugin's classloader
            org.bukkit.plugin.Plugin armPlugin = plugin.getServer().getPluginManager().getPlugin("AdvancedRegionMarket");
            if (armPlugin == null) {
                plugin.getLogger().severe("ARM plugin not found! Cannot initialize reflection.");
                return;
            }
            ClassLoader armClassLoader = armPlugin.getClass().getClassLoader();

            // Load ARM classes from ARM's classloader
            class_PreBuyEvent = Class.forName("net.alex9849.arm.events.PreBuyEvent", true, armClassLoader);
            class_Region = Class.forName("net.alex9849.arm.regions.Region", true, armClassLoader);
            class_WGRegion = Class.forName("net.alex9849.arm.adapters.WGRegion", true, armClassLoader);

            // Cache PreBuyEvent methods
            method_getBuyer = class_PreBuyEvent.getMethod("getBuyer");
            method_getRegionFromEvent = class_PreBuyEvent.getMethod("getRegion");
            method_setCancelled = class_PreBuyEvent.getMethod("setCancelled", boolean.class);
            method_setNoMoneyTransfer = class_PreBuyEvent.getMethod("setNoMoneyTransfer");

            // Cache Region methods
            method_getRegionFromRegion = class_Region.getMethod("getRegion");
            method_getRegionworld = class_Region.getMethod("getRegionworld");
            method_getPricePerPeriod = class_Region.getMethod("getPricePerPeriod");
            method_setLandlord = class_Region.getMethod("setLandlord", UUID.class);

            // Cache WGRegion methods
            method_getId = class_WGRegion.getMethod("getId");

            plugin.getLogger().info("RegionPurchaseListener: Successfully cached all ARM reflection objects from ARM classloader");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize ARM reflection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * EventExecutor interface method - called by Bukkit's event system
     */
    @Override
    public void execute(Listener listener, Event event) {
        // Validate event type using reflection
        if (!class_PreBuyEvent.isInstance(event)) {
            return;
        }

        try {
            handlePurchase(event);
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling ARM PreBuyEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle the ARM region purchase event
     */
    private void handlePurchase(Event event) {
        try {
            // Extract event data using cached methods
            Player buyer = (Player) method_getBuyer.invoke(event);
            Object armRegion = method_getRegionFromEvent.invoke(event);

            // Get WGRegion from Region to access region ID
            Object wgRegion = method_getRegionFromRegion.invoke(armRegion);
            String regionId = (String) method_getId.invoke(wgRegion);

            // Get region details from Region
            org.bukkit.World regionworld = (org.bukkit.World) method_getRegionworld.invoke(armRegion);
            String worldName = regionworld.getName();
            double price = (Double) method_getPricePerPeriod.invoke(armRegion);

            // Check if player is in a guild
            java.util.Set<UUID> playerGuilds = memberService.getPlayerGuilds(buyer.getUniqueId());

            if (playerGuilds.isEmpty()) {
                // Not in a guild - let ARM handle normally (personal purchase)
                return;
            }

            // Player is in a guild
            UUID guildId = playerGuilds.iterator().next();
            boolean hasGuildPerms = hasShopPurchasePermission(buyer.getUniqueId(), guildId);

            // Check if player has chosen a purchase mode
            Boolean chosenMode = purchaseMode.consumePurchaseMode(buyer.getUniqueId());

            if (chosenMode == null) {
                // No mode chosen yet
                if (!hasGuildPerms) {
                    // Can't buy for guild anyway - let ARM handle (personal)
                    return;
                }

                // Player CAN buy for guild but hasn't chosen yet - show prompt
                method_setCancelled.invoke(event, true);
                purchaseMode.showPrompt(buyer, regionId, price);
                return;
            }

            if (!chosenMode) {
                // Player explicitly chose PERSONAL mode - let ARM handle it normally
                return;
            }

            // Player wants guild purchase - validate and process
            if (!hasGuildPerms) {
                buyer.sendMessage("§cYou don't have permission to purchase regions for your guild!");
                buyer.sendMessage("§7Required permission: §e" + requiredPermission);
                method_setCancelled.invoke(event, true);
                return;
            }

            Guild guild = guildService.getGuild(guildId);
            if (guild == null) {
                buyer.sendMessage("§cError: Could not find your guild!");
                method_setCancelled.invoke(event, true);
                return;
            }

            // Check if guild has reached shop limit
            if (shopService.hasReachedShopLimit(guild.getId())) {
                int maxShops = plugin.getConfig().getInt("shop-purchase.max-shops-per-guild", 0);
                buyer.sendMessage("§cYour guild has reached the maximum shop limit (" + maxShops + ")!");
                method_setCancelled.invoke(event, true);
                return;
            }

            // Process guild purchase
            method_setNoMoneyTransfer.invoke(event);

            // Withdraw from guild vault
            String reason = "Shop region purchase: " + regionId;
            PaymentRoutingService.WithdrawalResult result = paymentService.withdrawFromGuild(
                guild.getId(),
                price,
                reason
            );

            if (!result.isSuccess()) {
                buyer.sendMessage("§cFailed to withdraw from guild vault: §f" + result.getError());
                buyer.sendMessage("§7Your guild needs §6" + price + " §7to purchase this shop region.");
                method_setCancelled.invoke(event, true);
                return;
            }

            // Set guild as landlord
            method_setLandlord.invoke(armRegion, guild.getId());

            // Register shop region in database
            boolean registered = shopService.registerGuildShopRegion(
                regionId,
                worldName,
                guild.getId(),
                price
            );

            if (!registered) {
                plugin.getLogger().warning("Failed to register shop region " + regionId + " for guild " + guild.getName());
            }

            // Update WorldGuard flags
            flagService.updateShopRegionFlags(regionId, worldName, guild.getId());

            // Fire event for external listeners (advancements, etc.)
            plugin.getServer().getPluginManager().callEvent(
                new GuildRegionPurchasedEvent(regionId, worldName, guild.getId(), buyer.getUniqueId(), price)
            );

            // Success messages
            buyer.sendMessage("§a✓ Successfully purchased shop region for your guild!");
            buyer.sendMessage("§7Region: §e" + regionId);
            buyer.sendMessage("§7Price: §6" + price);
            if (result.getInfo() != null) {
                buyer.sendMessage("§7Guild balance: §6" + result.getInfo().getRemainingBalance());
            }

            // Notify guild members
            if (notifyGuild) {
                notifyGuildMembers(guild, buyer, regionId, price);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error handling PreBuyEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if player has permission to purchase shop for guild
     */
    private boolean hasShopPurchasePermission(UUID playerUuid, UUID guildId) {
        try {
            RankPermission permission = RankPermission.valueOf(requiredPermission);
            Rank rank = rankService.getPlayerRank(playerUuid, guildId);

            if (rank == null) {
                return false;
            }

            return rank.getPermissions().contains(permission);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid permission in config: " + requiredPermission);
            // Default to MANAGE_GUILD_SETTINGS
            Rank rank = rankService.getPlayerRank(playerUuid, guildId);
            return rank != null && rank.getPermissions().contains(RankPermission.MANAGE_GUILD_SETTINGS);
        }
    }

    /**
     * Notify online guild members about the shop purchase
     */
    private void notifyGuildMembers(Guild guild, Player buyer, String regionId, double price) {
        String message = "§6[Guild Shop] §e" + buyer.getName() + " §7purchased shop region §e" +
                        regionId + " §7for §6" + price;

        // Get all online guild members
        memberService.getGuildMembers(guild.getId()).forEach(member -> {
            Player player = plugin.getServer().getPlayer(member.getPlayerId());
            if (player != null && player.isOnline() && !player.getUniqueId().equals(buyer.getUniqueId())) {
                player.sendMessage(message);
            }
        });
    }
}
