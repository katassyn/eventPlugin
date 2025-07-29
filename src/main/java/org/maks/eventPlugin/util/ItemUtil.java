package org.maks.eventPlugin.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ItemUtil {
    public static ItemStack createAttrieItem(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.POTATO);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§3Event Attrie");
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "attrie-item"), PersistentDataType.INTEGER, 1);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public static String serialize(ItemStack item) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOut = new BukkitObjectOutputStream(out)) {
            dataOut.writeObject(item);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    public static ItemStack deserialize(String data) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataIn = new BukkitObjectInputStream(in)) {
            return (ItemStack) dataIn.readObject();
        } catch (ClassNotFoundException | IOException e) {
            return null;
        }
    }
}
