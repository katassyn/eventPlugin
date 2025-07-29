package org.maks.eventPlugin.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.eventPlugin.eventsystem.BuffManager;

public class AttrieItemListener implements Listener {
    private final BuffManager buffManager;
    private final NamespacedKey key;

    public AttrieItemListener(JavaPlugin plugin, BuffManager buffManager) {
        this.buffManager = buffManager;
        this.key = new NamespacedKey(plugin, "attrie-item");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getItemMeta() != null && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            buffManager.applyBuff(player, 30);
            player.sendMessage("§aEvent Attrie activated for 30 days!");
        }
    }
}
