package org.maks.eventPlugin.winterevent.bigpresent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.maks.eventPlugin.fullmoon.integration.PouchHelper;

import java.util.*;

public class BigPresentInteractionListener implements Listener {

    private final BigPresentManager manager;

    // Cooldown to avoid chat double send (and off-hand duplication)
    private final Map<UUID, Long> lastNotify = new HashMap<>();

    // Cuboids definitions (inclusive)
    private final Cuboid infernal;
    private final Cuboid hell;
    private final Cuboid blood;

    public BigPresentInteractionListener(BigPresentManager manager) {
        this.manager = manager;
        // World is not specified: we'll match by coordinates only in player's current world
        // Define cuboids according to provided coords
        this.infernal = new Cuboid(2460, -60, 21, 2462, -58, 23);
        this.hell = new Cuboid(2460, -60, 371, 2462, -58, 373);
        this.blood = new Cuboid(2460, -60, 721, 2462, -58, 723);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() == EquipmentSlot.OFF_HAND) return; // prevent double firing
        if (e.getClickedBlock() == null) return;

        Player player = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();

        BigPresentTier tier = getTierByLocation(loc);
        if (tier == null) return; // Not in our cuboids

        e.setCancelled(true); // Prevent other interactions with these blocks

        UUID pid = player.getUniqueId();
        if (manager.isOpened(pid, tier)) {
            player.sendMessage("§c§l[Big Present] You have already opened the " + tier.getDisplayName() + " present for this event.");
            return;
        }

        List<ItemStack> rewards = manager.loadRewards(tier);
        if (rewards.isEmpty()) {
            player.sendMessage("§e§l[Big Present] This present has no rewards configured yet. Please contact an administrator.");
            return;
        }

        int cost = manager.getRequiredFlakes(tier);
        if (!PouchHelper.hasEnough(player, BigPresentManager.POUCH_ITEM_ID, cost)) {
            long now = System.currentTimeMillis();
            long last = lastNotify.getOrDefault(pid, 0L);
            if (now - last > 500L) { // debounce to avoid double chat on click/un-click
                player.sendMessage(manager.buildInfoMessage(tier));
                lastNotify.put(pid, now);
            }
            return;
        }

        int need = manager.additionalSlotsNeeded(player, rewards);
        if (need > 0) {
            player.sendMessage("§c§l[Big Present] Not enough inventory space. You need " + need + " more free slot" + (need == 1 ? "" : "s") + ".");
            return;
        }

        // Consume and grant
        boolean consumed = PouchHelper.consumeItem(player, BigPresentManager.POUCH_ITEM_ID, cost);
        if (!consumed) {
            player.sendMessage("§c§l[Big Present] Not enough Snow Flakes.");
            return;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rewards.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            // Shouldn't happen due to prior check; as a fallback, drop at feet
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }

        manager.markOpened(pid, tier);

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendMessage("§a§l[Big Present] You have opened the " + tier.getDisplayName() + " present! Enjoy your rewards.");
        // Global broadcast
        Bukkit.getServer().broadcastMessage("§6§l[Big Present] §ePlayer §f" + player.getName() + " §ehas opened the BIG PRESENT (§6" + tier.getDisplayName() + "§e)!");
    }

    private BigPresentTier getTierByLocation(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        if (infernal.contains(x, y, z)) return BigPresentTier.INFERNAL;
        if (hell.contains(x, y, z)) return BigPresentTier.HELL;
        if (blood.contains(x, y, z)) return BigPresentTier.BLOOD;
        return null;
    }

    private static class Cuboid {
        final int x1, y1, z1, x2, y2, z2;
        Cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
            this.z2 = Math.max(z1, z2);
        }
        boolean contains(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }
}