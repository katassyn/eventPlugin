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
import org.maks.eventPlugin.gui.AdminRewardEditorGUI;

import org.maks.eventPlugin.listener.AttrieItemListener;
import org.maks.eventPlugin.listener.MythicMobProgressListener;

public final class EventPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private java.util.Map<String, EventManager> eventManagers;
    private BuffManager buffManager;
    private PlayerProgressGUI progressGUI;
    private AdminRewardEditorGUI rewardGUI;


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

        eventManagers = new java.util.HashMap<>();
        buffManager = new BuffManager(databaseManager);
        progressGUI = new PlayerProgressGUI();
        rewardGUI = new AdminRewardEditorGUI();
        loadActiveEvents();

        getServer().getPluginManager().registerEvents(new MythicMobProgressListener(eventManagers, buffManager), this);
        getServer().getPluginManager().registerEvents(new AttrieItemListener(this, buffManager), this);
        getServer().getPluginManager().registerEvents(progressGUI, this);
        getServer().getPluginManager().registerEvents(rewardGUI, this);

        PluginCommand cmd = getCommand("event");
        if (cmd != null) {
            cmd.setExecutor(new EventCommand(eventManagers, databaseManager, progressGUI, rewardGUI));
        } else {
            Bukkit.getLogger().warning("Event command not found in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
    }

    private void loadActiveEvents() {
        try (var conn = databaseManager.getConnection();
             var ps = conn.prepareStatement("SELECT event_id FROM events WHERE active=1")) {
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    eventManagers.put(id, new EventManager(databaseManager, id));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
