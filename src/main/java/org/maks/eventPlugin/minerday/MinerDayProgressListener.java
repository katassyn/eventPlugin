package org.maks.eventPlugin.minerday;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Method;

/**
 * Listener for Miner Day event progress.
 * Listens to OreMinedEvent and SphereCompleteEvent from mineSystemPlugin and adds progress.
 * Progress is gained when:
 * - Mining any ore (custom or vanilla)
 * - Mining plants (MOSS_BLOCK)
 * - Mining bones (BONE_BLOCK)
 * - Mining crystals (AMETHYST_BLOCK)
 */
public class MinerDayProgressListener implements Listener {
    private final MinerDayManager minerDayManager;
    private boolean listenerRegistered = false;
    private Class<?> oreMinedEventClass;
    private Class<?> sphereCompleteEventClass;
    private Method getPlayerMethod;
    private Method getAmountMethod;

    public MinerDayProgressListener(MinerDayManager minerDayManager) {
        this.minerDayManager = minerDayManager;
        initializeReflection();
    }

    private void initializeReflection() {
        try {
            // Try to load OreMinedEvent class from mineSystemPlugin
            oreMinedEventClass = Class.forName("org.maks.mineSystemPlugin.events.OreMinedEvent");
            getPlayerMethod = oreMinedEventClass.getMethod("getPlayer");
            getAmountMethod = oreMinedEventClass.getMethod("getAmount");
            Bukkit.getLogger().info("[MinerDay] Successfully loaded OreMinedEvent class");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Bukkit.getLogger().warning("[MinerDay] mineSystemPlugin not found - ore mining progress will not be tracked");
        }

        try {
            // Try to load SphereCompleteEvent for bonus progress
            sphereCompleteEventClass = Class.forName("org.maks.mineSystemPlugin.events.SphereCompleteEvent");
            Bukkit.getLogger().info("[MinerDay] Successfully loaded SphereCompleteEvent class");
        } catch (ClassNotFoundException e) {
            // Optional - sphere complete is not critical
        }
    }

    /**
     * Register dynamic listener for OreMinedEvent.
     * This method should be called by the plugin to register the listener.
     */
    public void registerDynamicListener(Plugin plugin) {
        if (listenerRegistered || oreMinedEventClass == null) {
            return;
        }

        try {
            // Register this listener for OreMinedEvent
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
            Bukkit.getLogger().info("[MinerDay] Dynamic listener registered for mining events");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[MinerDay] Failed to register dynamic listener: " + e.getMessage());
        }
    }

    /**
     * Handle OreMinedEvent from mineSystemPlugin.
     * Called via reflection-based event handling.
     */
    public void handleOreMined(Object event) {
        if (!minerDayManager.isActive()) {
            return;
        }

        try {
            Player player = (Player) getPlayerMethod.invoke(event);
            // Each mined ore = 1 progress (regardless of amount dropped)
            minerDayManager.addProgress(player, 1);
        } catch (Exception e) {
            // Silently ignore
        }
    }
}
