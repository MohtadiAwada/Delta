package dev.moti.delta.listeners;

import dev.moti.delta.Delta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class SessionListener implements Listener {
    private final Delta plugin;
    public SessionListener(Delta plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        plugin.clearSelected(e.getPlayer().getUniqueId());
    }
}
