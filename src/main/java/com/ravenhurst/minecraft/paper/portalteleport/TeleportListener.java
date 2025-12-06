package com.ravenhurst.minecraft.paper.portalteleport;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;

public class TeleportListener implements Listener {
    private final PortalTeleportPlugin plugin;
    private final TeleportBlockManager manager;

    public TeleportListener(PortalTeleportPlugin plugin, TeleportBlockManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();

        if (item.getType() != Material.REINFORCED_DEEPSLATE || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "teleport_pair_id");

        if (!container.has(key, PersistentDataType.STRING)) {
            return;
        }

        String pairIdString = container.get(key, PersistentDataType.STRING);
        if (pairIdString == null) {
            return;
        }

        UUID pairId = UUID.fromString(pairIdString);

        // Get the actual placed block and its location
        Block placedBlock = event.getBlock();
        Location location = placedBlock.getLocation();

        plugin.getLogger().info("Block placed: Type=" + placedBlock.getType() +
            " at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

        TeleportBlock teleportBlock = manager.createTeleportBlock(location, pairId);

        if (teleportBlock != null) {
            Player player = event.getPlayer();

            // Create hologram for this block
            boolean isFirst = manager.isFirstBlock(teleportBlock);
            plugin.getVisuals().createHologram(teleportBlock, isFirst);

            if (teleportBlock.isPaired()) {
                player.sendMessage(ChatColor.GREEN + "Teleport block placed and paired! Right-click to teleport.");

                Location paired = teleportBlock.getPairedLocation();
                player.playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.0f);

                if (paired != null && paired.getWorld() != null) {
                    paired.getWorld().playSound(paired, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.0f);
                    paired.getWorld().spawnParticle(Particle.END_ROD, paired.clone().add(0.5, 1.0, 0.5), 20, 0.3, 0.3, 0.3, 0.05);

                    // Update the paired block's hologram to show it's now paired
                    TeleportBlock pairedBlock = manager.getTeleportBlock(paired);
                    if (pairedBlock != null) {
                        plugin.getVisuals().updateHologram(pairedBlock, manager.isFirstBlock(pairedBlock));
                    }
                }

                location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0.5, 1.0, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
            } else {
                player.sendMessage(ChatColor.YELLOW + "Teleport block placed. Place its pair to activate.");
                player.playSound(location, Sound.BLOCK_COPPER_PLACE, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process right-clicks on actual blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.REINFORCED_DEEPSLATE) {
            return;
        }

        Player player = event.getPlayer();
        Location clickLoc = clickedBlock.getLocation();

        // Check if this is a teleport block
        TeleportBlock teleportBlock = manager.getTeleportBlock(clickLoc);
        Location foundLocation = clickLoc;

        if (teleportBlock == null) {
            return;
        }

        // Allow sign placement - don't teleport if player is holding a sign
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand != null && itemInHand.getType().name().contains("SIGN")) {
            return; // Let the sign be placed
        }

        event.setCancelled(true);

        if (!player.hasPermission("portalteleport.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use teleport blocks!");
            return;
        }

        if (!teleportBlock.isPaired()) {
            plugin.getLogger().info("Block at " + foundLocation.getBlockX() + "," +
                foundLocation.getBlockY() + "," + foundLocation.getBlockZ() +
                " is not paired. PairID=" + teleportBlock.getPairId().toString().substring(0, 8) +
                ", PairedLocation=" + teleportBlock.getPairedLocation());
            player.sendMessage(ChatColor.YELLOW + "This teleport block is not paired yet!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        Location destination = teleportBlock.getPairedLocation();
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Destination block not found!");
            return;
        }

        // Check if the destination teleport block still exists in our system
        TeleportBlock destTeleportBlock = manager.getTeleportBlock(destination);
        if (destTeleportBlock == null) {
            player.sendMessage(ChatColor.RED + "Destination teleport block has been destroyed!");
            teleportBlock.setPairedLocation(null);
            return;
        }

        Location safeTeleportLocation = destination.clone().add(0.5, 1, 0.5);
        safeTeleportLocation.setYaw(player.getLocation().getYaw());
        safeTeleportLocation.setPitch(player.getLocation().getPitch());

        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        player.teleport(safeTeleportLocation);

        player.playSound(safeTeleportLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        destination.getWorld().spawnParticle(Particle.PORTAL, safeTeleportLocation, 50, 0.5, 0.5, 0.5, 0.1);

        player.sendMessage(ChatColor.GREEN + "Teleported!");
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        if (manager.isTeleportBlock(location)) {
            // Make ruby teleport blocks insta-break
            event.setInstaBreak(true);
            plugin.getLogger().info("Making teleport block insta-break at " +
                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        if (!manager.isTeleportBlock(location)) {
            return;
        }

        TeleportBlock teleportBlock = manager.getTeleportBlock(location);
        if (teleportBlock == null) {
            return;
        }

        Player player = event.getPlayer();

        event.setDropItems(false);

        // Remove hologram for this block
        plugin.getVisuals().removeHologram(location);

        // Get paired location before removing
        Location pairedLoc = teleportBlock.getPairedLocation();

        ItemStack teleportBlockItem = createTeleportBlockItem(teleportBlock.getPairId());
        block.getWorld().dropItemNaturally(location, teleportBlockItem);

        manager.removeTeleportBlock(location);

        if (teleportBlock.isPaired()) {
            player.sendMessage(ChatColor.YELLOW + "Teleport block broken. Its pair is now unpaired.");
            if (pairedLoc != null && pairedLoc.getWorld() != null) {
                pairedLoc.getWorld().playSound(pairedLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                pairedLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, pairedLoc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);

                // Update the paired block's hologram to show it's now unpaired
                TeleportBlock pairedBlock = manager.getTeleportBlock(pairedLoc);
                if (pairedBlock != null) {
                    plugin.getVisuals().updateHologram(pairedBlock, manager.isFirstBlock(pairedBlock));
                }
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Unpaired teleport block broken.");
        }
    }

    private ItemStack createTeleportBlockItem(UUID pairId) {
        ItemStack item = new ItemStack(Material.REINFORCED_DEEPSLATE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Teleport Block");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click when placed to teleport",
                ChatColor.GRAY + "to its paired block.",
                ChatColor.DARK_GRAY + "Pair ID: " + pairId.toString().substring(0, 8) + "..."
            ));

            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "teleport_pair_id");
            container.set(key, PersistentDataType.STRING, pairId.toString());

            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Handle explosions from TNT, creepers, etc.
        for (Block block : event.blockList()) {
            if (manager.isTeleportBlock(block.getLocation())) {
                // Drop the teleport block item instead of destroying it
                TeleportBlock teleportBlock = manager.getTeleportBlock(block.getLocation());
                if (teleportBlock != null) {
                    plugin.getVisuals().removeHologram(block.getLocation());
                    ItemStack teleportBlockItem = createTeleportBlockItem(teleportBlock.getPairId());
                    block.getWorld().dropItemNaturally(block.getLocation(), teleportBlockItem);
                    manager.removeTeleportBlock(block.getLocation());
                    plugin.getLogger().info("Teleport block destroyed by explosion at " +
                        block.getLocation().getBlockX() + "," +
                        block.getLocation().getBlockY() + "," +
                        block.getLocation().getBlockZ());
                }
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        // Handle explosions from beds, respawn anchors, etc.
        for (Block block : event.blockList()) {
            if (manager.isTeleportBlock(block.getLocation())) {
                TeleportBlock teleportBlock = manager.getTeleportBlock(block.getLocation());
                if (teleportBlock != null) {
                    plugin.getVisuals().removeHologram(block.getLocation());
                    ItemStack teleportBlockItem = createTeleportBlockItem(teleportBlock.getPairId());
                    block.getWorld().dropItemNaturally(block.getLocation(), teleportBlockItem);
                    manager.removeTeleportBlock(block.getLocation());
                    plugin.getLogger().info("Teleport block destroyed by block explosion at " +
                        block.getLocation().getBlockX() + "," +
                        block.getLocation().getBlockY() + "," +
                        block.getLocation().getBlockZ());
                }
            }
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        // Prevent pistons from pushing teleport blocks
        for (Block block : event.getBlocks()) {
            if (manager.isTeleportBlock(block.getLocation())) {
                event.setCancelled(true);
                plugin.getLogger().info("Prevented piston from pushing teleport block at " +
                    block.getLocation().getBlockX() + "," +
                    block.getLocation().getBlockY() + "," +
                    block.getLocation().getBlockZ());
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        // Prevent pistons from pulling teleport blocks
        for (Block block : event.getBlocks()) {
            if (manager.isTeleportBlock(block.getLocation())) {
                event.setCancelled(true);
                plugin.getLogger().info("Prevented piston from pulling teleport block at " +
                    block.getLocation().getBlockX() + "," +
                    block.getLocation().getBlockY() + "," +
                    block.getLocation().getBlockZ());
                return;
            }
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        // Check if the sign is attached to a teleport block
        Block signBlock = event.getBlock();
        plugin.getLogger().info("SignChangeEvent fired for sign at " +
            signBlock.getLocation().getBlockX() + "," +
            signBlock.getLocation().getBlockY() + "," +
            signBlock.getLocation().getBlockZ() +
            " type=" + signBlock.getType().name());

        // Log the lines being set
        for (int i = 0; i < 4; i++) {
            plugin.getLogger().info("  Line " + i + ": " + event.getLine(i));
        }

        TeleportBlock teleportBlock = findAttachedTeleportBlock(signBlock);
        plugin.getLogger().info("findAttachedTeleportBlock result: " + (teleportBlock != null ? "FOUND" : "null"));

        if (teleportBlock != null) {
            // Schedule hologram update with longer delay to ensure sign text is saved
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("Updating hologram after sign change...");
                boolean isFirst = manager.isFirstBlock(teleportBlock);
                plugin.getVisuals().updateHologram(teleportBlock, isFirst);

                // Notify player
                Player player = event.getPlayer();
                player.sendMessage(ChatColor.GREEN + "Teleport block name updated!");
            }, 5L);
        }
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        Material type = brokenBlock.getType();

        // Check if a sign is being broken
        if (!type.name().contains("SIGN")) {
            return;
        }

        // Check if the sign was attached to a teleport block
        TeleportBlock teleportBlock = findAttachedTeleportBlock(brokenBlock);

        if (teleportBlock != null) {
            // Schedule hologram update for next tick (after sign is removed)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                boolean isFirst = manager.isFirstBlock(teleportBlock);
                plugin.getVisuals().updateHologram(teleportBlock, isFirst);
            }, 1L);
        }
    }

    private TeleportBlock findAttachedTeleportBlock(Block signBlock) {
        Material type = signBlock.getType();
        String typeName = type.name();

        plugin.getLogger().info("findAttachedTeleportBlock: signBlock=" + typeName +
            " at " + signBlock.getLocation().getBlockX() + "," +
            signBlock.getLocation().getBlockY() + "," +
            signBlock.getLocation().getBlockZ());

        // Check if it's a wall sign (not hanging)
        if (typeName.contains("WALL_SIGN") && !typeName.contains("HANGING")) {
            if (signBlock.getBlockData() instanceof WallSign) {
                WallSign wallSign = (WallSign) signBlock.getBlockData();
                Block attachedTo = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
                plugin.getLogger().info("  Wall sign facing " + wallSign.getFacing() +
                    ", attached to " + attachedTo.getType() + " at " +
                    attachedTo.getLocation().getBlockX() + "," +
                    attachedTo.getLocation().getBlockY() + "," +
                    attachedTo.getLocation().getBlockZ());
                return manager.getTeleportBlock(attachedTo.getLocation());
            }
        }

        // Check if it's a wall hanging sign
        if (typeName.contains("WALL_HANGING_SIGN")) {
            if (signBlock.getBlockData() instanceof WallHangingSign) {
                WallHangingSign hangingSign = (WallHangingSign) signBlock.getBlockData();
                // Wall hanging signs attach to blocks on either side (perpendicular to facing)
                // If facing EAST/WEST, attached to NORTH or SOUTH
                // If facing NORTH/SOUTH, attached to EAST or WEST
                BlockFace facing = hangingSign.getFacing();
                BlockFace side1, side2;
                if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
                    side1 = BlockFace.NORTH;
                    side2 = BlockFace.SOUTH;
                } else {
                    side1 = BlockFace.EAST;
                    side2 = BlockFace.WEST;
                }

                Block block1 = signBlock.getRelative(side1);
                Block block2 = signBlock.getRelative(side2);
                plugin.getLogger().info("  Wall hanging sign facing " + facing +
                    ", checking " + side1 + ": " + block1.getType() + " at " +
                    block1.getLocation().getBlockX() + "," +
                    block1.getLocation().getBlockY() + "," +
                    block1.getLocation().getBlockZ());
                plugin.getLogger().info("  Also checking " + side2 + ": " + block2.getType() + " at " +
                    block2.getLocation().getBlockX() + "," +
                    block2.getLocation().getBlockY() + "," +
                    block2.getLocation().getBlockZ());

                TeleportBlock result = manager.getTeleportBlock(block1.getLocation());
                if (result != null) return result;
                return manager.getTeleportBlock(block2.getLocation());
            }
        }

        // Check if it's a standing sign (on top of a block)
        if (typeName.contains("SIGN") && !typeName.contains("WALL") && !typeName.contains("HANGING")) {
            Block below = signBlock.getRelative(BlockFace.DOWN);
            plugin.getLogger().info("  Standing sign, checking block below: " + below.getType());
            return manager.getTeleportBlock(below.getLocation());
        }

        // Check if it's a hanging sign (hanging below a block)
        if (typeName.contains("HANGING_SIGN") && !typeName.contains("WALL")) {
            Block above = signBlock.getRelative(BlockFace.UP);
            plugin.getLogger().info("  Hanging sign, checking block above: " + above.getType());
            return manager.getTeleportBlock(above.getLocation());
        }

        return null;
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        // Check if the despawning item is a teleport block
        Item itemEntity = event.getEntity();
        ItemStack item = itemEntity.getItemStack();

        handleTeleportBlockItemDestroyed(item, itemEntity.getLocation(), "despawn");
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Check if an item entity is being damaged (lava, fire, cactus, void, etc.)
        if (!(event.getEntity() instanceof Item)) {
            return;
        }

        Item itemEntity = (Item) event.getEntity();
        ItemStack item = itemEntity.getItemStack();

        // Check if this damage will kill the item
        // Items have 5 health and most damage sources deal enough to kill them
        if (event.getFinalDamage() >= 1) {
            handleTeleportBlockItemDestroyed(item, itemEntity.getLocation(), event.getCause().name().toLowerCase());
        }
    }

    private void handleTeleportBlockItemDestroyed(ItemStack item, Location location, String cause) {
        UUID pairId = getTeleportBlockPairId(item);
        if (pairId == null) {
            return;
        }

        String pairIdShort = pairId.toString().substring(0, 8);

        plugin.getLogger().warning("Teleport block item destroyed (" + cause + ") with pair ID: " + pairIdShort +
            " at " + location.getWorld().getName() + " " +
            location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

        // Destroy any placed blocks with this pair ID
        java.util.List<TeleportBlock> placedBlocks = manager.getBlocksByPairId(pairId);
        if (!placedBlocks.isEmpty()) {
            // Remove holograms first
            for (TeleportBlock block : placedBlocks) {
                plugin.getVisuals().removeHologram(block.getLocation());
            }
            manager.destroyPair(pairId);

            // Broadcast to all players
            String message = ChatColor.RED + "[PortalTeleport] " + ChatColor.YELLOW +
                "A teleport block pair (" + ChatColor.GOLD + "TP-" + pairIdShort + ChatColor.YELLOW +
                ") was destroyed by " + cause + "! " + placedBlocks.size() + " placed block(s) removed.";
            plugin.getServer().broadcastMessage(message);

            plugin.getLogger().warning("PAIR DESTROYED: " + pairIdShort +
                " - " + placedBlocks.size() + " placed block(s) removed due to " + cause);
        }
    }

    private UUID getTeleportBlockPairId(ItemStack item) {
        if (item == null || item.getType() != Material.REINFORCED_DEEPSLATE || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "teleport_pair_id");

        if (!container.has(key, PersistentDataType.STRING)) {
            return null;
        }

        String pairIdString = container.get(key, PersistentDataType.STRING);
        if (pairIdString == null) {
            return null;
        }

        try {
            return UUID.fromString(pairIdString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}