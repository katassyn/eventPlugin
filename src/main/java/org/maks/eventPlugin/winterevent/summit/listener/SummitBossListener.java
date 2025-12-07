package org.maks.eventPlugin.winterevent.summit.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.config.ConfigManager;
import org.maks.eventPlugin.winterevent.WinterEventManager;
import org.maks.eventPlugin.winterevent.summit.WinterSummitInstance;
import org.maks.eventPlugin.winterevent.summit.WinterSummitManager;

import java.util.Map;
import java.util.Random;

/**
 * Listens for boss deaths in Winter Summit instances.
 * Awards progress and cleans up instances.
 */
public class SummitBossListener implements Listener {
    private final WinterSummitManager summitManager;
    private final WinterEventManager winterEventManager;
    private final ConfigManager config;
    private final JavaPlugin plugin;
    private final Random random = new Random();

    public SummitBossListener(WinterSummitManager summitManager, WinterEventManager winterEventManager, ConfigManager config, JavaPlugin plugin) {
        this.summitManager = summitManager;
        this.winterEventManager = winterEventManager;
        this.config = config;
        this.plugin = plugin;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!(event.getKiller() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getKiller();
        String mobType = event.getMobType().getInternalName();

        // Check if this is a Winter Summit boss
        if (!isSummitBoss(mobType)) {
            return;
        }

        // Get instance by player
        WinterSummitInstance instance = summitManager.getInstanceByPlayer(player.getUniqueId());
        if (instance == null) {
            return;
        }

        // Award progress
        awardBossProgress(player, instance.getDifficulty());

        player.sendMessage("§f§l[Winter Event] §aBoss defeated! You earned event progress.");
        player.sendMessage("§f§l[Winter Event] §eArena will close in 15 seconds...");

        // Cleanup instance after 15 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            summitManager.cleanupInstance(instance.getInstanceId());

            // Teleport player to spawn
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
        }, 15 * 20L);
    }

    /**
     * Check if mob is a Winter Summit boss.
     */
    private boolean isSummitBoss(String mobType) {
        return mobType.equals("gluttonous_bear_inf") || mobType.equals("gluttonous_bear_hell") || mobType.equals("gluttonous_bear_blood") ||
                mobType.equals("krampus_spirit_inf") || mobType.equals("krampus_spirit_hell") || mobType.equals("krampus_spirit_blood");
    }

    /**
     * Award progress based on difficulty and configured drop chances.
     */
    private void awardBossProgress(Player player, String difficulty) {
        String configPath = "winter_event.drop_chances.bosses." + difficulty;

        // Get drop chances for this difficulty
        Map<String, Object> chances = config.getSection(configPath).getValues(false);

        // Roll for progress amount
        double roll = random.nextDouble() * 100.0;
        double cumulative = 0.0;

        for (Map.Entry<String, Object> entry : chances.entrySet()) {
            int progressAmount = Integer.parseInt(entry.getKey());
            double chance = ((Number) entry.getValue()).doubleValue();

            cumulative += chance;
            if (roll <= cumulative) {
                // Award this amount
                winterEventManager.handleMobKill(player, "winter_summit_boss", progressAmount, 1.0);
                return;
            }
        }

        // Fallback - shouldn't happen if percentages sum to 100
        winterEventManager.handleMobKill(player, "winter_summit_boss", 300, 1.0);
    }
}
