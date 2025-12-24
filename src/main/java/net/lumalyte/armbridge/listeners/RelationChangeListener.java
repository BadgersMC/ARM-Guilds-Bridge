package net.lumalyte.armbridge.listeners;

import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.services.RelationFlagService;
import net.lumalyte.lg.domain.events.GuildRelationChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens to LumaGuilds relation change events and updates WorldGuard flags accordingly
 */
public class RelationChangeListener implements Listener {

    private final ARMGuildsBridge plugin;
    private final RelationFlagService relationFlagService;

    public RelationChangeListener(ARMGuildsBridge plugin) {
        this.plugin = plugin;
        this.relationFlagService = plugin.getRelationFlagService();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGuildRelationChange(GuildRelationChangeEvent event) {
        // Update WorldGuard flags for all shop regions owned by these guilds
        relationFlagService.updateRegionFlagsForRelation(
            event.getGuild1(),
            event.getGuild2(),
            event.getNewRelationType()
        );

        plugin.getLogger().info("Updated shop region flags for relation change: " +
            event.getGuild1() + " <-> " + event.getGuild2() + " = " + event.getNewRelationType());
    }
}
