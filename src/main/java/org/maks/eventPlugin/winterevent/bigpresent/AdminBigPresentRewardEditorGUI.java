package org.maks.eventPlugin.winterevent.bigpresent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AdminBigPresentRewardEditorGUI implements Listener {

    private final BigPresentManager manager;
    private final JavaPlugin plugin;

    private final Map<UUID, Inventory> openGUIs = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static class Session {
        BigPresentTier selectedTier = null;
        boolean isEditing = false;
    }

    private static class TierSelectionHolder implements org.bukkit.inventory.InventoryHolder {
        private final Session session;
        TierSelectionHolder(Session session) { this.session = session; }
        public Session getSession() { return session; }
        @Override public Inventory getInventory() { return null; }
    }

    private static class RewardEditorHolder implements org.bukkit.inventory.InventoryHolder {
        private final Session session;
        RewardEditorHolder(Session session) { this.session = session; }
        public Session getSession() { return session; }
        @Override public Inventory getInventory() { return null; }
    }

    public AdminBigPresentRewardEditorGUI(BigPresentManager manager, JavaPlugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!player.hasPermission("eventplugin.admin.big_present_rewards")) {
            player.sendMessage("§c§lYou don't have permission to use this!");
            return;
        }

        Session session = sessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.isEditing = false;

        TierSelectionHolder holder = new TierSelectionHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 27, "§6§lBig Present - Select Tier");

        // background
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        inv.setItem(11, createTierItem(BigPresentTier.INFERNAL));
        inv.setItem(13, createTierItem(BigPresentTier.HELL));
        inv.setItem(15, createTierItem(BigPresentTier.BLOOD));

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private ItemStack createTierItem(BigPresentTier tier) {
        Material mat;
        switch (tier) {
            case INFERNAL: mat = Material.GREEN_CONCRETE; break;
            case HELL: mat = Material.ORANGE_CONCRETE; break;
            case BLOOD: mat = Material.RED_CONCRETE; break;
            default: mat = Material.WHITE_CONCRETE; break;
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§e§l" + tier.getDisplayName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Edit rewards for this tier");
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void openRewardEditor(Player player, BigPresentTier tier) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.selectedTier = tier;
        session.isEditing = true;

        RewardEditorHolder holder = new RewardEditorHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lEdit " + tier.getDisplayName() + " Rewards");

        // Fill whole GUI with background to prevent shift-clicks placing items outside the editor area
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        // Clear editable area (0-26)
        for (int i = 0; i <= 26; i++) inv.setItem(i, null);

        // Populate with current rewards (limited to 0-26)
        List<ItemStack> current = manager.loadRewards(tier);
        for (int i = 0; i < current.size() && i < 27; i++) {
            inv.setItem(i, current.get(i).clone());
        }

        // save button at 27
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveBtn.getItemMeta();
        saveMeta.setDisplayName("§a§l✔ SAVE REWARDS");
        saveBtn.setItemMeta(saveMeta);
        inv.setItem(27, saveBtn);

        // info at 49
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lHow to Use");
        infoMeta.setLore(Arrays.asList(
                "§71. Place reward items in slots 0-26",
                "§72. Click the SAVE button"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        // back at 53
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§l← BACK");
        back.setItemMeta(backMeta);
        inv.setItem(53, back);

        openGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof TierSelectionHolder) && !(inv.getHolder() instanceof RewardEditorHolder)) return;

        event.setCancelled(true);

        if (inv.getHolder() instanceof TierSelectionHolder) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            String name = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName() ? clicked.getItemMeta().getDisplayName() : "";
            if (name.contains("Infernal")) {
                openRewardEditor(player, BigPresentTier.INFERNAL);
            } else if (name.contains("Hell")) {
                openRewardEditor(player, BigPresentTier.HELL);
            } else if (name.contains("Blood")) {
                openRewardEditor(player, BigPresentTier.BLOOD);
            }
            return;
        }

        if (inv.getHolder() instanceof RewardEditorHolder) {
            int raw = event.getRawSlot();
            int topSize = inv.getSize(); // size of the editor GUI (54)

            // If the click is in the player's own inventory (bottom), allow it fully
            if (raw >= topSize) {
                event.setCancelled(false);
                return;
            }

            // Click is in the top editor inventory
            int slot = raw;
            // allow editing in 0-26 normally; cancel clicks on other slots unless they are buttons
            if (slot == 27) {
                // SAVE
                Session session = ((RewardEditorHolder) inv.getHolder()).getSession();
                saveRewardsFromInventory(player, session, inv);
                // back to tier select
                open(player);
                return;
            }
            if (slot == 53) {
                open(player);
                return;
            }
            if (slot >= 0 && slot <= 26) {
                event.setCancelled(false); // allow placing/removing items in editor area
                return;
            }
            // everything else in top stays cancelled (filler panes)
        }
    }

    private void saveRewardsFromInventory(Player player, Session session, Inventory inv) {
        if (session == null || session.selectedTier == null) return;
        List<ItemStack> rewards = new ArrayList<>();
        for (int i = 0; i <= 26; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                rewards.add(it.clone());
            }
        }
        manager.saveRewards(session.selectedTier, rewards);
        player.sendMessage("§a§l[Big Present] Rewards saved for " + session.selectedTier.getDisplayName());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof TierSelectionHolder) && !(inv.getHolder() instanceof RewardEditorHolder)) return;
        openGUIs.remove(player.getUniqueId());
    }

    public void cleanup() {
        openGUIs.clear();
        sessions.clear();
    }
}