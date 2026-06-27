package net.lumalyte.armbridge.commands;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.models.EnemyAccessMode;
import net.lumalyte.armbridge.services.GuildShopService;
import net.lumalyte.armbridge.storage.ShopRegionInfo;
import net.lumalyte.lg.application.services.MemberService;
import net.lumalyte.lg.application.services.RankService;
import net.lumalyte.lg.domain.entities.RankPermission;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command handler for /guildshop - allows players to manage enemy access modes for their guild shops
 */
public class GuildShopCommand implements CommandExecutor, TabCompleter {

    private final ARMGuildsBridge plugin;
    private final GuildShopService shopService;
    private final MemberService memberService;
    private final RankService rankService;

    public GuildShopCommand(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.shopService = plugin.getGuildShopService();
        this.memberService = plugin.getMemberService();
        this.rankService = plugin.getRankService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "info":
                return handleInfo(player);
            case "setmode":
                return handleSetMode(player, args);
            case "purchasemode":
                return handlePurchaseMode(player, args);
            case "convert":
                return handleConvert(player);
            case "help":
                sendHelp(player);
                return true;
            default:
                player.sendMessage("§cUnknown subcommand. Use /guildshop help for usage.");
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== Guild Shop Commands ===");
        player.sendMessage("§e/guildshop info §7- Show info about the shop you're in");
        player.sendMessage("§e/guildshop setmode <mode> [upcharge%] §7- Set enemy access mode");
        player.sendMessage("§e/guildshop purchasemode <personal|guild> §7- Choose purchase mode");
        player.sendMessage("§e/guildshop convert §7- Convert personal stall to guild stall");
        player.sendMessage("");
        player.sendMessage("§6Enemy Access Modes:");
        player.sendMessage("  §eBAN §7- Enemies cannot enter the shop §a✓ READY");
        player.sendMessage("  §eWINDOW_SHOP §7- Enemies can view but cannot purchase §a✓ READY");
        player.sendMessage("  §eALLOW §7- Enemies have full access, no warnings §a✓ READY");
        player.sendMessage("  §eUPCHARGE §7- Enemies pay extra (automatic pricing) §a✓ READY");
        player.sendMessage("");
        player.sendMessage("§7Examples:");
        player.sendMessage("  §f/guildshop setmode BAN");
        player.sendMessage("  §f/guildshop setmode WINDOW_SHOP");
        player.sendMessage("  §f/guildshop setmode UPCHARGE 50");
    }

    private boolean handleInfo(Player player) {
        ShopRegionInfo shopInfo = getShopAtLocation(player.getLocation());

        if (shopInfo == null) {
            player.sendMessage("§cYou are not standing in a guild shop region!");
            return true;
        }

        // Check if player is in the shop owner guild
        Set<UUID> playerGuilds = memberService.getPlayerGuilds(player.getUniqueId());
        if (playerGuilds.isEmpty() || !playerGuilds.contains(shopInfo.getGuildId())) {
            player.sendMessage("§cThis shop does not belong to your guild!");
            return true;
        }

        player.sendMessage("§6§l=== Guild Shop Info ===");
        player.sendMessage("§eRegion: §f" + shopInfo.getRegionId());
        player.sendMessage("§eWorld: §f" + shopInfo.getWorldName());
        player.sendMessage("§ePurchase Price: §f" + shopInfo.getPurchasePrice());
        player.sendMessage("§eEnemy Access Mode: §f" + shopInfo.getEnemyAccessMode().name());
        player.sendMessage("  §7" + shopInfo.getEnemyAccessMode().getDescription());
        if (shopInfo.getEnemyAccessMode() == EnemyAccessMode.UPCHARGE) {
            player.sendMessage("§eUpcharge: §f" + String.format("%.0f", shopInfo.getUpchargePercentage()) + "%");
            player.sendMessage("  §7Enemies pay " + String.format("%.0f", shopInfo.getUpchargePercentage()) + "% more for items");
        }

        return true;
    }

    private boolean handleSetMode(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guildshop setmode <mode> [upcharge%]");
            player.sendMessage("§cModes: BAN, UPCHARGE, WINDOW_SHOP, ALLOW");
            return true;
        }

        // Get shop region at player location
        ShopRegionInfo shopInfo = getShopAtLocation(player.getLocation());
        if (shopInfo == null) {
            player.sendMessage("§cYou are not standing in a guild shop region!");
            return true;
        }

