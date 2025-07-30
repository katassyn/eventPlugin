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
        progressGUI = new PlayerProgressGUI(buffManager);
        rewardGUI = new AdminRewardEditorGUI();
        loadActiveEvents();
        loadConfiguredEvents();

        if (getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            getServer().getPluginManager().registerEvents(new MythicMobProgressListener(eventManagers, buffManager), this);
        } else {
            Bukkit.getLogger().warning("MythicMobs not found - progress events disabled");
        }
        getServer().getPluginManager().registerEvents(new AttrieItemListener(this, buffManager), this);
        getServer().getPluginManager().registerEvents(progressGUI, this);
        getServer().getPluginManager().registerEvents(rewardGUI, this);
        PluginCommand cmd = getCommand("event");
        if (cmd != null) {
            cmd.setExecutor(new EventCommand(eventManagers, databaseManager, progressGUI, rewardGUI, configManager));
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

    private void loadConfiguredEvents() {
        var sec = configManager.getSection("events");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            var eSec = sec.getConfigurationSection(id);
            if (eSec == null) continue;
            boolean active = eSec.getBoolean("active", false);
            String name = eSec.getString("name", id);
            String desc = eSec.getString("description", "");
            int max = eSec.getInt("max_progress", 25000);
            int durationDays = eSec.getInt("duration_days", 0);

            EventManager manager = eventManagers.get(id);
            if (manager == null) {
                manager = new EventManager(databaseManager, id);
                eventManagers.put(id, manager);
            }

            var dropSec = eSec.getConfigurationSection("drop_chances");
            if (dropSec != null) {
                java.util.Map<Integer, Double> map = new java.util.HashMap<>();
                for (String k : dropSec.getKeys(false)) {
                    map.put(Integer.parseInt(k), dropSec.getDouble(k) / 100.0);
                }
                manager.setDropChances(map);
            }

            if (active) {
                long dur = durationDays > 0 ? durationDays * 86400L : 0;
                manager.start(name, desc, max, dur);
            }
        }

    }
}
