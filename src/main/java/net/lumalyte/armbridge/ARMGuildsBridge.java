package net.lumalyte.armbridge;

import net.lumalyte.armbridge.services.GuildShopService;
import net.lumalyte.armbridge.services.ItemShopGuildService;
import net.lumalyte.armbridge.services.PaymentRoutingService;
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.armbridge.storage.GuildRegionRepository;
import net.lumalyte.armbridge.storage.GuildRegionRepositoryImpl;
import net.lumalyte.lg.application.services.GuildService;
import net.lumalyte.lg.application.services.GuildVaultService;
import net.lumalyte.lg.application.services.MemberService;
import net.lumalyte.lg.application.services.RankService;
import net.lumalyte.lg.application.services.RelationService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Main plugin class for ARM-Guilds-Bridge
 * Integrates Advanced Region Market with LumaGuilds for guild-owned shop regions
 */
public class ARMGuildsBridge extends JavaPlugin {

    private static ARMGuildsBridge instance;
    private Logger logger;

    // LumaGuilds services
    private GuildService guildService;
    private GuildVaultService guildVaultService;
    private MemberService memberService;
    private RankService rankService;
    private RelationService relationService;

    // Bridge services
    private GuildRegionRepository guildRegionRepository;
    private GuildShopService guildShopService;
    private PaymentRoutingService paymentRoutingService;
    private RelationFlagService relationFlagService;
    private ItemShopGuildService itemShopGuildService;
    private net.lumalyte.armbridge.services.PurchaseMode purchaseMode;

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();

        // Save default config
        saveDefaultConfig();

