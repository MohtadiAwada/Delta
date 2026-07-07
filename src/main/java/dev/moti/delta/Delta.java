package dev.moti.delta;

import dev.moti.delta.commands.DeltaTabCompleter;
import dev.moti.delta.listeners.SessionListener;
import dev.moti.delta.registry.RegistryManager;
import org.bukkit.plugin.java.JavaPlugin;
import dev.moti.delta.commands.DeltaCommand;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public final class Delta extends JavaPlugin {

    private RegistryManager registryManager;
    private final Map<UUID, String> selectedProject = new HashMap<>();

    @Override
    public void onEnable() {
        File worldContainer = getServer().getWorldContainer();
        File deltaDir = new File(worldContainer, ".delta");
        deltaDir.mkdirs();
        registryManager = new RegistryManager(deltaDir);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);
        try {
            registryManager.load();
        } catch (IOException e) {
            getLogger().severe("Delta: Failed to load registry: " + e.getMessage());
        }

        getCommand("delta").setExecutor(new DeltaCommand(this));
        getCommand("delta").setTabCompleter(new DeltaTabCompleter(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public RegistryManager getRegistryManager() {
        return registryManager;
    }

    public void setSelected(UUID playerId, String projectName) {
        selectedProject.put(playerId, projectName);
    }

    public String getSelected (UUID playerId) {
        return selectedProject.get(playerId);
    }

    public void clearSelected(UUID playerId) {
        selectedProject.remove(playerId);
    }
}
