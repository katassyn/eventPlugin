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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.Action;

public class AttrieItemListener implements Listener {
    private final BuffManager buffManager;
    private final NamespacedKey key;

    public AttrieItemListener(JavaPlugin plugin, BuffManager buffManager) {
        this.buffManager = buffManager;
        this.key = new NamespacedKey(plugin, "attrie-item");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) {
            event.setCancelled(true);

            ItemStack updated = item.clone();
            updated.setAmount(item.getAmount() - 1);

            EquipmentSlot hand = event.getHand();
            if (hand == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(updated.getAmount() > 0 ? updated : null);
            } else if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(updated.getAmount() > 0 ? updated : null);
            }

            buffManager.applyBuff(player, 30);
            player.sendMessage("§aEvent Attrie activated for 30 days!");
        }
    }
}
