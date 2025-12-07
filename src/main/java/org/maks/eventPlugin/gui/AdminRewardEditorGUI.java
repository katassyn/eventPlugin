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
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
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
        int currentPage = 0; // For pagination in progress stage
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
            for (int i = 0; i < existingRewards.size() && i < 25; i++) {
                Reward reward = existingRewards.get(i);
                session.rewards.add(reward.item().clone());
                session.progress.add(reward.requiredProgress());

                // Display the reward in the GUI
                inv.setItem(i, reward.item().clone());
            }
            player.sendMessage(ChatColor.GREEN + "Loaded " + existingRewards.size() + " existing rewards.");
        }
        
        // Organize button (slot 25)
        ItemStack organize = new ItemStack(Material.HOPPER);
        ItemMeta organizeMeta = organize.getItemMeta();
        organizeMeta.setDisplayName("§e§lOrganize Items");
        organizeMeta.setLore(List.of("", "§7Click to remove gaps", "§7and compact items"));
        organize.setItemMeta(organizeMeta);
        inv.setItem(25, organize);

        // Next button (slot 26)
        ItemStack next = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = next.getItemMeta();
        meta.setDisplayName("§a§lNext");
        next.setItemMeta(meta);
        inv.setItem(26, next);
        session.inventory = inv;
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    /**
     * Organize items in the reward GUI - remove gaps and compact items to the left.
     */
    private void organizeItems(Inventory inv, Player player) {
        // Collect all non-null items from slots 0-24
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        // Clear all reward slots
        for (int i = 0; i < 25; i++) {
            inv.setItem(i, null);
        }

        // Place items compactly starting from slot 0
        for (int i = 0; i < items.size() && i < 25; i++) {
            inv.setItem(i, items.get(i));
        }

        player.sendMessage(ChatColor.GREEN + "Items organized! (" + items.size() + " items)");
    }

    private void openProgressStage(Player player, Session session) {
        session.stage = Session.Stage.SET_PROGRESS;

        // Use custom InventoryHolder instead of player
        RewardProgressHolder holder = new RewardProgressHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, "Reward Progress - Page " + (session.currentPage + 1));

        // Make sure progress list has the same size as rewards list
        while (session.progress.size() < session.rewards.size()) {
            session.progress.add(0); // Add default progress for any missing entries
        }

        // Calculate pagination
        int itemsPerPage = 45; // 5 rows for items
        int totalPages = (int) Math.ceil(session.rewards.size() / (double) itemsPerPage);
        int startIndex = session.currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, session.rewards.size());

        // Display items for current page
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack item = session.rewards.get(i).clone();
            ItemMeta meta = item.getItemMeta();
            int progressValue = session.progress.get(i);
            meta.setLore(List.of(
                "§7Item #" + (i + 1),
                "§eRequired: " + progressValue,
                "§7Left/Right click to edit ±100",
                "§7Shift-Right click to type exact value"
            ));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }

        // Bottom row - Navigation and Save buttons
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, bg);
        }

        // Previous page button
        if (session.currentPage > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName("§e§l← Previous Page");
            prevMeta.setLore(List.of("§7Page " + session.currentPage + "/" + totalPages));
            prev.setItemMeta(prevMeta);
            inv.setItem(45, prev);
        }

        // Info/Page indicator
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lPage " + (session.currentPage + 1) + "/" + totalPages);
        infoMeta.setLore(List.of(
            "",
            "§7Total rewards: §e" + session.rewards.size(),
            "§7Showing: §e" + (startIndex + 1) + "-" + endIndex
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        // Next page button
        if (session.currentPage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName("§e§lNext Page →");
            nextMeta.setLore(List.of("§7Page " + (session.currentPage + 2) + "/" + totalPages));
            next.setItemMeta(nextMeta);
            inv.setItem(53, next);
        }

        // Save button
        ItemStack save = new ItemStack(Material.GREEN_WOOL);
        ItemMeta saveMeta = save.getItemMeta();
        saveMeta.setDisplayName("§a§lSave All Rewards");
        saveMeta.setLore(List.of("", "§7Click to save all " + session.rewards.size() + " rewards"));
        save.setItemMeta(saveMeta);
        inv.setItem(50, save);

        // Auto distribute button
        ItemStack auto = new ItemStack(Material.NETHER_STAR);
        ItemMeta autoMeta = auto.getItemMeta();
        autoMeta.setDisplayName("§d§lAuto Distribute");
        autoMeta.setLore(List.of(
            "",
            "§7Automatically distribute rewards",
            "§7based on event max progress",
            "§7with random spacing",
            "",
            "§eMax Progress: §6" + session.eventManager.getMaxProgress(),
            "§eRewards: §6" + session.rewards.size(),
            "",
            "§aClick to auto-distribute!"
        ));
        auto.setItemMeta(autoMeta);
        inv.setItem(48, auto);

        session.inventory = inv;
        player.openInventory(inv);
    }

    /**
     * Automatically distribute progress values across all rewards.
     * Uses random spacing but ensures values are sorted and span the full max progress.
     */
    private void autoDistributeProgress(Session session) {
        int N = session.rewards.size();
        if (N == 0) return;

        int M = session.eventManager.getMaxProgress();
        Random random = new Random();

        // Minimalna odległość między nagrodami (5% od średniej)
        int minGap = Math.max(1, (int)(0.05 * M / N));

        List<Integer> values = new ArrayList<>();
        values.add(0); // start point

        for (int i = 0; i < N; i++) {
            int prevValue = values.get(values.size() - 1);
            int remaining = M - prevValue;
            int itemsLeft = N - i;

            // Średnia wartość do rozłożenia na pozostałe itemy
            double avgStep = (double) remaining / itemsLeft;

            // Losowy step z zakresu [avgStep * 0.6, avgStep * 1.4]
            int minStep = Math.max(minGap, (int)(avgStep * 0.6));
            int maxStep = (int)(avgStep * 1.4);

            // Upewnij się że nie przekroczymy M
            maxStep = Math.min(maxStep, remaining - (itemsLeft - 1) * minGap);

            int step = minStep + random.nextInt(Math.max(1, maxStep - minStep + 1));
            int value = prevValue + step;

            // Dla ostatniego itemu, ustaw dokładnie na M
            if (i == N - 1) {
                value = M;
            }

            values.add(value);
        }

        // Przypisz wartości (pomijamy pierwszy element który jest 0)
        for (int i = 0; i < N; i++) {
            session.progress.set(i, values.get(i + 1));
        }
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

                if (slot == 25) {
                    // Organize button clicked - compact items removing gaps
                    organizeItems(topInventory, player);
                    return;
                }

                if (slot == 26) {
                    // Rebuild rewards list from GUI slots 0..24 while preserving per-occurrence progress
                    List<Integer> oldProgress = new ArrayList<>(session.progress);
                    List<ItemStack> oldRewards = new ArrayList<>(session.rewards);
                    session.rewards.clear();

                    // Collect current items
                    for (int i = 0; i < 25; i++) {
                        ItemStack it = session.inventory.getItem(i);
                        if (it != null && it.getType() != Material.AIR) {
                            session.rewards.add(it.clone());
                        }
                    }

                    // Map duplicates by pairing each new item to the next unused matching old item
                    session.progress.clear();
                    boolean[] used = new boolean[oldRewards.size()];
                    for (ItemStack newItem : session.rewards) {
                        int matchedIndex = -1;
                        for (int j = 0; j < oldRewards.size(); j++) {
                            if (!used[j] && oldRewards.get(j).isSimilar(newItem)) {
                                used[j] = true;
                                matchedIndex = j;
                                break;
                            }
                        }
                        if (matchedIndex >= 0 && matchedIndex < oldProgress.size()) {
                            session.progress.add(oldProgress.get(matchedIndex));
                        } else {
                            session.progress.add(0); // default for brand new item or moved without prior match
                        }
                    }

                    openProgressStage(player, session);
                } else if (slot < 25) {
                    // Handle item interactions in reward slots (0-24)
                    ClickType click = event.getClick();

                    // Right-click to remove items
                    if (click == ClickType.RIGHT) {
                        ItemStack item = topInventory.getItem(slot);
                        if (item != null && item.getType() != Material.AIR) {
                            // Remove the item
                            topInventory.setItem(slot, null);
                            player.sendMessage(ChatColor.YELLOW + "Removed reward item.");
                        }
                        event.setCancelled(true);
                        return;
                    }

                    // Block shift+click from taking items OUT of GUI
                    if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                        // If clicking on GUI slot (trying to move out) - block it
                        if (clicked.equals(topInventory)) {
                            event.setCancelled(true);
                            return;
                        }

                        // Allow shift-click from player inventory to GUI
                        event.setCancelled(false);
                        return;
                    }

                    // Allow other click types for placing/swapping items
                    event.setCancelled(false);
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

            // Navigation buttons
            if (rawSlot == 45) {
                // Previous page
                if (session.currentPage > 0) {
                    session.currentPage--;
                    openProgressStage(player, session);
                }
                return;
            }

            if (rawSlot == 53) {
                // Next page
                int itemsPerPage = 45;
                int totalPages = (int) Math.ceil(session.rewards.size() / (double) itemsPerPage);
                if (session.currentPage < totalPages - 1) {
                    session.currentPage++;
                    openProgressStage(player, session);
                }
                return;
            }

            // Auto distribute button
            if (rawSlot == 48) {
                autoDistributeProgress(session);
                player.sendMessage("§a§lAuto-distributed rewards based on max progress!");
                // Refresh the GUI to show new values
                openProgressStage(player, session);
                return;
            }

            // Save button
            if (rawSlot == 50) {
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

            // Item slots (0-44)
            if (rawSlot >= 0 && rawSlot < 45) {
                // Calculate actual reward index based on current page
                int itemsPerPage = 45;
                int actualIndex = (session.currentPage * itemsPerPage) + rawSlot;

                // Check if this index exists in rewards list
                if (actualIndex >= session.rewards.size()) return;

                ItemStack item = topInventory.getItem(rawSlot);
                if (item == null || item.getType() == Material.AIR) return;

                // 1) SHIFT-RIGHT → aktywuj wpisywanie liczby
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    session.pendingInputSlot = actualIndex;
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "Wpisz w czacie wymaganą wartość dla nagrody #"
                                     + (actualIndex + 1) + ":");
                    return;
                }

                // 2) zwykłe LEFT/RIGHT – inkrementacja po 100
                int prog = session.progress.get(actualIndex);
                if (event.getClick() == ClickType.LEFT) {
                    prog += 100;
                } else if (event.getClick() == ClickType.RIGHT) {
                    prog = Math.max(0, prog - 100);
                }

                session.progress.set(actualIndex, prog);

                ItemMeta meta = item.getItemMeta();
                meta.setLore(List.of(
                    "§7Item #" + (actualIndex + 1),
                    "§eRequired: " + prog,
                    "§7Left/Right click to edit ±100",
                    "§7Shift-Right click to type exact value"
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

            // Cancel drag if moving items outside allowed reward slots (0-24)
            for (int raw : event.getRawSlots()) {
                if (raw < topInventory.getSize() && raw >= 25) {
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());

        if (session != null && session.stage == Session.Stage.SET_PROGRESS && session.pendingInputSlot != null) {
            event.setCancelled(true);
            event.getRecipients().clear();
            processChat(event.getPlayer(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAsyncChat(AsyncChatEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());

        if (session != null && session.stage == Session.Stage.SET_PROGRESS && session.pendingInputSlot != null) {
            event.setCancelled(true);
            var msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
            processChat(event.getPlayer(), msg);
        }
    }

    private boolean processChat(Player player, String message) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null
            || session.stage != Session.Stage.SET_PROGRESS
            || session.pendingInputSlot == null) {
            return false;
        }

        String msg = message.trim();
        int value;
        try {
            value = Integer.parseInt(msg);
            if (value < 0) {
                player.sendMessage(ChatColor.RED + "Wartość nie może być ujemna. Spróbuj ponownie:");
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "To nie jest prawidłowa liczba. Spróbuj ponownie:");
            return true;
        }

        final int actualIndex = session.pendingInputSlot;
        session.progress.set(actualIndex, value);
        session.pendingInputSlot = null;
        player.sendMessage(ChatColor.GREEN
            + "Ustawiono postęp dla nagrody #" + (actualIndex + 1) + " na " + value + ".");

        // Re-open the progress GUI on the same page
        Bukkit.getScheduler().runTask(plugin, () -> {
            openProgressStage(player, session);
        });
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Check if the closed inventory has one of our custom holders
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        // Only remove the session if it's one of our GUIs
        if (holder instanceof RewardItemsHolder || holder instanceof RewardProgressHolder) {
            UUID playerId = event.getPlayer().getUniqueId();
            Session session = sessions.get(playerId);

            // Don't remove session if:
            // 1. Waiting for chat input
            // 2. OR if the closed inventory is NOT the current session inventory (stage transition)
            if (session != null) {
                if (session.pendingInputSlot != null) {
                    return;
                }

                // Check if this is NOT the current session inventory (means we're transitioning stages)
                if (session.inventory != inventory) {
                    return;
                }
            }

            sessions.remove(playerId);
        }
    }
}