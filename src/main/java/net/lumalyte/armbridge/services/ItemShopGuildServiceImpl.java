package net.lumalyte.armbridge.services;

import net.lumalyte.armbridge.ARMGuildsBridge;
import org.bukkit.Location;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Implementation of ItemShopGuildService using database storage
 */
public class ItemShopGuildServiceImpl implements ItemShopGuildService {

    private final ARMGuildsBridge plugin;
    private final Logger logger;
    private final Map<String, UUID> cache = new HashMap<>(); // location key -> guild ID
    private Connection connection;

    public ItemShopGuildServiceImpl(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            // Use same database as GuildRegionRepository
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            String dbPath = new File(dataFolder, "guild_shops.db").getAbsolutePath();
            String url = "jdbc:sqlite:" + dbPath;

            connection = DriverManager.getConnection(url);
            logger.info("ItemShopGuildService connected to database");

            // Create table
            String createTable = "CREATE TABLE IF NOT EXISTS arm_guild_itemshops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "world_name TEXT NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "y INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "guild_id TEXT NOT NULL," +
                    "creator_uuid TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "UNIQUE(world_name, x, y, z)" +
                    ")";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTable);
                logger.info("ItemShop guild tracking table initialized");
            }
        } catch (SQLException e) {
            logger.severe("Failed to initialize ItemShop database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    @Override
    public boolean registerGuildItemShop(Location shopLocation, UUID guildId, UUID playerUuid) {
        String sql = "INSERT OR REPLACE INTO arm_guild_itemshops " +
                "(world_name, x, y, z, guild_id, creator_uuid, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, shopLocation.getWorld().getName());
            stmt.setInt(2, shopLocation.getBlockX());
            stmt.setInt(3, shopLocation.getBlockY());
            stmt.setInt(4, shopLocation.getBlockZ());
            stmt.setString(5, guildId.toString());
            stmt.setString(6, playerUuid.toString());
            stmt.setLong(7, System.currentTimeMillis());

            stmt.executeUpdate();

            // Update cache
            cache.put(locationKey(shopLocation), guildId);

            logger.info("Registered ItemShop at " + locationKey(shopLocation) + " for guild " + guildId);
            return true;
        } catch (SQLException e) {
            logger.severe("Failed to register guild ItemShop: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public UUID getGuildForItemShop(Location shopLocation) {
        // Check cache first
        String key = locationKey(shopLocation);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        // Query database
        String sql = "SELECT guild_id FROM arm_guild_itemshops " +
                "WHERE world_name = ? AND x = ? AND y = ? AND z = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, shopLocation.getWorld().getName());
            stmt.setInt(2, shopLocation.getBlockX());
            stmt.setInt(3, shopLocation.getBlockY());
            stmt.setInt(4, shopLocation.getBlockZ());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID guildId = UUID.fromString(rs.getString("guild_id"));
                cache.put(key, guildId);
                return guildId;
            }
        } catch (SQLException e) {
            logger.warning("Failed to query guild ItemShop: " + e.getMessage());
        }

        return null;
    }

    @Override
    public boolean isGuildItemShop(Location shopLocation) {
        return getGuildForItemShop(shopLocation) != null;
    }

    @Override
    public List<Location> getGuildItemShops(UUID guildId) {
        List<Location> shops = new ArrayList<>();

        String sql = "SELECT world_name, x, y, z FROM arm_guild_itemshops WHERE guild_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, guildId.toString());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String worldName = rs.getString("world_name");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world != null) {
                    shops.add(new Location(world, x, y, z));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to get guild ItemShops: " + e.getMessage());
        }

        return shops;
    }

    @Override
    public boolean removeGuildItemShop(Location shopLocation) {
        String sql = "DELETE FROM arm_guild_itemshops " +
                "WHERE world_name = ? AND x = ? AND y = ? AND z = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, shopLocation.getWorld().getName());
            stmt.setInt(2, shopLocation.getBlockX());
            stmt.setInt(3, shopLocation.getBlockY());
            stmt.setInt(4, shopLocation.getBlockZ());

            int rowsAffected = stmt.executeUpdate();

            // Clear cache
            cache.remove(locationKey(shopLocation));

            logger.info("Removed guild ItemShop at " + locationKey(shopLocation));
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Failed to remove guild ItemShop: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public UUID getItemShopCreator(Location shopLocation) {
        String sql = "SELECT creator_uuid FROM arm_guild_itemshops " +
                "WHERE world_name = ? AND x = ? AND y = ? AND z = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, shopLocation.getWorld().getName());
            stmt.setInt(2, shopLocation.getBlockX());
            stmt.setInt(3, shopLocation.getBlockY());
            stmt.setInt(4, shopLocation.getBlockZ());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("creator_uuid"));
            }
        } catch (SQLException e) {
            logger.warning("Failed to query ItemShop creator: " + e.getMessage());
        }

        return null;
    }
}
