package net.lumalyte.armbridge.storage;

import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.models.EnemyAccessMode;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite implementation of GuildRegionRepository
 */
public class GuildRegionRepositoryImpl implements GuildRegionRepository {

    private final ARMGuildsBridge plugin;
    private final Logger logger;
    private Connection connection;

    public GuildRegionRepositoryImpl(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        initializeDatabase();
        initializeSchema();
    }

    private void initializeDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            String dbPath = new File(dataFolder, "guild_shops.db").getAbsolutePath();
            String url = "jdbc:sqlite:" + dbPath;

            connection = DriverManager.getConnection(url);
            logger.info("Connected to SQLite database: " + dbPath);

        } catch (SQLException e) {
            logger.severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void initializeSchema() {
        String createShopsTable = """
            CREATE TABLE IF NOT EXISTS arm_guild_shops (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                region_id VARCHAR(255) NOT NULL,
                world_name VARCHAR(255) NOT NULL,
                guild_id VARCHAR(36) NOT NULL,
                purchase_price REAL NOT NULL,
                purchased_at TEXT NOT NULL,
                UNIQUE(region_id, world_name)
            )
        """;

        String createIndexGuild = """
            CREATE INDEX IF NOT EXISTS idx_guild_shops_guild
            ON arm_guild_shops(guild_id)
        """;

        String createTransactionsTable = """
            CREATE TABLE IF NOT EXISTS arm_shop_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id VARCHAR(36) NOT NULL,
                region_id VARCHAR(255) NOT NULL,
                transaction_type VARCHAR(50) NOT NULL,
                amount REAL NOT NULL,
                description TEXT,
                actor_uuid VARCHAR(36),
                created_at TEXT NOT NULL
            )
        """;

        String createIndexTransactions = """
            CREATE INDEX IF NOT EXISTS idx_shop_transactions_guild
            ON arm_shop_transactions(guild_id, created_at)
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createShopsTable);
            stmt.execute(createIndexGuild);
            stmt.execute(createTransactionsTable);
            stmt.execute(createIndexTransactions);

            // Add enemy_access_mode and upcharge_percentage columns if they don't exist
            try {
                stmt.execute("ALTER TABLE arm_guild_shops ADD COLUMN enemy_access_mode VARCHAR(20) DEFAULT 'BAN'");
                logger.info("Added enemy_access_mode column to arm_guild_shops");
            } catch (SQLException e) {
                // Column already exists, ignore
            }

            try {
                stmt.execute("ALTER TABLE arm_guild_shops ADD COLUMN upcharge_percentage REAL DEFAULT 50.0");
                logger.info("Added upcharge_percentage column to arm_guild_shops");
            } catch (SQLException e) {
                // Column already exists, ignore
            }

            logger.info("Database schema initialized successfully");
        } catch (SQLException e) {
            logger.severe("Failed to initialize schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean registerGuildShopRegion(String regionId, String worldName, UUID guildId, double purchasePrice) {
        // Get default enemy access mode from config
        String defaultMode = plugin.getConfig().getString("enemy-blocking.default-mode", "BAN");
        double defaultUpcharge = plugin.getConfig().getDouble("enemy-blocking.default-upcharge-percentage", 50.0);

        String sql = """
            INSERT INTO arm_guild_shops (region_id, world_name, guild_id, purchase_price, purchased_at, enemy_access_mode, upcharge_percentage)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, worldName);
            stmt.setString(3, guildId.toString());
            stmt.setDouble(4, purchasePrice);
            stmt.setString(5, Instant.now().toString());
            stmt.setString(6, defaultMode);
            stmt.setDouble(7, defaultUpcharge);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to register guild shop region: " + e.getMessage());
            return false;
        }
    }

    @Override
    public UUID getGuildForShopRegion(String regionId, String worldName) {
        String sql = """
            SELECT guild_id FROM arm_guild_shops
            WHERE region_id = ? AND world_name = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, worldName);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("guild_id"));
            }
        } catch (SQLException e) {
            logger.warning("Failed to get guild for shop region: " + e.getMessage());
        }

        return null;
    }

    @Override
    public List<ShopRegionInfo> getGuildShopRegions(UUID guildId) {
        List<ShopRegionInfo> regions = new ArrayList<>();
        String sql = """
            SELECT region_id, world_name, guild_id, purchase_price, purchased_at,
                   enemy_access_mode, upcharge_percentage
            FROM arm_guild_shops
            WHERE guild_id = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId.toString());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String enemyAccessModeStr = rs.getString("enemy_access_mode");
                EnemyAccessMode enemyAccessMode = enemyAccessModeStr != null ?
                    EnemyAccessMode.valueOf(enemyAccessModeStr) : EnemyAccessMode.BAN;

                regions.add(new ShopRegionInfo(
                    rs.getString("region_id"),
                    rs.getString("world_name"),
                    UUID.fromString(rs.getString("guild_id")),
                    rs.getDouble("purchase_price"),
                    Instant.parse(rs.getString("purchased_at")),
                    enemyAccessMode,
                    rs.getDouble("upcharge_percentage")
                ));
            }
        } catch (SQLException e) {
            logger.warning("Failed to get guild shop regions: " + e.getMessage());
        }

        return regions;
    }

    @Override
    public ShopRegionInfo getShopRegionInfo(String regionId, String worldName) {
        String sql = """
            SELECT region_id, world_name, guild_id, purchase_price, purchased_at,
                   enemy_access_mode, upcharge_percentage
            FROM arm_guild_shops
            WHERE region_id = ? AND world_name = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, worldName);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String enemyAccessModeStr = rs.getString("enemy_access_mode");
                EnemyAccessMode enemyAccessMode = enemyAccessModeStr != null ?
                    EnemyAccessMode.valueOf(enemyAccessModeStr) : EnemyAccessMode.BAN;

                return new ShopRegionInfo(
                    rs.getString("region_id"),
                    rs.getString("world_name"),
                    UUID.fromString(rs.getString("guild_id")),
                    rs.getDouble("purchase_price"),
                    Instant.parse(rs.getString("purchased_at")),
                    enemyAccessMode,
                    rs.getDouble("upcharge_percentage")
                );
            }
        } catch (SQLException e) {
            logger.warning("Failed to get shop region info: " + e.getMessage());
        }

        return null;
    }

    @Override
    public boolean updateEnemyAccessMode(String regionId, String worldName,
                                        EnemyAccessMode mode, double upchargePercentage) {
        String sql = """
            UPDATE arm_guild_shops
            SET enemy_access_mode = ?, upcharge_percentage = ?
            WHERE region_id = ? AND world_name = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, mode.name());
            stmt.setDouble(2, upchargePercentage);
            stmt.setString(3, regionId);
            stmt.setString(4, worldName);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to update enemy access mode: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeGuildShopRegion(String regionId, String worldName) {
        String sql = """
            DELETE FROM arm_guild_shops
            WHERE region_id = ? AND world_name = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, worldName);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to remove guild shop region: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int removeAllGuildShopRegions(UUID guildId) {
        String sql = """
            DELETE FROM arm_guild_shops
            WHERE guild_id = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId.toString());

            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to remove all guild shop regions: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean logShopTransaction(UUID guildId, String regionId, String transactionType,
                                      double amount, String description, UUID actorId) {
        String sql = """
            INSERT INTO arm_shop_transactions
            (guild_id, region_id, transaction_type, amount, description, actor_uuid, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId.toString());
            stmt.setString(2, regionId);
            stmt.setString(3, transactionType);
            stmt.setDouble(4, amount);
            stmt.setString(5, description);
            stmt.setString(6, actorId != null ? actorId.toString() : null);
            stmt.setString(7, Instant.now().toString());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to log shop transaction: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<ShopTransaction> getTransactionHistory(UUID guildId, int limit) {
        List<ShopTransaction> transactions = new ArrayList<>();
        String sql = """
            SELECT id, guild_id, region_id, transaction_type, amount, description, actor_uuid, created_at
            FROM arm_shop_transactions
            WHERE guild_id = ?
            ORDER BY created_at DESC
            LIMIT ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId.toString());
            stmt.setInt(2, limit);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String actorUuidStr = rs.getString("actor_uuid");
                UUID actorId = actorUuidStr != null ? UUID.fromString(actorUuidStr) : null;

                transactions.add(new ShopTransaction(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("guild_id")),
                    rs.getString("region_id"),
                    rs.getString("transaction_type"),
                    rs.getDouble("amount"),
                    rs.getString("description"),
                    actorId,
                    Instant.parse(rs.getString("created_at"))
                ));
            }
        } catch (SQLException e) {
            logger.warning("Failed to get transaction history: " + e.getMessage());
        }

        return transactions;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.warning("Error closing database connection: " + e.getMessage());
        }
    }
}
