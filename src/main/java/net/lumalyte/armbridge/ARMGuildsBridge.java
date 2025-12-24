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
        logger.info("  - Shop permissions (chest access, inventory, signs)");
        logger.info("  - Player commands (/guildshop)");
        logger.info("");
        logger.warning("⚠ NOT READY YET (waiting for ItemShops source code):");
        logger.warning("  - UPCHARGE mode automatic price modification");
        logger.warning("    → Currently shows warnings only, doesn't modify prices");
        logger.warning("  - PHYSICAL mode shop income routing");
        logger.warning("    → Use VIRTUAL mode for now");
        logger.info("");
        logger.info("See ITEMSHOPS-FORK-REQUIREMENTS.md for details");
        logger.info("=================================================");
    }

    @Override
    public void onDisable() {
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

        // Initialize RelationFlagService (stub for now)
        relationFlagService = new net.lumalyte.armbridge.services.RelationFlagServiceImpl(this);
        logger.info("Initialized RelationFlagService (stub - WorldGuard integration pending)");

        // Initialize ItemShopGuildService
        itemShopGuildService = new net.lumalyte.armbridge.services.ItemShopGuildServiceImpl(this);
        logger.info("Initialized ItemShopGuildService");
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        // Register region purchase listener (hooks into ARM PreBuyEvent)
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.RegionPurchaseListener(this),
            this
        );
        logger.info("Registered RegionPurchaseListener");

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

        // Register shop transaction listener (UPCHARGE mode - requires ItemShops fork)
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.ShopTransactionListener(this),
            this
        );
        logger.warning("Registered ShopTransactionListener - UPCHARGE mode NOT READY YET");
        logger.warning("Waiting for ItemShops source code to add PreShopTransactionEvent");
        logger.warning("Current status: UPCHARGE shows warnings but cannot auto-modify prices");

        // Register shop income listener (PHYSICAL mode income routing - requires ItemShops fork)
        getServer().getPluginManager().registerEvents(
            new net.lumalyte.armbridge.listeners.ShopIncomeListener(this),
            this
        );
        logger.warning("Registered ShopIncomeListener - PHYSICAL mode income routing NOT READY YET");
        logger.warning("Waiting for ItemShops source code to add PostShopTransactionEvent");
        logger.warning("Current status: Use VIRTUAL mode for shop income (PHYSICAL mode won't convert to RAW_GOLD)");
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
}
