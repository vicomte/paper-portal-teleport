package com.ravenhurst.minecraft.paper.portalteleport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.UUID;

public class TeleportBlock {
    private final Location location;
    private final UUID pairId;
    private Location pairedLocation;
    private final long createdAt;

    public TeleportBlock(Location location, UUID pairId) {
        this.location = location;
        this.pairId = pairId;
        this.createdAt = System.currentTimeMillis();
        this.pairedLocation = null;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getPairId() {
        return pairId;
    }

    public Location getPairedLocation() {
        return pairedLocation != null ? pairedLocation.clone() : null;
    }

    public void setPairedLocation(Location pairedLocation) {
        if (pairedLocation != null) {
            // Normalize to block coordinates
            this.pairedLocation = new Location(
                pairedLocation.getWorld(),
                pairedLocation.getBlockX(),
                pairedLocation.getBlockY(),
                pairedLocation.getBlockZ()
            );
        } else {
            this.pairedLocation = null;
        }
    }

    public boolean isPaired() {
        return pairedLocation != null;
    }

    public boolean isValid() {
        Block block = location.getBlock();
        return block.getType() == Material.REINFORCED_DEEPSLATE;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TeleportBlock)) return false;
        TeleportBlock other = (TeleportBlock) obj;
        return location.equals(other.location);
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }
}