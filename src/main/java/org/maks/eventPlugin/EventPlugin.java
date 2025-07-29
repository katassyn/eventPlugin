package org.maks.eventPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.maks.eventPlugin.command.EventCommand;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.db.DatabaseManager;
import org.maks.eventPlugin.eventsystem.BuffManager;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.gui.PlayerProgressGUI;
import org.maks.eventPlugin.listener.AttrieItemListener;
import org.maks.eventPlugin.listener.MythicMobProgressListener;

public final class EventPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EventManager eventManager;
    private BuffManager buffManager;
    private PlayerProgressGUI progressGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.load();

        String host = configManager.getString("database.host");
        String port = configManager.getString("database.port");
        String db = configManager.getString("database.name");
        String user = configManager.getString("database.user");
        String pass = configManager.getString("database.password");

        databaseManager = new DatabaseManager();
        databaseManager.connect(host, port, db, user, pass);
        databaseManager.setupTables();
        eventManager = new EventManager(databaseManager);
        buffManager = new BuffManager(databaseManager);
        progressGUI = new PlayerProgressGUI(eventManager);

        getServer().getPluginManager().registerEvents(new MythicMobProgressListener(eventManager, buffManager), this);
        getServer().getPluginManager().registerEvents(new AttrieItemListener(this, buffManager), this);
        getServer().getPluginManager().registerEvents(progressGUI, this);

        PluginCommand cmd = getCommand("event");
        if (cmd != null) {
            cmd.setExecutor(new EventCommand(eventManager, progressGUI));
        } else {
            Bukkit.getLogger().warning("Event command not found in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
    }
}
