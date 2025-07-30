package org.maks.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.EventPlugin;
import org.maks.eventPlugin.eventsystem.EventManager;
import org.maks.eventPlugin.eventsystem.Reward;

import java.util.*;

public class AdminRewardEditorGUI implements Listener {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final JavaPlugin plugin;

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
        enum Stage { NONE, ADD_ITEMS, SET_PROGRESS }
        Stage stage = Stage.NONE;
        Inventory inventory;
        List<ItemStack> rewards = new ArrayList<>();
        List<Integer> progress = new ArrayList<>();
        EventManager eventManager;
        Integer pendingInputSlot; // Used for chat input
    }

    public AdminRewardEditorGUI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, EventManager manager) {
        Session session = new Session();
        session.eventManager = manager;
        session.stage = Session.Stage.ADD_ITEMS;
        
        // Use custom InventoryHolder instead of player
        RewardItemsHolder holder = new RewardItemsHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 27, "Reward Items");
        
        // Load existing rewards from the database
        List<Reward> existingRewards = manager.getRewards();
        if (!existingRewards.isEmpty()) {
            // Store rewards and their progress values in the session
            for (int i = 0; i < existingRewards.size() && i < 26; i++) {
                Reward reward = existingRewards.get(i);
                session.rewards.add(reward.item().clone());
                session.progress.add(reward.requiredProgress());
                
                // Display the reward in the GUI
                inv.setItem(i, reward.item().clone());
            }
            player.sendMessage(ChatColor.GREEN + "Loaded " + existingRewards.size() + " existing rewards.");
        }
        
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
        
        // Make sure progress list has the same size as rewards list
        while (session.progress.size() < session.rewards.size()) {
            session.progress.add(0); // Add default progress for any missing entries
        }
        
        for (int i = 0; i < session.rewards.size(); i++) {
            ItemStack item = session.rewards.get(i).clone();
            ItemMeta meta = item.getItemMeta();
            int progressValue = session.progress.get(i);
            meta.setLore(List.of(
                "Required: " + progressValue,
                "Left/Right click to edit ±100",
                "Shift-Right click to type exact value"
            ));
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
                    // Clear existing rewards list but keep the progress values
                    List<Integer> existingProgress = new ArrayList<>(session.progress);
                    List<ItemStack> existingRewards = new ArrayList<>(session.rewards);
                    session.rewards.clear();
                    
                    // Add all items from the inventory to the rewards list
                    Map<ItemStack, Integer> slotMap = new HashMap<>();
                    for (int i = 0; i < 26; i++) {
                        ItemStack it = session.inventory.getItem(i);
                        if (it != null && it.getType() != Material.AIR) {
                            session.rewards.add(it.clone());
                            
                            // Check if this is an existing reward to preserve its progress
                            for (int j = 0; j < existingRewards.size(); j++) {
                                if (existingRewards.get(j).isSimilar(it)) {
                                    slotMap.put(it, j);
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Rebuild the progress list to match the new rewards list
                    session.progress.clear();
                    for (ItemStack reward : session.rewards) {
                        Integer existingIndex = slotMap.get(reward);
                        if (existingIndex != null && existingIndex < existingProgress.size()) {
                            session.progress.add(existingProgress.get(existingIndex));
                        } else {
                            session.progress.add(0); // Default progress for new rewards
                        }
                    }
                    
                    openProgressStage(player, session);
                } else if (slot < 26) {
                    // Handle right-click to remove items
                    if (event.getClick() == ClickType.RIGHT) {
                        ItemStack item = topInventory.getItem(slot);
                        if (item != null && item.getType() != Material.AIR) {
                            // Remove the item
                            topInventory.setItem(slot, null);
                            player.sendMessage(ChatColor.YELLOW + "Removed reward item.");
                            event.setCancelled(true);
                        }
                    } else {
                        // Allow placing/removing reward items with other click types
                        event.setCancelled(false);
                    }
                }
            } else if (slot >= session.inventory.getSize()) {
                // allow taking from player inventory
                event.setCancelled(false);
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
                
                // 1) SHIFT-RIGHT → aktywuj wpisywanie liczby
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    session.pendingInputSlot = rawSlot;
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "Wpisz w czacie wymaganą wartość dla slotu #" 
                                     + rawSlot + ":");
                    return;
                }
                
                // 2) zwykłe LEFT/RIGHT – inkrementacja po 100
                int prog = session.progress.get(rawSlot);
                if (event.getClick() == ClickType.LEFT) {
                    prog += 100;
                } else if (event.getClick() == ClickType.RIGHT) {
                    prog = Math.max(0, prog - 100);
                }
                
                session.progress.set(rawSlot, prog);
                
                ItemMeta meta = item.getItemMeta();
                meta.setLore(List.of(
                    "Required: " + prog,
                    "Left/Right click to edit ±100",
                    "Shift-Right click to type exact value"
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
            
            // Cancel drag if moving items outside allowed reward slots
            for (int raw : event.getRawSlots()) {
                if (raw < topInventory.getSize() && raw >= 26) {
                    event.setCancelled(true);
                    return;
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
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null
            || session.stage != Session.Stage.SET_PROGRESS
            || session.pendingInputSlot == null) {
            return;
        }

        event.setCancelled(true);  // nie wyświetlamy w czacie
        event.getRecipients().clear();

        String msg = event.getMessage().trim();
        int value;
        try {
            value = Integer.parseInt(msg);
            if (value < 0) {
                player.sendMessage(ChatColor.RED + "Wartość nie może być ujemna. Spróbuj ponownie:");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "To nie jest prawidłowa liczba. Spróbuj ponownie:");
            return;
        }

        final int slot = session.pendingInputSlot;
        session.progress.set(slot, value);
        session.pendingInputSlot = null;
        player.sendMessage(ChatColor.GREEN
            + "Ustawiono postęp w slocie #" + slot + " na " + value + ".");

        // ponowne otwarcie GUI na głównym wątku
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(AdminRewardEditorGUI.class), () -> {
            // odśwież lore w itemach
            ItemStack item = session.inventory.getItem(slot);
            if (item != null) {
                ItemMeta meta = item.getItemMeta();
                meta.setLore(List.of(
                    "Required: " + value,
                    "Left/Right click to edit ±100",
                    "Shift-Right click to type exact value"
                ));
                item.setItemMeta(meta);
                session.inventory.setItem(slot, item);
            }
            // otwórz GUI
            player.openInventory(session.inventory);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Check if the closed inventory has one of our custom holders
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        
        // Only remove the session if it's one of our GUIs and not waiting for chat input
        if (holder instanceof RewardItemsHolder || holder instanceof RewardProgressHolder) {
            UUID playerId = event.getPlayer().getUniqueId();
            Session session = sessions.get(playerId);
            
            // Don't remove session if waiting for chat input
            if (session != null && session.pendingInputSlot != null) {
                return;
            }
            
            sessions.remove(playerId);
        }
    }
}