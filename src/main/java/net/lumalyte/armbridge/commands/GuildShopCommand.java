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
        player.sendMessage("");
        player.sendMessage("§6Enemy Access Modes:");
        player.sendMessage("  §eBAN §7- Enemies cannot enter the shop §a✓ READY");
        player.sendMessage("  §eWINDOW_SHOP §7- Enemies can view but cannot purchase §a✓ READY");
        player.sendMessage("  §eALLOW §7- Enemies have full access, no warnings §a✓ READY");
        player.sendMessage("  §eUPCHARGE §7- Enemies pay extra §c⚠ NOT READY YET");
        player.sendMessage("");
        player.sendMessage("§c§l⚠ UPCHARGE Mode - NOT READY YET:");
        player.sendMessage("§7Waiting for ItemShops source code to enable automatic pricing.");
        player.sendMessage("§7Current status: Shows warnings to enemies but doesn't modify prices.");
        player.sendMessage("§7You can set UPCHARGE mode now - it will work once ItemShops is forked.");
        player.sendMessage("");
        player.sendMessage("§7Examples:");
        player.sendMessage("  §f/guildshop setmode BAN");
        player.sendMessage("  §f/guildshop setmode WINDOW_SHOP");
        player.sendMessage("  §f/guildshop setmode UPCHARGE 50 §c(warnings only for now)");
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
            player.sendMessage("");
            player.sendMessage("§c§l⚠ UPCHARGE mode is NOT READY YET");
            player.sendMessage("§7Enemies see warnings but prices aren't modified.");
            player.sendMessage("§7Waiting for ItemShops source code.");
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
            if (mode == EnemyAccessMode.UPCHARGE) {
                player.sendMessage("");
                player.sendMessage("§c§l⚠ WARNING - UPCHARGE MODE NOT READY YET ⚠");
                player.sendMessage("§7Waiting for ItemShops source code.");
                player.sendMessage("§7Current status:");
                player.sendMessage("  §e✓ §7Enemies see §e" + String.format("%.0f", upchargePercentage) + "% §7upcharge warnings");
                player.sendMessage("  §c✗ §7Prices NOT automatically modified yet");
                player.sendMessage("§7Mode saved - will work automatically once ItemShops is forked.");
                player.sendMessage("");
            } else {
                player.sendMessage("§7" + mode.getDescription());
            }
        } else {
            player.sendMessage("§cFailed to update enemy access mode!");
        }

        return true;
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
            return Arrays.asList("info", "setmode", "help").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setmode")) {
            return Arrays.stream(EnemyAccessMode.values())
                .map(Enum::name)
                .filter(s -> s.startsWith(args[1].toUpperCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setmode") &&
            args[1].equalsIgnoreCase("UPCHARGE")) {
            return Arrays.asList("25", "50", "75", "100");
        }

        return new ArrayList<>();
    }
}
