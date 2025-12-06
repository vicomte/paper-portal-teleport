package com.ravenhurst.minecraft.paper.portalteleport;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportBlockVisuals {
    private final PortalTeleportPlugin plugin;
    private final TeleportBlockManager manager;
    private final Map<Location, ArmorStand> holograms;
    private BukkitTask particleTask;

    public TeleportBlockVisuals(PortalTeleportPlugin plugin, TeleportBlockManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.holograms = new HashMap<>();
    }

    public void startParticleTask() {
        // Run particle effects every 20 ticks (1 second)
        particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            manager.getAllBlocks().forEach(this::spawnParticles);
        }, 20L, 20L);
    }

    public void stopParticleTask() {
        if (particleTask != null) {
            particleTask.cancel();
        }
    }

    public void createHologram(TeleportBlock block, boolean isFirstBlock) {
        Location loc = block.getLocation();
        Location hologramLoc = loc.clone().add(0.5, 1.5, 0.5);

        // Remove existing hologram if present
        removeHologram(loc);

        ArmorStand hologram = (ArmorStand) loc.getWorld().spawnEntity(hologramLoc, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setCustomNameVisible(true);
        hologram.setMarker(true);
        hologram.setInvulnerable(true);

        updateHologramText(hologram, block, isFirstBlock);

        holograms.put(normalizeLocation(loc), hologram);
    }

    public void updateHologram(TeleportBlock block, boolean isFirstBlock) {
        Location loc = normalizeLocation(block.getLocation());
        ArmorStand hologram = holograms.get(loc);

        if (hologram != null && !hologram.isDead()) {
            updateHologramText(hologram, block, isFirstBlock);
        } else {
            // Remove from map if it was dead
            if (hologram != null) {
                holograms.remove(loc);
            }
            // Also clean up any orphaned armor stands at this location
            cleanupHologramsAtLocation(loc);
            createHologram(block, isFirstBlock);
        }
    }

    private void cleanupHologramsAtLocation(Location loc) {
        Location checkLoc = loc.clone().add(0.5, 1.5, 0.5);
        for (Entity entity : loc.getWorld().getNearbyEntities(checkLoc, 0.5, 0.5, 0.5)) {
            if (entity instanceof ArmorStand) {
                ArmorStand stand = (ArmorStand) entity;
                // Remove any invisible marker armor stands (our holograms)
                if (!stand.isVisible() && stand.isMarker()) {
                    stand.remove();
                }
            }
        }
    }

    private void updateHologramText(ArmorStand hologram, TeleportBlock block, boolean isFirstBlock) {
        ChatColor pairColor = getPairColor(block.getPairId());
        String label = isFirstBlock ? "α" : "β";
        String status = block.isPaired() ? ChatColor.GREEN + "●" : ChatColor.RED + "○";

        // Check for attached sign with custom name
        String signName = getAttachedSignText(block.getLocation());
        String displayName;

        if (signName != null && !signName.isEmpty()) {
            // Use custom name from sign
            displayName = pairColor + signName + " " + ChatColor.WHITE + label + " " + status;
        } else {
            // Use UUID-based name
            String pairIdShort = block.getPairId().toString().substring(0, 8);
            displayName = pairColor + "TP-" + pairIdShort + " " + ChatColor.WHITE + label + " " + status;
        }

        // Add destination coordinates if enabled and paired
        if (plugin.showDestinationCoords() && block.isPaired()) {
            Location dest = block.getPairedLocation();
            if (dest != null) {
                displayName += ChatColor.GRAY + " -> " + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ();
            }
        }

        hologram.setCustomName(displayName);
    }

    private String getAttachedSignText(Location blockLoc) {
        Block block = blockLoc.getBlock();

        // Check all faces for attached signs
        BlockFace[] faces = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP };

        for (BlockFace face : faces) {
            Block adjacent = block.getRelative(face);
            Material type = adjacent.getType();
            String typeName = type.name();

            // Check for wall signs attached to this block
            if (typeName.contains("WALL_SIGN") && !typeName.contains("HANGING")) {
                if (adjacent.getBlockData() instanceof WallSign) {
                    WallSign wallSign = (WallSign) adjacent.getBlockData();
                    // Check if the sign is facing away from our block (meaning it's attached to us)
                    if (wallSign.getFacing().getOppositeFace() == face) {
                        return readSignText(adjacent);
                    }
                }
            }

            // Check for wall hanging signs attached to this block
            // Wall hanging signs attach perpendicular to their facing direction
            if (typeName.contains("WALL_HANGING_SIGN")) {
                if (adjacent.getBlockData() instanceof org.bukkit.block.data.type.WallHangingSign) {
                    org.bukkit.block.data.type.WallHangingSign hangingSign =
                        (org.bukkit.block.data.type.WallHangingSign) adjacent.getBlockData();
                    BlockFace signFacing = hangingSign.getFacing();
                    // If sign faces EAST/WEST, it's attached to blocks on NORTH/SOUTH sides
                    // If sign faces NORTH/SOUTH, it's attached to blocks on EAST/WEST sides
                    boolean isAttached = false;
                    if ((signFacing == BlockFace.EAST || signFacing == BlockFace.WEST) &&
                        (face == BlockFace.NORTH || face == BlockFace.SOUTH)) {
                        isAttached = true;
                    } else if ((signFacing == BlockFace.NORTH || signFacing == BlockFace.SOUTH) &&
                               (face == BlockFace.EAST || face == BlockFace.WEST)) {
                        isAttached = true;
                    }
                    if (isAttached) {
                        return readSignText(adjacent);
                    }
                }
            }

            // Check for standing sign or hanging sign on top
            if (face == BlockFace.UP && typeName.contains("SIGN") &&
                !typeName.contains("WALL") && !typeName.contains("HANGING")) {
                return readSignText(adjacent);
            }

            // Check for hanging sign below (attached to bottom of block)
            if (face == BlockFace.UP) {
                Block below = block.getRelative(BlockFace.DOWN);
                String belowType = below.getType().name();
                if (belowType.contains("HANGING_SIGN") && !belowType.contains("WALL")) {
                    return readSignText(below);
                }
            }
        }

        return null;
    }

    private String readSignText(Block signBlock) {
        if (signBlock.getState() instanceof Sign) {
            Sign sign = (Sign) signBlock.getState();
            StringBuilder text = new StringBuilder();

            for (String line : sign.getLines()) {
                String stripped = ChatColor.stripColor(line).trim();
                if (!stripped.isEmpty()) {
                    if (text.length() > 0) {
                        text.append(" ");
                    }
                    text.append(stripped);
                }
            }

            return text.toString();
        }
        return null;
    }

    public void removeHologram(Location loc) {
        Location normalized = normalizeLocation(loc);
        ArmorStand hologram = holograms.remove(normalized);
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }
    }

    public void removeAllHolograms() {
        for (ArmorStand hologram : holograms.values()) {
            if (hologram != null && !hologram.isDead()) {
                hologram.remove();
            }
        }
        holograms.clear();
    }

    public int cleanupOrphanedHolograms() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand) {
                    ArmorStand stand = (ArmorStand) entity;
                    String name = stand.getCustomName();
                    // Check if this is one of our hologram armor stands
                    // Just check for "TP-" in name and invisible - older versions may not have marker set
                    if (name != null && name.contains("TP-") && !stand.isVisible()) {
                        stand.remove();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private void spawnParticles(TeleportBlock block) {
        Location loc = block.getLocation();
        if (loc.getWorld() == null) return;

        Color color = getParticleColor(block.getPairId());
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);

        // Spawn particles at the center of the block
        Location particleLoc = loc.clone().add(0.5, 1.0, 0.5);

        // Different particle pattern based on paired status
        if (block.isPaired()) {
            // Paired: particles spiral upward
            for (int i = 0; i < 5; i++) {
                double angle = (System.currentTimeMillis() / 100.0 + i * 72) * Math.PI / 180.0;
                double x = Math.cos(angle) * 0.3;
                double z = Math.sin(angle) * 0.3;
                loc.getWorld().spawnParticle(Particle.REDSTONE,
                    particleLoc.clone().add(x, i * 0.1, z),
                    1, 0, 0, 0, 0, dustOptions);
            }
        } else {
            // Unpaired: particles pulse at block center
            double pulse = (Math.sin(System.currentTimeMillis() / 200.0) + 1) * 0.2;
            loc.getWorld().spawnParticle(Particle.REDSTONE,
                particleLoc,
                3, pulse, pulse, pulse, 0, dustOptions);
        }
    }

    private ChatColor getPairColor(UUID pairId) {
        // Use the pair ID to consistently generate a color
        int hash = pairId.hashCode();
        ChatColor[] colors = {
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN,
            ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE, ChatColor.WHITE
        };
        return colors[Math.abs(hash) % colors.length];
    }

    private Color getParticleColor(UUID pairId) {
        // Generate RGB color from UUID hash
        int hash = pairId.hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);

        // Ensure minimum brightness
        r = Math.max(r, 80);
        g = Math.max(g, 80);
        b = Math.max(b, 80);

        return Color.fromRGB(r, g, b);
    }

    private Location normalizeLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}