        // Check if player is in the shop owner guild
        Set<UUID> playerGuilds = memberService.getPlayerGuilds(player.getUniqueId());
        if (playerGuilds.isEmpty() || !playerGuilds.contains(shopInfo.getGuildId())) {
            player.sendMessage("§cThis shop does not belong to your guild!");
            return true;
        }

        UUID guildId = playerGuilds.iterator().next();

        // Check permission
        if (!memberService.hasPermission(player.getUniqueId(), guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            player.sendMessage("§cYou don't have permission to change shop settings!");
            player.sendMessage("§7Required permission: §eMANAGE_GUILD_SETTINGS");
            return true;
        }

        // Parse mode
        EnemyAccessMode mode;
        try {
            mode = EnemyAccessMode.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid mode: " + args[1]);
            player.sendMessage("§cValid modes: BAN, UPCHARGE, WINDOW_SHOP, ALLOW");
            return true;
        }

        // Parse upcharge percentage if UPCHARGE mode
        double upchargePercentage = shopInfo.getUpchargePercentage(); // Keep existing by default
        if (mode == EnemyAccessMode.UPCHARGE) {
            if (args.length >= 3) {
                try {
                    upchargePercentage = Double.parseDouble(args[2]);
                    if (upchargePercentage < 0 || upchargePercentage > 1000) {
                        player.sendMessage("§cUpcharge percentage must be between 0 and 1000!");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid upcharge percentage: " + args[2]);
                    return true;
                }
            } else {
                player.sendMessage("§cPlease specify upcharge percentage!");
                player.sendMessage("§cUsage: /guildshop setmode UPCHARGE <percentage>");
                return true;
            }
        }

        // Update mode
        boolean success = shopService.updateEnemyAccessMode(
            shopInfo.getRegionId(),
            shopInfo.getWorldName(),
            mode,
            upchargePercentage
        );

        if (success) {
            player.sendMessage("§aSuccessfully updated enemy access mode to §e" + mode.name());
            player.sendMessage("§7" + mode.getDescription());
            if (mode == EnemyAccessMode.UPCHARGE) {
                player.sendMessage("§7Enemies will pay §e" + String.format("%.0f", upchargePercentage) + "% §7more for items");
            }
        } else {
            player.sendMessage("§cFailed to update enemy access mode!");
        }

        return true;
    }

    private boolean handlePurchaseMode(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guildshop purchasemode <personal|guild>");
            return true;
        }

        String mode = args[1].toLowerCase();

        switch (mode) {
            case "personal":
                plugin.getPurchaseMode().enablePersonalMode(player.getUniqueId());
                player.sendMessage("§a✓ Purchase mode set to PERSONAL");
                player.sendMessage("§7Your next ARM region purchase will use your personal money.");
                player.sendMessage("§7Now run your §e/arm buy §7command again.");
                player.sendMessage("§8(Mode expires in 60 seconds)");
                return true;

            case "guild":
                // Check if player is in a guild
                Set<UUID> playerGuilds = memberService.getPlayerGuilds(player.getUniqueId());
                if (playerGuilds.isEmpty()) {
                    player.sendMessage("§cYou must be in a guild to use guild purchase mode!");
                    return true;
                }

                UUID guildId;
                if (playerGuilds.size() == 1) {
                    guildId = playerGuilds.iterator().next();
                } else if (args.length >= 3) {
                    guildId = resolveGuildIdByName(player, args[2], playerGuilds);
                    if (guildId == null) {
                        return true;
                    }
                } else {
                    player.sendMessage("§cYou are in multiple guilds. Specify which guild:");
                    player.sendMessage("§f/guildshop purchasemode guild <guildname>");
                    return true;
                }

                plugin.getPurchaseMode().enableGuildMode(player.getUniqueId(), guildId);
                player.sendMessage("§a✓ Purchase mode set to GUILD");
                player.sendMessage("§7Your next ARM region purchase will use your guild vault.");
                player.sendMessage("§7Now run your §e/arm buy §7command again.");
                player.sendMessage("§8(Mode expires in 60 seconds)");
                return true;

            default:
                player.sendMessage("§cInvalid mode: " + args[1]);
                player.sendMessage("§cValid modes: personal, guild");
                return true;
        }
    }

