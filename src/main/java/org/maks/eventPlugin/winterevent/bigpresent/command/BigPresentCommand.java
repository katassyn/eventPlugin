package org.maks.eventPlugin.winterevent.bigpresent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.eventPlugin.winterevent.bigpresent.AdminBigPresentRewardEditorGUI;
import org.maks.eventPlugin.winterevent.bigpresent.BigPresentManager;
import org.maks.eventPlugin.winterevent.bigpresent.BigPresentTier;

import java.util.UUID;

public class BigPresentCommand implements CommandExecutor {

    private final BigPresentManager manager;
    private final AdminBigPresentRewardEditorGUI adminGUI;

    public BigPresentCommand(BigPresentManager manager, AdminBigPresentRewardEditorGUI adminGUI) {
        this.manager = manager;
        this.adminGUI = adminGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        if (label.equalsIgnoreCase("big_present_rewards")) {
            // Admin GUI
            if (!player.hasPermission("eventplugin.admin.big_present_rewards")) {
                player.sendMessage("§c§lYou don't have permission to use this!");
                return true;
            }
            adminGUI.open(player);
            return true;
        }

        // Player info: /big_present
        if (!player.hasPermission("eventplugin.big_present")) {
            player.sendMessage("§c§lYou don't have permission to use this!");
            return true;
        }

        player.sendMessage("§b§lWinter Event §7» §fA great Christmas Present awaits the greatest savior who defeated the evil holiday spirit and recovered the stolen sweets.");
        player.sendMessage("§7To open a present, bring §bSnow Flakes§7 (from Ingredient Pouch) and right-click the present blocks.");
        player.sendMessage("§7Each tier can be opened §conly once§7 per event edition.");

        UUID pid = player.getUniqueId();
        for (BigPresentTier tier : BigPresentTier.values()) {
            int cost = manager.getRequiredFlakes(tier);
            boolean opened = manager.isOpened(pid, tier);
            String status = opened ? "§aOPENED" : "§eNOT OPENED";
            player.sendMessage(" §8- §f" + tier.getDisplayName() + " §7(§b" + cost + " Snow Flakes§7): " + status);
        }

        return true;
    }
}
