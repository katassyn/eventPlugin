package org.maks.eventPlugin.newmoon.listener;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.maks.eventPlugin.newmoon.NewMoonManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Lord immunity system for New Moon event.
 *
 * Lords (Silvanus and Malachai) are immune to player damage unless the player
 * has the Cauldron buff (activated by clicking cauldron with 50 Fairy Wood).
 *
 * Similar to Q3 Stage1 immunity system (Evil Miller protection).
 */
public class LordImmunityListener implements Listener {

    private final NewMoonManager newMoonManager;

    // Debounce warning messages (only show every 5 seconds)
    private final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private static final long WARNING_COOLDOWN_MS = 5000;

    public LordImmunityListener(NewMoonManager newMoonManager) {
        this.newMoonManager = newMoonManager;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if damager is a player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        // Check if entity is a LivingEntity
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        Player player = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();

        // Get MythicMob type if this is a MythicMob
        ActiveMob activeMob = MythicBukkit.inst().getMobManager()
                .getActiveMob(target.getUniqueId())
                .orElse(null);

        if (activeMob == null) {
            return; // Not a MythicMob
        }

        String mobTypeName = activeMob.getType().getInternalName().toLowerCase();

        // Check if this is a Lord (Silvanus or Malachai)
        boolean isLordSilvanus = mobTypeName.equals("lord_silvanus_normal") ||
                                 mobTypeName.equals("lord_silvanus_hard");
        boolean isLordMalachai = mobTypeName.equals("lord_malachai_normal") ||
                                 mobTypeName.equals("lord_malachai_hard");

        if (!isLordSilvanus && !isLordMalachai) {
            return; // Not a Lord, allow damage
        }

        // Check if player has cauldron buff
        if (!newMoonManager.hasCauldronBuff(player.getUniqueId())) {
            // Cancel damage - Lord is immune
            event.setCancelled(true);

            // Show warning message (with cooldown to prevent spam)
            long now = System.currentTimeMillis();
            if (!lastWarningTime.containsKey(player.getUniqueId()) ||
                    now - lastWarningTime.getOrDefault(player.getUniqueId(), 0L) > WARNING_COOLDOWN_MS) {

                String lordName = isLordSilvanus ? "Lord Silvanus" : "Lord Malachai";
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "§l⚔ §c" + lordName + " is protected by ancient magic!");
                player.sendMessage(ChatColor.GRAY + "   You cannot damage them without the §6Lord's Weakness §7buff!");
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "§l» §eFind the §6Cauldron §ein this realm");
                player.sendMessage(ChatColor.YELLOW + "§l» §eUse it with §650 Fairy Wood §eto activate the buff");
                player.sendMessage(ChatColor.YELLOW + "§l» §eThe buff lasts §660 seconds");
                player.sendMessage("");

                lastWarningTime.put(player.getUniqueId(), now);
            }
        }
        // If player has buff, allow damage (don't cancel event)
    }

    /**
     * Clear warning cooldown for player (e.g., when they leave or event ends)
     */
    public void clearPlayerData(UUID playerId) {
        lastWarningTime.remove(playerId);
    }
}
