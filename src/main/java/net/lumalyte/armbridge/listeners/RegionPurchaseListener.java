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
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.Set;
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

    private boolean disabled;

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
                disabled = true;
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
            disabled = true;
        }

        if (class_PreBuyEvent == null) {
            disabled = true;
        }
    }

    /**
     * EventExecutor interface method - called by Bukkit's event system
     */
    @Override
    public void execute(Listener listener, Event event) {
        if (disabled || class_PreBuyEvent == null) {
            return;
        }

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
            Set<UUID> playerGuilds = memberService.getPlayerGuilds(buyer.getUniqueId());

            if (playerGuilds.isEmpty()) {
                // Not in a guild - let ARM handle normally (personal purchase)
                return;
            }

            // Multi-guild: require explicit guild selection for guild purchases
            if (playerGuilds.size() > 1) {
                PurchaseMode.PurchaseModeChoice pendingChoice = purchaseMode.consumePurchaseMode(buyer.getUniqueId());
                if (pendingChoice == null) {
                    method_setCancelled.invoke(event, true);
                    buyer.sendMessage("§cYou are in multiple guilds. Specify which guild to purchase for:");
                    buyer.sendMessage("§f/guildshop purchasemode guild <guildname> §7- Guild vault purchase");
                    buyer.sendMessage("§f/guildshop purchasemode personal §7- Personal purchase");
                    buyer.sendMessage("§7Then run your §e/arm buy §7command again.");
                    return;
                }
                if (!pendingChoice.isGuildMode()) {
                    return;
                }
                if (pendingChoice.getGuildId() == null) {
                    method_setCancelled.invoke(event, true);
                    buyer.sendMessage("§cYou are in multiple guilds. Specify which guild to purchase for:");
                    buyer.sendMessage("§f/guildshop purchasemode guild <guildname>");
                    return;
                }
                if (!playerGuilds.contains(pendingChoice.getGuildId())) {
                    method_setCancelled.invoke(event, true);
                    buyer.sendMessage("§cThe selected guild is not one of your guilds!");
                    return;
                }
                processGuildPurchase(event, buyer, armRegion, regionId, worldName, price, pendingChoice.getGuildId());
                return;
            }

            // Single guild
            UUID guildId = playerGuilds.iterator().next();
            boolean hasGuildPerms = hasShopPurchasePermission(buyer.getUniqueId(), guildId);

            // Check if player has chosen a purchase mode
            PurchaseMode.PurchaseModeChoice chosenMode = purchaseMode.consumePurchaseMode(buyer.getUniqueId());

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

            if (!chosenMode.isGuildMode()) {
                // Player explicitly chose PERSONAL mode - let ARM handle it normally
                return;
            }

            UUID selectedGuildId = chosenMode.getGuildId() != null ? chosenMode.getGuildId() : guildId;
            processGuildPurchase(event, buyer, armRegion, regionId, worldName, price, selectedGuildId);

        } catch (Exception e) {
            plugin.getLogger().severe("Error handling PreBuyEvent: " + e.getMessage());
            e.printStackTrace();
            try {
                method_setCancelled.invoke(event, true);
            } catch (Exception cancelEx) {
                plugin.getLogger().severe("Failed to cancel PreBuyEvent after error: " + cancelEx.getMessage());
            }
        }
    }

    private void processGuildPurchase(Event event, Player buyer, Object armRegion, String regionId,
                                      String worldName, double price, UUID guildId) throws Exception {
        double withdrawnAmount = 0;
        UUID guildIdForRefund = null;
        boolean landlordSet = false;
        boolean registered = false;
        UUID buyerUuid = buyer.getUniqueId();

        try {
            if (!hasShopPurchasePermission(buyer.getUniqueId(), guildId)) {
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

            withdrawnAmount = price;
            guildIdForRefund = guild.getId();

            // Register shop region in database before setting landlord
            registered = shopService.registerGuildShopRegion(
                regionId,
                worldName,
                guild.getId(),
                price
            );

            if (!registered) {
                plugin.getLogger().warning("Failed to register shop region " + regionId + " for guild " + guild.getName() + " — rolling back payment");
                rollbackGuildPurchase(withdrawnAmount, guildIdForRefund, false, buyerUuid, armRegion,
                    false, regionId, worldName);
                buyer.sendMessage("§cFailed to register shop region. Your guild vault has been refunded.");
                method_setCancelled.invoke(event, true);
                return;
            }

            // Set guild as landlord after successful registration
            method_setLandlord.invoke(armRegion, guild.getId());
            landlordSet = true;

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
            plugin.getLogger().severe("Error during guild purchase for region " + regionId + ": " + e.getMessage());
            e.printStackTrace();
            rollbackGuildPurchase(withdrawnAmount, guildIdForRefund, landlordSet, buyerUuid, armRegion,
                registered, regionId, worldName);
            method_setCancelled.invoke(event, true);
            buyer.sendMessage("§cAn error occurred during guild shop purchase. Any charges have been refunded.");
        }
    }

    private void rollbackGuildPurchase(double withdrawnAmount, UUID guildIdForRefund, boolean landlordSet,
                                       UUID buyerUuid, Object armRegion, boolean registered,
                                       String regionId, String worldName) {
        if (withdrawnAmount > 0 && guildIdForRefund != null) {
            boolean refunded = paymentService.depositToGuild(
                guildIdForRefund,
                withdrawnAmount,
                "Refund: shop region purchase rollback for " + regionId
            );
            if (!refunded) {
                plugin.getLogger().severe("CRITICAL: Failed to refund " + withdrawnAmount + " to guild " +
                    guildIdForRefund + " after purchase rollback! Manual intervention required.");
            }
        }

        if (landlordSet && buyerUuid != null && armRegion != null) {
            try {
                method_setLandlord.invoke(armRegion, buyerUuid);
            } catch (Exception revertEx) {
                plugin.getLogger().severe("Failed to revert landlord to buyer after registration failure!");
            }
        }

        if (registered && regionId != null && worldName != null) {
            shopService.removeGuildShopRegion(regionId, worldName);
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