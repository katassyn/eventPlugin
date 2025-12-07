package org.maks.eventPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.maks.eventPlugin.command.EventCommand;
import org.maks.eventPlugin.command.EventHubCommand;
import org.maks.eventPlugin.command.EventGUICommand;
import org.maks.eventPlugin.command.FullMoonQuestsCommand;
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

    // New Moon components
    private org.maks.eventPlugin.newmoon.NewMoonManager newMoonManager;
    private org.maks.eventPlugin.newmoon.gui.NewMoonQuestGUI newMoonQuestGUI;
    private org.maks.eventPlugin.newmoon.gui.Map1SelectionGUI newMoonMap1SelectionGUI;
    private org.maks.eventPlugin.newmoon.gui.PortalConfirmationGUI newMoonPortalGUI;
    private org.maks.eventPlugin.newmoon.gui.AdminQuestRewardEditorGUI newMoonAdminQuestRewardGUI;

    // Winter Event components
    private org.maks.eventPlugin.winterevent.WinterEventManager winterEventManager;
    private org.maks.eventPlugin.winterevent.summit.gui.DifficultySelectionGUI winterDifficultyGUI;
    private org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveGUI winterCaveGUI;
    private org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveRewardsGUI winterCaveRewardsGUI;
    private org.maks.eventPlugin.winterevent.gui.WinterQuestGUI winterQuestGUI;
    private org.maks.eventPlugin.winterevent.gui.AdminWinterQuestRewardEditorGUI winterAdminQuestRewardGUI;

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

        // Initialize New Moon components
        initializeNewMoon();

        // Initialize Winter Event components
        initializeWinterEvent();

        // Initialize Events Main GUI (requires both Full Moon and New Moon to be initialized)
        if (fullMoonManager != null && newMoonManager != null) {
            eventsMainGUI = new EventsMainGUI(
                this,
                eventManagers,
                progressGUI,
                fullMoonManager,
                mapSelectionGUI,
                questGUI,
                newMoonManager,
                newMoonMap1SelectionGUI,
                newMoonQuestGUI,
                winterEventManager,
                winterDifficultyGUI,
                winterCaveGUI,
                databaseManager
            );
            Bukkit.getLogger().info("[EventPlugin] Events Main GUI initialized with both Full Moon and New Moon");
        } else if (fullMoonManager != null) {
            // Only Full Moon available - use null for New Moon parameters
            eventsMainGUI = new EventsMainGUI(
                this,
                eventManagers,
                progressGUI,
                fullMoonManager,
                mapSelectionGUI,
                questGUI,
                null,
                null,
                null,
                winterEventManager,
                winterDifficultyGUI,
                winterCaveGUI,
                databaseManager
            );
            Bukkit.getLogger().info("[EventPlugin] Events Main GUI initialized with Full Moon only");
        } else if (newMoonManager != null) {
            // Only New Moon available - use null for Full Moon parameters
            eventsMainGUI = new EventsMainGUI(
                this,
                eventManagers,
                progressGUI,
                null,
                null,
                null,
                newMoonManager,
                newMoonMap1SelectionGUI,
                newMoonQuestGUI,
                winterEventManager,
                winterDifficultyGUI,
                winterCaveGUI,
                databaseManager
            );
            Bukkit.getLogger().info("[EventPlugin] Events Main GUI initialized with New Moon only");
        }

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

            // Register New Moon listeners if event exists
            if (newMoonManager != null) {
                getServer().getPluginManager().registerEvents(new org.maks.eventPlugin.newmoon.listener.NewMoonMobListener(newMoonManager), this);
                getServer().getPluginManager().registerEvents(new org.maks.eventPlugin.newmoon.listener.CauldronListener(newMoonManager), this);
                getServer().getPluginManager().registerEvents(new org.maks.eventPlugin.newmoon.listener.LordRespawnListener(newMoonManager), this);
                getServer().getPluginManager().registerEvents(new org.maks.eventPlugin.newmoon.listener.LordImmunityListener(newMoonManager), this);
                getServer().getPluginManager().registerEvents(new org.maks.eventPlugin.newmoon.listener.PortalListener(newMoonManager, newMoonPortalGUI), this);
                getServer().getPluginManager().registerEvents(new org.maks.eventPlugin.newmoon.listener.Map2PlayerListener(newMoonManager), this);
                Bukkit.getLogger().info("[EventPlugin] New Moon listeners registered");
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

        // Register New Moon GUIs
        if (newMoonQuestGUI != null) {
            getServer().getPluginManager().registerEvents(newMoonQuestGUI, this);
        }
        if (newMoonMap1SelectionGUI != null) {
            getServer().getPluginManager().registerEvents(newMoonMap1SelectionGUI, this);
        }
        if (newMoonPortalGUI != null) {
            getServer().getPluginManager().registerEvents(newMoonPortalGUI, this);
        }
        if (newMoonAdminQuestRewardGUI != null) {
            getServer().getPluginManager().registerEvents(newMoonAdminQuestRewardGUI, this);
        }

        // Register /event command
        PluginCommand cmd = getCommand("event");
        if (cmd != null) {
            EventCommand eventCommand = new EventCommand(eventManagers, databaseManager, progressGUI, rewardGUI, configManager);
            eventCommand.setFullMoonManager(fullMoonManager); // Pass FullMoonManager for quest reset
            eventCommand.setNewMoonManager(newMoonManager); // Add New Moon Manager
            cmd.setExecutor(eventCommand);
        } else {
            Bukkit.getLogger().warning("Event command not found in plugin.yml");
        }

        // Register /event_hub command
        PluginCommand eventHubCmd = getCommand("event_hub");
        if (eventHubCmd != null && eventsMainGUI != null) {
            eventHubCmd.setExecutor(new EventHubCommand(eventsMainGUI));
            Bukkit.getLogger().info("[EventPlugin] Event hub command registered");
        }

        // Register /event_gui command
        PluginCommand eventGUICmd = getCommand("event_gui");
        if (eventGUICmd != null) {
            eventGUICmd.setExecutor(new EventGUICommand(eventManagers, progressGUI));
            Bukkit.getLogger().info("[EventPlugin] Event GUI command registered");
        }

        // Register /fullmoon command
        PluginCommand fullMoonCmd = getCommand("fullmoon");
        if (fullMoonCmd != null && fullMoonManager != null) {
            fullMoonCmd.setExecutor(new FullMoonCommand(fullMoonManager, mapSelectionGUI, adminQuestRewardGUI));
            Bukkit.getLogger().info("[EventPlugin] Full Moon command registered");
        }

        // Register /fullmoon_quests command
        PluginCommand fullMoonQuestsCmd = getCommand("fullmoon_quests");
        if (fullMoonQuestsCmd != null && fullMoonManager != null && questGUI != null) {
            fullMoonQuestsCmd.setExecutor(new FullMoonQuestsCommand(fullMoonManager, questGUI));
            Bukkit.getLogger().info("[EventPlugin] Full Moon quests command registered");
        }

        // Register /new_moon_quest command
        PluginCommand newMoonQuestCmd = getCommand("new_moon_quest");
        if (newMoonQuestCmd != null && newMoonManager != null && newMoonQuestGUI != null) {
            newMoonQuestCmd.setExecutor(new org.maks.eventPlugin.newmoon.command.NewMoonQuestCommand(newMoonManager, newMoonQuestGUI));
            Bukkit.getLogger().info("[EventPlugin] New Moon quest command registered");
        }

        // Register /new_moon command
        PluginCommand newMoonCmd = getCommand("new_moon");
        if (newMoonCmd != null && newMoonManager != null) {
            newMoonCmd.setExecutor(new org.maks.eventPlugin.newmoon.command.NewMoonCommand(newMoonManager, newMoonMap1SelectionGUI, newMoonAdminQuestRewardGUI));
            Bukkit.getLogger().info("[EventPlugin] New Moon command registered");
        }

        // Register /seteventshowcase command
        PluginCommand setShowcaseCmd = getCommand("seteventshowcase");
        if (setShowcaseCmd != null) {
            org.maks.eventPlugin.gui.EventRewardPreviewDAO rewardPreviewDAO = new org.maks.eventPlugin.gui.EventRewardPreviewDAO(this, databaseManager);
            setShowcaseCmd.setExecutor(new org.maks.eventPlugin.command.SetEventShowcaseCommand(this, rewardPreviewDAO));
            Bukkit.getLogger().info("[EventPlugin] SetEventShowcase command registered");
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
            adminQuestRewardGUI = new org.maks.eventPlugin.fullmoon.gui.AdminQuestRewardEditorGUI(this, fullMoonManager.getQuestManager());

            // Cleanup any leftover Map2 instances from previous server run/crash
            fullMoonManager.getMap2InstanceManager().cleanupAll();
            Bukkit.getLogger().info("[EventPlugin] Cleaned up any leftover Map2 instances from previous run");

            Bukkit.getLogger().info("[EventPlugin] Full Moon event initialized");
        } else {
            Bukkit.getLogger().info("[EventPlugin] Full Moon event not found in configuration");
        }
    }

    /**
     * Initialize New Moon event components if the event is configured.
     */
    private void initializeNewMoon() {
        EventManager newMoonEvent = eventManagers.get("new_moon");
        if (newMoonEvent != null) {
            newMoonManager = new org.maks.eventPlugin.newmoon.NewMoonManager(this, databaseManager, configManager, newMoonEvent);
            newMoonQuestGUI = new org.maks.eventPlugin.newmoon.gui.NewMoonQuestGUI(newMoonManager);
            newMoonMap1SelectionGUI = new org.maks.eventPlugin.newmoon.gui.Map1SelectionGUI(newMoonManager);

            // Create PortalListener first (with null GUI temporarily)
            var portalListener = new org.maks.eventPlugin.newmoon.listener.PortalListener(newMoonManager, null);

            // Create PortalConfirmationGUI with PortalListener reference
            newMoonPortalGUI = new org.maks.eventPlugin.newmoon.gui.PortalConfirmationGUI(newMoonManager, portalListener);

            // Update PortalListener with the GUI reference
            portalListener.setPortalGUI(newMoonPortalGUI);

            newMoonAdminQuestRewardGUI = new org.maks.eventPlugin.newmoon.gui.AdminQuestRewardEditorGUI(this, newMoonManager.getQuestManager());

            Bukkit.getLogger().info("[EventPlugin] New Moon event initialized");
        } else {
            Bukkit.getLogger().info("[EventPlugin] New Moon event not found in configuration");
        }
    }

    /**
     * Initialize Winter Event components if the event is configured.
     */
    private void initializeWinterEvent() {
        EventManager winterEvent = eventManagers.get("winter_event");
        if (winterEvent != null) {
            winterEventManager = new org.maks.eventPlugin.winterevent.WinterEventManager(this, databaseManager, configManager, winterEvent);

            // Create GUIs
            winterDifficultyGUI = new org.maks.eventPlugin.winterevent.summit.gui.DifficultySelectionGUI(winterEventManager, configManager);
            winterCaveGUI = new org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveGUI(winterEventManager.getWinterCaveManager());
            winterCaveRewardsGUI = new org.maks.eventPlugin.winterevent.wintercave.gui.WinterCaveRewardsGUI(winterEventManager.getWinterCaveManager());
            winterQuestGUI = new org.maks.eventPlugin.winterevent.gui.WinterQuestGUI(winterEventManager.getQuestManager());
            winterAdminQuestRewardGUI = new org.maks.eventPlugin.winterevent.gui.AdminWinterQuestRewardEditorGUI(winterEventManager.getQuestManager(), this);

            // Register listeners
            getServer().getPluginManager().registerEvents(
                new org.maks.eventPlugin.winterevent.listener.GiftDropListener(winterEventManager), this);
            getServer().getPluginManager().registerEvents(
                new org.maks.eventPlugin.winterevent.listener.WinterEventMobListener(winterEventManager, buffManager), this);
            getServer().getPluginManager().registerEvents(
                new org.maks.eventPlugin.winterevent.wintercave.listener.WinterCaveMobListener(winterEventManager.getWinterCaveManager()), this);
            getServer().getPluginManager().registerEvents(
                new org.maks.eventPlugin.winterevent.wintercave.listener.WinterCavePlayerListener(winterEventManager.getWinterCaveManager()), this);
            getServer().getPluginManager().registerEvents(
                new org.maks.eventPlugin.winterevent.summit.listener.SummitInteractionListener(winterEventManager.getWinterSummitManager(), winterEventManager, configManager), this);
            getServer().getPluginManager().registerEvents(
                new org.maks.eventPlugin.winterevent.summit.listener.SummitBossListener(winterEventManager.getWinterSummitManager(), winterEventManager, configManager, this), this);

            // Register GUIs as listeners
            getServer().getPluginManager().registerEvents(winterDifficultyGUI, this);
            getServer().getPluginManager().registerEvents(winterCaveGUI, this);
            getServer().getPluginManager().registerEvents(winterCaveRewardsGUI, this);
            getServer().getPluginManager().registerEvents(winterQuestGUI, this);
            getServer().getPluginManager().registerEvents(winterAdminQuestRewardGUI, this);

            // Register commands
            org.maks.eventPlugin.winterevent.wintercave.command.WinterCaveCommand winterCaveCommand =
                new org.maks.eventPlugin.winterevent.wintercave.command.WinterCaveCommand(winterCaveGUI, winterCaveRewardsGUI);
            getCommand("winter_cave").setExecutor(winterCaveCommand);
            getCommand("winter_cave_rewards").setExecutor(winterCaveCommand);

            org.maks.eventPlugin.winterevent.command.WinterQuestCommand winterQuestCommand =
                new org.maks.eventPlugin.winterevent.command.WinterQuestCommand(winterQuestGUI, winterAdminQuestRewardGUI);
            getCommand("winter_quests").setExecutor(winterQuestCommand);

            // Cleanup leftover instances
            winterEventManager.getWinterCaveManager().cleanupLeftoverInstance();
            winterEventManager.getWinterSummitManager().cleanupLeftoverInstances();

            Bukkit.getLogger().info("[EventPlugin] Winter Event initialized");
        } else {
            Bukkit.getLogger().info("[EventPlugin] Winter Event not found in configuration");
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