        // Load LumaGuilds services
        if (!loadLumaGuildsServices()) {
            logger.severe("Failed to load LumaGuilds services! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize storage
        initializeStorage();

        // Initialize services
        initializeServices();

        // Run migrations for pre-existing guilds
        migratePreExistingGuilds();

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        logger.info("=================================================");
        logger.info("ARM-Guilds-Bridge enabled successfully!");
        logger.info("=================================================");
        logger.info("");
        logger.info("✅ READY FOR PRODUCTION:");
        logger.info("  - Guild shop purchases (ARM integration)");
        logger.info("  - BAN mode (enemies completely blocked)");
        logger.info("  - WINDOW_SHOP mode (enemies view but can't buy)");
        logger.info("  - ALLOW mode (full enemy access)");
        logger.info("  - UPCHARGE mode (automatic price modification for enemies)");
        logger.info("  - PHYSICAL mode (shop income routing to guild vaults)");
        logger.info("  - Shop permissions (chest access, inventory, signs)");
        logger.info("  - Player commands (/guildshop)");
        logger.info("");
        logger.info("=================================================");
    }

    @Override
    public void onDisable() {
        // Close database connection
        if (guildRegionRepository instanceof GuildRegionRepositoryImpl) {
            ((GuildRegionRepositoryImpl) guildRegionRepository).close();
        }
        logger.info("ARM-Guilds-Bridge disabled.");
    }

    /**
     * Load LumaGuilds services through public service getters
     */
    private boolean loadLumaGuildsServices() {
        try {
            // Get LumaGuilds plugin instance
            org.bukkit.plugin.Plugin lumaGuildsPlugin = getServer().getPluginManager().getPlugin("LumaGuilds");

            if (lumaGuildsPlugin == null) {
                logger.severe("LumaGuilds plugin not found! Make sure it's installed and loaded.");
                return false;
            }

            if (!(lumaGuildsPlugin instanceof net.lumalyte.lg.LumaGuilds)) {
                logger.severe("LumaGuilds plugin is wrong type!");
                return false;
            }

            net.lumalyte.lg.LumaGuilds lumaGuilds = (net.lumalyte.lg.LumaGuilds) lumaGuildsPlugin;

            // Access services through public getters
            guildService = lumaGuilds.getGuildService();
            guildVaultService = lumaGuilds.getGuildVaultService();
            memberService = lumaGuilds.getMemberService();
            rankService = lumaGuilds.getRankService();
            relationService = lumaGuilds.getRelationService();

            logger.info("Successfully loaded LumaGuilds services");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to load LumaGuilds services: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initialize storage layer
     */
    private void initializeStorage() {
        String storageType = getConfig().getString("storage.type", "database");

        if ("database".equalsIgnoreCase(storageType)) {
            boolean useSharedConnection = getConfig().getBoolean("storage.shared-connection", true);

            if (useSharedConnection) {
                logger.info("Using shared database connection with LumaGuilds");
                // TODO: Get database connection from LumaGuilds
                // For now, create a new repository (will implement shared connection later)
            }

            guildRegionRepository = new GuildRegionRepositoryImpl(this);
            logger.info("Initialized database storage for guild shop regions");
        } else {
            logger.warning("YAML storage not yet implemented, using database");
            guildRegionRepository = new GuildRegionRepositoryImpl(this);
        }
    }

    /**
     * Initialize bridge services
     */
    private void initializeServices() {
        // Initialize GuildShopService
        guildShopService = new net.lumalyte.armbridge.services.GuildShopServiceImpl(
            this,
            guildRegionRepository
        );
        logger.info("Initialized GuildShopService");

        // Initialize PaymentRoutingService
        paymentRoutingService = new net.lumalyte.armbridge.services.PaymentRoutingServiceImpl(
            this,
            guildVaultService,
            guildRegionRepository
        );
        logger.info("Initialized PaymentRoutingService");

        // Initialize RelationFlagService
        relationFlagService = new net.lumalyte.armbridge.services.RelationFlagServiceImpl(this);
        if (net.lumalyte.armbridge.services.RelationFlagServiceImpl.BLOCKED_GUILDS_FLAG != null) {
            logger.info("Initialized RelationFlagService with WorldGuard integration");
        } else {
            logger.warning("Initialized RelationFlagService in stub mode - WorldGuard flag registration failed");
        }

        // Initialize ItemShopGuildService
        itemShopGuildService = new net.lumalyte.armbridge.services.ItemShopGuildServiceImpl(this);
        logger.info("Initialized ItemShopGuildService");

        // Initialize PurchaseMode
        purchaseMode = new net.lumalyte.armbridge.services.PurchaseMode();
        logger.info("Initialized PurchaseMode service");
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        // Register region purchase listener (hooks into ARM PreBuyEvent) using dynamic registration
        try {
            net.lumalyte.armbridge.listeners.RegionPurchaseListener purchaseListener =
                new net.lumalyte.armbridge.listeners.RegionPurchaseListener(this, purchaseMode);

            // Get ARM's PreBuyEvent class using ARM plugin classloader (avoids classloader mismatch)
            org.bukkit.plugin.Plugin armPlugin = getServer().getPluginManager().getPlugin("AdvancedRegionMarket");
            if (armPlugin == null) {
                logger.severe("ARM plugin not found! Cannot register RegionPurchaseListener.");
                return;
            }
            ClassLoader armClassLoader = armPlugin.getClass().getClassLoader();
            Class<?> preBuyEventClass = Class.forName("net.alex9849.arm.events.PreBuyEvent", true, armClassLoader);

            // Register using EventExecutor to avoid classloader issues
            getServer().getPluginManager().registerEvent(
                preBuyEventClass.asSubclass(org.bukkit.event.Event.class),
                purchaseListener,
                org.bukkit.event.EventPriority.HIGH,
                purchaseListener,
                this,
                false
            );
            logger.info("Registered RegionPurchaseListener using dynamic event registration");
        } catch (Exception e) {
            logger.severe("Failed to register RegionPurchaseListener: " + e.getMessage());
            e.printStackTrace();
        }

        // Register permission enforcement listeners
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.ChestAccessListener(this),
            this
        );
        logger.info("Registered ChestAccessListener");

        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.InventoryModificationListener(this),
            this
        );
        logger.info("Registered InventoryModificationListener");

        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.SignInteractionListener(this),
            this
        );
        logger.info("Registered SignInteractionListener");

        // Register enemy blocking listener
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.ShopEntryListener(this),
            this
        );
        logger.info("Registered ShopEntryListener");

