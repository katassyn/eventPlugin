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

import org.maks.eventPlugin.fullmoon.FullMoonManager;
import org.maks.eventPlugin.fullmoon.gui.MapSelectionGUI;
import org.maks.eventPlugin.fullmoon.gui.QuestGUI;
import org.maks.eventPlugin.fullmoon.gui.Map2TransitionGUI;
import org.maks.eventPlugin.fullmoon.listener.FullMoonMobListener;
import org.maks.eventPlugin.fullmoon.listener.BloodVialSummonListener;
import org.maks.eventPlugin.fullmoon.listener.CursedAmphoryListener;
import org.maks.eventPlugin.fullmoon.listener.Map2BossListener;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;
import org.maks.eventPlugin.command.FullMoonCommand;
import org.maks.eventPlugin.gui.EventsMainGUI;
import org.maks.eventPlugin.api.EventPluginAPI;

public final class EventPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private java.util.Map<String, EventManager> eventManagers;
    private BuffManager buffManager;
    private PlayerProgressGUI progressGUI;
    private AdminRewardEditorGUI rewardGUI;

    // Full Moon components
    private FullMoonManager fullMoonManager;
    private MapSelectionGUI mapSelectionGUI;
    private QuestGUI questGUI;
    private Map2TransitionGUI map2TransitionGUI;
    private EventsMainGUI eventsMainGUI;
    private org.maks.eventPlugin.fullmoon.gui.AdminQuestRewardEditorGUI adminQuestRewardGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.load();

        // Initialize IngredientPouch integration
        PouchHelper.initialize();

        String host = configManager.getString("database.host");
        String port = configManager.getString("database.port");
        String db = configManager.getString("database.name");
        String user = configManager.getString("database.user");
        String pass = configManager.getString("database.password");

        databaseManager = new DatabaseManager();
        try {
            databaseManager.connect(host, port, db, user, pass);
            databaseManager.setupTables();
        } catch (Exception ex) {
            getLogger().severe("Could not connect to the database: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        eventManagers = new java.util.HashMap<>();
        buffManager = new BuffManager(databaseManager);
        progressGUI = new PlayerProgressGUI(buffManager);
        rewardGUI = new AdminRewardEditorGUI(this);

        loadActiveEvents();
        loadConfiguredEvents();

        progressGUI.setAllEvents(eventManagers);

        // Initialize public API
        EventPluginAPI.initialize(eventManagers);
        getLogger().info("EventPlugin API initialized with " + eventManagers.size() + " event(s)");

        // Initialize Full Moon components
        initializeFullMoon();

        if (getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            getServer().getPluginManager().registerEvents(new MythicMobProgressListener(eventManagers, buffManager), this);

            // Register Full Moon listeners if event exists
            if (fullMoonManager != null) {
                getServer().getPluginManager().registerEvents(new FullMoonMobListener(fullMoonManager, buffManager, map2TransitionGUI), this);
                getServer().getPluginManager().registerEvents(new BloodVialSummonListener(fullMoonManager, configManager), this);
                getServer().getPluginManager().registerEvents(new CursedAmphoryListener(fullMoonManager.getCursedAmphoryManager()), this);
                getServer().getPluginManager().registerEvents(new Map2BossListener(fullMoonManager), this);
                getServer().getPluginManager().registerEvents(new org.maks.eventPlugin.fullmoon.listener.Map2PlayerListener(fullMoonManager), this);
                Bukkit.getLogger().info("[EventPlugin] Full Moon listeners registered");
            }
        } else {
            Bukkit.getLogger().warning("MythicMobs not found - progress events disabled");
        }
        getServer().getPluginManager().registerEvents(new AttrieItemListener(this, buffManager), this);
        getServer().getPluginManager().registerEvents(progressGUI, this);
        getServer().getPluginManager().registerEvents(rewardGUI, this);

        // Register Full Moon GUIs
        if (mapSelectionGUI != null) {
            getServer().getPluginManager().registerEvents(mapSelectionGUI, this);
        }
        if (questGUI != null) {
            getServer().getPluginManager().registerEvents(questGUI, this);
        }
        if (map2TransitionGUI != null) {
            getServer().getPluginManager().registerEvents(map2TransitionGUI, this);
        }
        if (eventsMainGUI != null) {
            getServer().getPluginManager().registerEvents(eventsMainGUI, this);
        }
        if (adminQuestRewardGUI != null) {
            getServer().getPluginManager().registerEvents(adminQuestRewardGUI, this);
        }

        // Register /event command
        PluginCommand cmd = getCommand("event");
        if (cmd != null) {
            EventCommand eventCommand = new EventCommand(eventManagers, databaseManager, progressGUI, rewardGUI, configManager);
            eventCommand.setEventsMainGUI(eventsMainGUI);
            eventCommand.setFullMoonManager(fullMoonManager); // Pass FullMoonManager for quest reset
            cmd.setExecutor(eventCommand);
        } else {
            Bukkit.getLogger().warning("Event command not found in plugin.yml");
        }

        // Register /fullmoon command
        PluginCommand fullMoonCmd = getCommand("fullmoon");
        if (fullMoonCmd != null && fullMoonManager != null) {
            fullMoonCmd.setExecutor(new FullMoonCommand(fullMoonManager, mapSelectionGUI, questGUI, adminQuestRewardGUI));
            Bukkit.getLogger().info("[EventPlugin] Full Moon command registered");
        }
    }

    /**
     * Initialize Full Moon event components if the event is configured.
     */
    private void initializeFullMoon() {
        EventManager fullMoonEvent = eventManagers.get("full_moon");
        if (fullMoonEvent != null) {
            fullMoonManager = new FullMoonManager(this, databaseManager, configManager, fullMoonEvent);
            mapSelectionGUI = new MapSelectionGUI(this, fullMoonManager);
            questGUI = new QuestGUI(fullMoonManager);
            map2TransitionGUI = new Map2TransitionGUI(fullMoonManager);
            eventsMainGUI = new EventsMainGUI(this, eventManagers, progressGUI, fullMoonManager, mapSelectionGUI, questGUI);
            adminQuestRewardGUI = new org.maks.eventPlugin.fullmoon.gui.AdminQuestRewardEditorGUI(this, fullMoonManager.getQuestManager());

            // Cleanup any leftover Map2 instances from previous server run/crash
            fullMoonManager.getMap2InstanceManager().cleanupAll();
            Bukkit.getLogger().info("[EventPlugin] Cleaned up any leftover Map2 instances from previous run");

            Bukkit.getLogger().info("[EventPlugin] Full Moon event initialized");
        } else {
            Bukkit.getLogger().info("[EventPlugin] Full Moon event not found in configuration");
        }
    }

    @Override
    public void onDisable() {
        // Cleanup all Map2 instances before shutdown
        if (fullMoonManager != null) {
            fullMoonManager.getMap2InstanceManager().cleanupAll();
            Bukkit.getLogger().info("[EventPlugin] Cleaned up all Map2 instances on shutdown");
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void loadActiveEvents() {
        try (var conn = databaseManager.getConnection();
             var ps = conn.prepareStatement("SELECT event_id FROM events WHERE active=1")) {
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    EventManager manager = new EventManager(databaseManager, id);
                    manager.setConfigManager(configManager);
                    eventManagers.put(id, manager);
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
                manager.setConfigManager(configManager);
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

            if (active && !manager.isActive()) {
                long dur = durationDays > 0 ? durationDays * 86400L : 0;
                manager.start(name, desc, max, dur);
            }
        }

    }
}
