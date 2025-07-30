package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.eventsystem.Reward;

import java.util.*;

public class AdminRewardEditorGUI implements Listener {
    private final Map<UUID, Session> sessions = new HashMap<>();

    // Custom InventoryHolder for Reward Items GUI
    public class RewardItemsHolder implements InventoryHolder {
        private final Session session;

        public RewardItemsHolder(Session session) {
            this.session = session;
        }

        public Session getSession() {
            return session;
        }

        @Override
        public Inventory getInventory() {
            return session.inventory;
        }
    }

    // Custom InventoryHolder for Reward Progress GUI
    public class RewardProgressHolder implements InventoryHolder {
        private final Session session;

        public RewardProgressHolder(Session session) {
            this.session = session;
        }

        public Session getSession() {
            return session;
        }

        @Override
        public Inventory getInventory() {
            return session.inventory;
        }
    }

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
        
        // Use custom InventoryHolder instead of player
        RewardItemsHolder holder = new RewardItemsHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 27, "Reward Items");
        
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
        
        // Use custom InventoryHolder instead of player
        RewardProgressHolder holder = new RewardProgressHolder(session);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, "Reward Progress");
        
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
        
        // Check if the top inventory has our custom holder
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        
        // Only process if we're in the ADD_ITEMS stage with RewardItemsHolder
        if (holder instanceof RewardItemsHolder) {
            RewardItemsHolder customHolder = (RewardItemsHolder) holder;
            Session session = customHolder.getSession();
            
            if (session.stage != Session.Stage.ADD_ITEMS) return;
            
            int slot = event.getRawSlot();
            Inventory clicked = event.getClickedInventory();
            
            // Check if player clicked in our GUI inventory
            if (clicked != null && clicked.equals(topInventory)) {
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
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        
        // Check if the top inventory has our custom holder
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        
        // Only process if we have a RewardProgressHolder
        if (holder instanceof RewardProgressHolder) {
            RewardProgressHolder customHolder = (RewardProgressHolder) holder;
            Session session = customHolder.getSession();
            
            if (session.stage != Session.Stage.SET_PROGRESS) return;
            
            // blokujemy wszystko
            event.setCancelled(true);
            
            int rawSlot = event.getRawSlot();
            int topSize = topInventory.getSize();
            
            //  – przycisk zapisu jest zawsze ostatnim slotem
            if (rawSlot == topSize - 1) {
                // save rewards
                List<Reward> rewards = new ArrayList<>();
                for (int i = 0; i < session.rewards.size(); i++) {
                    rewards.add(new Reward(session.progress.get(i), session.rewards.get(i)));
                }
                session.eventManager.setRewards(rewards);
                player.sendMessage("Rewards saved.");
                player.closeInventory();
                sessions.remove(player.getUniqueId());
                return;
            }
            
            // – edycja progresu dla każdego reward slotu 0..(n-1)
            if (rawSlot >= 0 && rawSlot < session.rewards.size()) {
                ItemStack item = topInventory.getItem(rawSlot);
                if (item == null) return;
                
                int prog = session.progress.get(rawSlot);
                if (event.getClick() == ClickType.LEFT) {
                    prog += 100;
                } else if (event.getClick() == ClickType.RIGHT) {
                    prog = Math.max(0, prog - 100);
                } else {
                    return;
                }
                session.progress.set(rawSlot, prog);
                
                ItemMeta meta = item.getItemMeta();
                meta.setLore(List.of(
                    "Required: " + prog,
                    "Left/Right click to edit"
                ));
                item.setItemMeta(meta);
                // odświeżamy GUI
                topInventory.setItem(rawSlot, item);
            }
        }
    }


    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        // Check if the top inventory has our custom holder
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        
        // Only process if we have a RewardItemsHolder
        if (holder instanceof RewardItemsHolder) {
            RewardItemsHolder customHolder = (RewardItemsHolder) holder;
            Session session = customHolder.getSession();
            
            if (session.stage != Session.Stage.ADD_ITEMS) return;
            
            // Prevent dragging items in or out of the GUI
            event.setCancelled(true);
            
            // Check if any dragged slots are in our GUI
            for (int raw : event.getRawSlots()) {
                if (raw < topInventory.getSize()) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Check if the top inventory has our custom holder
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        
        // Only process if we have a RewardProgressHolder
        if (holder instanceof RewardProgressHolder) {
            RewardProgressHolder customHolder = (RewardProgressHolder) holder;
            Session session = customHolder.getSession();
            
            if (session.stage != Session.Stage.SET_PROGRESS) return;
            
            // jeśli jakikolwiek raw slot jest w topInventory, blokujemy cały drag
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Check if the closed inventory has one of our custom holders
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        
        // Only remove the session if it's one of our GUIs
        if (holder instanceof RewardItemsHolder || holder instanceof RewardProgressHolder) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }
}
