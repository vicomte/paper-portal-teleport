package com.ravenhurst.minecraft.paper.portalteleport;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
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
            createHologram(block, isFirstBlock);
        }
    }

    private void updateHologramText(ArmorStand hologram, TeleportBlock block, boolean isFirstBlock) {
        String pairIdShort = block.getPairId().toString().substring(0, 8);
        ChatColor pairColor = getPairColor(block.getPairId());
        String label = isFirstBlock ? "α" : "β";
        String status = block.isPaired() ? ChatColor.GREEN + "●" : ChatColor.RED + "○";

        String displayName = pairColor + "TP-" + pairIdShort + " " + ChatColor.WHITE + label + " " + status;

        // Add destination coordinates if enabled and paired
        if (plugin.showDestinationCoords() && block.isPaired()) {
            Location dest = block.getPairedLocation();
            if (dest != null) {
                displayName += ChatColor.GRAY + " -> " + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ();
            }
        }

        hologram.setCustomName(displayName);
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