    /**
     * Converts a player's personally-owned ARM stall into a guild shop.
     * Admin-intentional bypass: this path deliberately skips PreBuyEvent / guild-vault
     * withdrawal and only reassigns landlord + DB registration for already-owned regions.
     */
    private boolean handleConvert(Player player) {
        // Check if player is in a guild
        Set<UUID> playerGuilds = memberService.getPlayerGuilds(player.getUniqueId());
        if (playerGuilds.isEmpty()) {
            player.sendMessage("§cYou must be in a guild to convert a shop to a guild shop!");
            return true;
        }

        UUID guildId = playerGuilds.iterator().next();

        // Check all required permissions for managing guild shops
        List<RankPermission> requiredPermissions = Arrays.asList(
            RankPermission.MANAGE_GUILD_SETTINGS,
            RankPermission.ACCESS_SHOP_CHESTS,
            RankPermission.EDIT_SHOP_STOCK,
            RankPermission.MODIFY_SHOP_PRICES
        );

        List<RankPermission> missingPermissions = new ArrayList<>();
        for (RankPermission permission : requiredPermissions) {
            if (!memberService.hasPermission(player.getUniqueId(), guildId, permission)) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            player.sendMessage("§cYou don't have all required permissions to convert this shop!");
            player.sendMessage("§7Missing permissions:");
            for (RankPermission permission : missingPermissions) {
                player.sendMessage("  §e- " + permission.name());
            }
            return true;
        }

        // Check if guild has reached shop limit
        if (shopService.hasReachedShopLimit(guildId)) {
            int maxShops = plugin.getConfig().getInt("shop-purchase.max-shops-per-guild", 0);
            player.sendMessage("§cYour guild has reached the maximum shop limit (" + maxShops + ")!");
            player.sendMessage("§7Cannot convert personal shop to guild shop.");
            return true;
        }

        // Get WorldGuard regions at player location
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

            if (regions == null) {
                player.sendMessage("§cWorldGuard regions not available in this world!");
                return true;
            }

            BlockVector3 position = BlockVector3.at(player.getLocation().getX(),
                                                     player.getLocation().getY(),
                                                     player.getLocation().getZ());
            ApplicableRegionSet regionSet = regions.getApplicableRegions(position);

            if (regionSet.size() == 0) {
                player.sendMessage("§cYou are not standing in a region!");
                return true;
            }

            // Try to find an ARM region by checking with ARM using reflection
            String regionId = null;
            String worldName = player.getWorld().getName();
            Object armRegion = null;

            for (ProtectedRegion wgRegion : regionSet) {
                armRegion = getARMRegionByReflection(wgRegion.getId(), worldName);
                if (armRegion != null) {
                    regionId = wgRegion.getId();
                    break;
                }
            }

            if (armRegion == null || regionId == null) {
                player.sendMessage("§cYou are not standing in an ARM shop region!");
                player.sendMessage("§7Make sure you're standing inside a region that is for sale in ARM.");
                return true;
            }

            // Check if region is sold
            boolean isSold = (Boolean) armRegion.getClass().getMethod("isSold").invoke(armRegion);
            if (!isSold) {
                player.sendMessage("§cThis region is not currently owned by anyone!");
                player.sendMessage("§7You must own the region before you can convert it to a guild shop.");
                return true;
            }

            // Check if already a guild shop
            UUID existingGuild = shopService.getGuildForShopRegion(regionId, worldName);
            if (existingGuild != null) {
                player.sendMessage("§cThis shop is already a guild shop!");
                return true;
            }

            // Get current owner using reflection
            UUID currentOwner = (UUID) armRegion.getClass().getMethod("getOwner").invoke(armRegion);

            // Check if player owns this shop
            if (currentOwner == null || !currentOwner.equals(player.getUniqueId())) {
                player.sendMessage("§cYou don't own this shop! Only the owner can convert it to a guild shop.");
                if (currentOwner != null) {
                    player.sendMessage("§7Current owner: §e" + currentOwner);
                }
                return true;
            }

            // Get shop price using reflection
            double price = (Double) armRegion.getClass().getMethod("getPricePerPeriod").invoke(armRegion);

            // Update landlord to guild using reflection
            armRegion.getClass().getMethod("setLandlord", UUID.class).invoke(armRegion, guildId);

            // Register in database
            boolean registered = shopService.registerGuildShopRegion(regionId, worldName, guildId, price);

            if (!registered) {
                player.sendMessage("§cFailed to register shop as guild shop in database!");
                // Try to revert landlord
                try {
                    armRegion.getClass().getMethod("setLandlord", UUID.class).invoke(armRegion, player.getUniqueId());
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to revert landlord after database registration failure!");
                }
                return true;
            }

            // Update WorldGuard flags
            plugin.getRelationFlagService().updateShopRegionFlags(regionId, worldName, guildId);

            // Success!
            player.sendMessage("§a✓ Successfully converted personal shop to guild shop!");
            player.sendMessage("§7Region: §e" + regionId);
            player.sendMessage("§7This shop is now owned by your guild.");
            player.sendMessage("§7All future income will go to the guild vault.");

            // Notify guild members
            String message = "§6[Guild Shop] §e" + player.getName() +
                           " §7converted shop §e" + regionId + " §7to a guild shop!";
            memberService.getGuildMembers(guildId).forEach(member -> {
                Player online = plugin.getServer().getPlayer(member.getPlayerId());
                if (online != null && online.isOnline() && !online.getUniqueId().equals(player.getUniqueId())) {
                    online.sendMessage(message);
                }
            });

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error converting shop to guild shop: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cAn error occurred while converting the shop!");
            return true;
        }
    }

    private UUID resolveGuildIdByName(Player player, String guildName, Set<UUID> playerGuilds) {
        for (UUID candidateId : playerGuilds) {
            net.lumalyte.lg.domain.entities.Guild guild = plugin.getGuildService().getGuild(candidateId);
            if (guild != null && guild.getName().equalsIgnoreCase(guildName)) {
                return candidateId;
            }
        }
        player.sendMessage("§cGuild not found or you are not a member: §f" + guildName);
        return null;
    }

    /**
     * Get ARM region object using reflection (similar to how RegionPurchaseListener does it)
     */
    private Object getARMRegionByReflection(String regionId, String worldName) {
        try {
            // Get ARM plugin
            org.bukkit.plugin.Plugin armPlugin = plugin.getServer().getPluginManager().getPlugin("AdvancedRegionMarket");
            if (armPlugin == null) {
                return null;
            }

            // Use ARM's classloader
            ClassLoader armClassLoader = armPlugin.getClass().getClassLoader();
            Class<?> armClass = Class.forName("net.alex9849.arm.AdvancedRegionMarket", true, armClassLoader);

            // Get ARM instance
            Object armInstance = armClass.getMethod("getInstance").invoke(null);

            // Get RegionManager
            Object regionManager = armClass.getMethod("getRegionManager").invoke(armInstance);

            // Get world
            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                return null;
            }

            // Try to get region - the method signature might be different
            // Let's try getRegion(String id, World world) which is most common
            try {
                return regionManager.getClass().getMethod("getRegion", String.class, org.bukkit.World.class)
                                              .invoke(regionManager, regionId, world);
            } catch (NoSuchMethodException e) {
                // Try alternative signature: getRegion(String id)
                Object region = regionManager.getClass().getMethod("getRegion", String.class)
                                           .invoke(regionManager, regionId);
                // Check if this region is in the correct world
                if (region != null) {
                    org.bukkit.World regionWorld = (org.bukkit.World) region.getClass().getMethod("getRegionworld").invoke(region);
                    if (regionWorld.getName().equals(worldName)) {
                        return region;
                    }
                }
                return null;
            }

        } catch (Exception e) {
            // Silently fail - this is expected for non-ARM regions
            return null;
        }
    }

    /**
     * Get shop region info at player's location
     */
    private ShopRegionInfo getShopAtLocation(Location location) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regions == null) {
                return null;
            }

            BlockVector3 position = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regionSet = regions.getApplicableRegions(position);

            for (ProtectedRegion region : regionSet) {
                ShopRegionInfo shopInfo = plugin.getGuildRegionRepository()
                    .getShopRegionInfo(region.getId(), location.getWorld().getName());
                if (shopInfo != null) {
                    return shopInfo;
                }
            }

            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting shop at location: " + e.getMessage());
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("info", "setmode", "purchasemode", "convert", "help").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("setmode")) {
                return Arrays.stream(EnemyAccessMode.values())
                    .map(Enum::name)
                    .filter(s -> s.startsWith(args[1].toUpperCase()))
                    .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("purchasemode")) {
                return Arrays.asList("personal", "guild").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setmode") &&
            args[1].equalsIgnoreCase("UPCHARGE")) {
            return Arrays.asList("25", "50", "75", "100");
        }

        return new ArrayList<>();
    }
}
