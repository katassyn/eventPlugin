package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.eventsystem.Reward;

import java.util.*;

public class AdminRewardEditorGUI implements Listener {
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static class Session {
        enum Stage { ADD_ITEMS, SET_PROGRESS }
        Stage stage;
        Inventory inventory;
        List<ItemStack> rewards = new ArrayList<>();
        List<Integer> progress = new ArrayList<>();
        EventManager eventManager;
    }

    public AdminRewardEditorGUI() {
    }

    public void open(Player player, EventManager manager) {
        Session session = new Session();
        session.eventManager = manager;
        session.stage = Session.Stage.ADD_ITEMS;
        Inventory inv = Bukkit.createInventory(player, 27, "Reward Items");
        ItemStack next = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = next.getItemMeta();
        meta.setDisplayName("Next");
        next.setItemMeta(meta);
        inv.setItem(26, next);
        session.inventory = inv;
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    private void openProgressStage(Player player, Session session) {
        session.stage = Session.Stage.SET_PROGRESS;
        int rows = ((session.rewards.size() - 1) / 9 + 1) + 1; // extra row for save
        Inventory inv = Bukkit.createInventory(player, rows * 9, "Reward Progress");
        session.progress = new ArrayList<>(Collections.nCopies(session.rewards.size(), 0));
        for (int i = 0; i < session.rewards.size(); i++) {
            ItemStack item = session.rewards.get(i).clone();
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of("Required: 0", "Left/Right click to edit"));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        ItemStack save = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = save.getItemMeta();
        meta.setDisplayName("Save");
        save.setItemMeta(meta);
        inv.setItem(inv.getSize() - 1, save);
        session.inventory = inv;
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        int slot = event.getRawSlot();

        if (session.stage == Session.Stage.ADD_ITEMS) {
            if (event.getView().getTopInventory() == session.inventory) {
                // interacting with our GUI
                event.setCancelled(true);
                if (slot == 26) {
                    for (int i = 0; i < 26; i++) {
                        ItemStack it = session.inventory.getItem(i);
                        if (it != null && it.getType() != Material.AIR) {
                            session.rewards.add(it.clone());
                        }
                    }
                    openProgressStage(player, session);
                } else if (slot < 26) {
                    // allow placing/removing reward items
                    event.setCancelled(false);
                }
            } else {
                // player inventory interaction
                event.setCancelled(false);
            }
        } else if (session.stage == Session.Stage.SET_PROGRESS) {
            // cancel all clicks while editing progress
            event.setCancelled(true);

            if (event.getView().getTopInventory() != session.inventory) {
                return; // clicked outside our GUI
            }

            if (slot == session.inventory.getSize() - 1) {
                // save rewards
                List<Reward> rewards = new ArrayList<>();
                for (int i = 0; i < session.rewards.size(); i++) {
                    rewards.add(new Reward(session.progress.get(i), session.rewards.get(i)));
                }
                session.eventManager.setRewards(rewards);
                player.sendMessage("Rewards saved.");
                player.closeInventory();
                sessions.remove(player.getUniqueId());
            } else if (slot < session.rewards.size()) {
                int prog = session.progress.get(slot);
                switch (event.getClick()) {
                    case LEFT -> prog += 100;
                    case RIGHT -> prog = Math.max(0, prog - 100);
                    default -> {
                        return; // ignore other click types
                    }
                }
                session.progress.set(slot, prog);
                ItemStack item = session.inventory.getItem(slot);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    meta.setLore(List.of("Required: " + prog, "Left/Right click to edit"));
                    item.setItemMeta(meta);
                    session.inventory.setItem(slot, item);
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        if (event.getView().getTopInventory() == session.inventory) {
            // Prevent dragging items in or out of the GUI
            event.setCancelled(true);
        } else {
            // Also cancel if any dragged slot belongs to our GUI
            for (int raw : event.getRawSlots()) {
                if (raw < session.inventory.getSize()) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