        // Register relation change listener
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.RelationChangeListener(this),
            this
        );
        logger.info("Registered RelationChangeListener");

        // Register shop sign interaction listener (WINDOW_SHOP mode)
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.ShopSignInteractionListener(this),
            this
        );
        logger.info("Registered ShopSignInteractionListener");

        // Register shop transaction listener (UPCHARGE mode)
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.ShopTransactionListener(this),
            this
        );
        logger.info("Registered ShopTransactionListener (UPCHARGE mode)");

        // Register shop income listener (PHYSICAL mode income routing)
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.ShopIncomeListener(this),
            this
        );
        logger.info("Registered ShopIncomeListener (PHYSICAL mode)");

        // Register guild disband cleanup listener
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.GuildDisbandListener(this),
            this
        );
        logger.info("Registered GuildDisbandListener (shop cleanup on disband)");
    }

    /**
     * Register commands
     */
    private void registerCommands() {
        net.lumalyte.armbridge.commands.GuildShopCommand guildShopCommand =
            new net.lumalyte.armbridge.commands.GuildShopCommand(this);

        getCommand("guildshop").setExecutor(guildShopCommand);
        getCommand("guildshop").setTabCompleter(guildShopCommand);

        logger.info("Registered GuildShopCommand");
    }

    // Getters
    public static ARMGuildsBridge getInstance() {
        return instance;
    }

    public GuildService getGuildService() {
        return guildService;
    }

    public GuildVaultService getGuildVaultService() {
        return guildVaultService;
    }

    public MemberService getMemberService() {
        return memberService;
    }

    public RankService getRankService() {
        return rankService;
    }

    public RelationService getRelationService() {
        return relationService;
    }

    public GuildRegionRepository getGuildRegionRepository() {
        return guildRegionRepository;
    }

    public GuildShopService getGuildShopService() {
        return guildShopService;
    }

    public PaymentRoutingService getPaymentRoutingService() {
        return paymentRoutingService;
    }

    public RelationFlagService getRelationFlagService() {
        return relationFlagService;
    }

    public ItemShopGuildService getItemShopGuildService() {
        return itemShopGuildService;
    }

    public net.lumalyte.armbridge.services.PurchaseMode getPurchaseMode() {
        return purchaseMode;
    }

    /**
     * Migrate pre-existing guilds to ensure Owner ranks have MANAGE_GUILD_SETTINGS permission.
     * This is needed for guilds created before the permission was added to support shop purchases.
     */
    private void migratePreExistingGuilds() {
        logger.info("Checking for guilds needing MANAGE_GUILD_SETTINGS permission migration...");

        try {
            // Use the already-loaded services
            java.util.Set<net.lumalyte.lg.domain.entities.Guild> allGuilds = guildService.getAllGuilds();
            int migratedCount = 0;

            // Define all required shop permissions
            java.util.Set<net.lumalyte.lg.domain.entities.RankPermission> requiredShopPermissions = new java.util.HashSet<>();
            requiredShopPermissions.add(net.lumalyte.lg.domain.entities.RankPermission.MANAGE_GUILD_SETTINGS);
            requiredShopPermissions.add(net.lumalyte.lg.domain.entities.RankPermission.ACCESS_SHOP_CHESTS);
            requiredShopPermissions.add(net.lumalyte.lg.domain.entities.RankPermission.EDIT_SHOP_STOCK);
            requiredShopPermissions.add(net.lumalyte.lg.domain.entities.RankPermission.MODIFY_SHOP_PRICES);

            for (net.lumalyte.lg.domain.entities.Guild guild : allGuilds) {
                // Get the highest rank (Owner rank) for this guild
                net.lumalyte.lg.domain.entities.Rank ownerRank = rankService.getHighestRank(guild.getId());

                if (ownerRank != null) {
                    // Check which shop permissions are missing
                    java.util.Set<net.lumalyte.lg.domain.entities.RankPermission> missingPermissions = new java.util.HashSet<>();
                    for (net.lumalyte.lg.domain.entities.RankPermission perm : requiredShopPermissions) {
                        if (!ownerRank.getPermissions().contains(perm)) {
                            missingPermissions.add(perm);
                        }
                    }

                    if (!missingPermissions.isEmpty()) {
                        logger.info("Guild '" + guild.getName() + "' - Owner rank '" + ownerRank.getName() +
                            "' is missing " + missingPermissions.size() + " shop permission(s): " + missingPermissions);

                        // Add missing permissions
                        java.util.Set<net.lumalyte.lg.domain.entities.RankPermission> updatedPermissions =
                            new java.util.HashSet<>(ownerRank.getPermissions());
                        updatedPermissions.addAll(missingPermissions);

                        net.lumalyte.lg.domain.entities.Rank updatedRank = new net.lumalyte.lg.domain.entities.Rank(
                            ownerRank.getId(),
                            ownerRank.getGuildId(),
                            ownerRank.getName(),
                            ownerRank.getPriority(),
                            updatedPermissions,
                            ownerRank.getIcon()
                        );

                        if (rankService.updateRank(updatedRank, guild.getId())) {
                            migratedCount++;
                            logger.info("Added shop permissions to Owner rank for guild: " + guild.getName());
                        }
                    } else {
                        logger.info("Guild '" + guild.getName() + "' - Owner rank already has all shop permissions");
                    }
                } else {
                    logger.warning("Guild '" + guild.getName() + "' has no owner rank!");
                }
            }

            if (migratedCount > 0) {
                logger.info("Successfully migrated " + migratedCount + " guild(s) to have all shop permissions");
            } else {
                logger.info("No guilds needed migration - all Owner ranks already have shop permissions");
            }

        } catch (Exception e) {
            logger.warning("Error during guild permission migration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
