package com.ravenhurst.minecraft.paper.portalteleport;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PortalTeleportCommand implements CommandExecutor {
    private final PortalTeleportPlugin plugin;
    private final TeleportBlockManager manager;

    public PortalTeleportCommand(PortalTeleportPlugin plugin, TeleportBlockManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "PortalTeleport Commands:");
            sender.sendMessage(ChatColor.GRAY + "/portalteleport cleanup - Remove invalid teleport blocks");
            sender.sendMessage(ChatColor.GRAY + "/portalteleport cleanholograms - Remove orphaned hologram armor stands");
            sender.sendMessage(ChatColor.GRAY + "/portalteleport reload - Reload configuration");
            return true;
        }

        if (args[0].equalsIgnoreCase("cleanup")) {
            if (!sender.hasPermission("portalteleport.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "Validating and cleaning up teleport blocks...");
            int removed = manager.validateAndCleanup();

            if (removed == 0) {
                sender.sendMessage(ChatColor.GREEN + "All teleport blocks are valid!");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " invalid teleport block(s).");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("cleanholograms")) {
            if (!sender.hasPermission("portalteleport.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "Cleaning up orphaned hologram armor stands...");
            int removed = plugin.getVisuals().cleanupOrphanedHolograms();

            if (removed == 0) {
                sender.sendMessage(ChatColor.GREEN + "No orphaned holograms found!");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " orphaned hologram armor stand(s).");
            }

            // Recreate holograms for existing blocks
            for (TeleportBlock block : manager.getAllBlocks()) {
                boolean isFirst = manager.isFirstBlock(block);
                plugin.getVisuals().createHologram(block, isFirst);
            }
            sender.sendMessage(ChatColor.GREEN + "Recreated holograms for " + manager.getAllBlocks().size() + " teleport block(s).");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("portalteleport.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            plugin.reloadConfig();

            // Refresh all holograms to apply new config settings
            for (TeleportBlock block : manager.getAllBlocks()) {
                boolean isFirst = manager.isFirstBlock(block);
                plugin.getVisuals().updateHologram(block, isFirst);
            }

            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded! Holograms updated.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /portalteleport for help.");
        return true;
    }
}