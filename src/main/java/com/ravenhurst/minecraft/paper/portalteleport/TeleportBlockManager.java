package com.ravenhurst.minecraft.paper.portalteleport;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.Material;

public class TeleportBlockManager {
    private final PortalTeleportPlugin plugin;
    private final Map<UUID, List<TeleportBlock>> blockPairs;  // UUID -> List of 1 or 2 blocks
    private File dataFile;
    private FileConfiguration dataConfig;

    public TeleportBlockManager(PortalTeleportPlugin plugin) {
        this.plugin = plugin;
        this.blockPairs = new HashMap<>();
        loadData();
    }

    public TeleportBlock createTeleportBlock(Location location, UUID pairId) {
        // Normalize location to block coordinates
        Location blockLoc = new Location(
            location.getWorld(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );

        // Check if this location already has a teleport block
        if (getTeleportBlock(blockLoc) != null) {
            return null;
        }

        TeleportBlock block = new TeleportBlock(blockLoc, pairId);

        // Get or create the list for this pair ID
        List<TeleportBlock> pairList = blockPairs.computeIfAbsent(pairId, k -> new ArrayList<>());

        // Log current state before adding
        plugin.getLogger().info("Before add - pairList size: " + pairList.size());
        for (int i = 0; i < pairList.size(); i++) {
            TeleportBlock tb = pairList.get(i);
            plugin.getLogger().info("  [" + i + "] " + tb.getLocation().getBlockX() + "," + tb.getLocation().getBlockY() + "," + tb.getLocation().getBlockZ() +
                " paired=" + tb.isPaired());
        }

        // Add the new block to the list FIRST (max 2 blocks per pair)
        if (pairList.size() < 2) {
            pairList.add(block);
        }

        plugin.getLogger().info("After add - pairList size: " + pairList.size());

        // If there are now 2 blocks, link them
        if (pairList.size() == 2) {
            TeleportBlock first = pairList.get(0);
            TeleportBlock second = pairList.get(1);
            first.setPairedLocation(second.getLocation());
            second.setPairedLocation(first.getLocation());

            plugin.getLogger().info("Paired blocks at " +
                first.getLocation().getBlockX() + "," + first.getLocation().getBlockY() + "," + first.getLocation().getBlockZ() +
                " (paired=" + first.isPaired() + ") and " +
                second.getLocation().getBlockX() + "," + second.getLocation().getBlockY() + "," + second.getLocation().getBlockZ() +
                " (paired=" + second.isPaired() + ")");
        } else {
            plugin.getLogger().info("Added unpaired block at " + blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ() +
                " with pair ID " + pairId.toString().substring(0, 8) + ". Waiting for pair...");
        }

        // Log final state
        plugin.getLogger().info("Final state - pairList size: " + pairList.size());
        for (int i = 0; i < pairList.size(); i++) {
            TeleportBlock tb = pairList.get(i);
            plugin.getLogger().info("  [" + i + "] " + tb.getLocation().getBlockX() + "," + tb.getLocation().getBlockY() + "," + tb.getLocation().getBlockZ() +
                " paired=" + tb.isPaired() + " pairedLoc=" + (tb.getPairedLocation() != null ?
                    tb.getPairedLocation().getBlockX() + "," + tb.getPairedLocation().getBlockY() + "," + tb.getPairedLocation().getBlockZ() : "null"));
        }

        saveData();
        return block;
    }

    public TeleportBlock getTeleportBlock(Location location) {
        // Search through all pairs to find a block at this location
        for (Map.Entry<UUID, List<TeleportBlock>> entry : blockPairs.entrySet()) {
            List<TeleportBlock> pairList = entry.getValue();
            for (int i = 0; i < pairList.size(); i++) {
                TeleportBlock block = pairList.get(i);
                Location blockLoc = block.getLocation();
                if (blockLoc.getWorld().equals(location.getWorld()) &&
                    blockLoc.getBlockX() == location.getBlockX() &&
                    blockLoc.getBlockY() == location.getBlockY() &&
                    blockLoc.getBlockZ() == location.getBlockZ()) {
                    plugin.getLogger().info("Found block at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                        " [index=" + i + "/" + pairList.size() + "]" +
                        " with pairId=" + entry.getKey().toString().substring(0,8) +
                        ", isPaired=" + block.isPaired() +
                        ", pairedLoc=" + (block.getPairedLocation() != null ?
                            block.getPairedLocation().getBlockX() + "," + block.getPairedLocation().getBlockY() + "," + block.getPairedLocation().getBlockZ()
                            : "null") +
                        ", blockHashCode=" + System.identityHashCode(block));
                    return block;
                }
            }
        }
        return null;
    }

    public void removeTeleportBlock(Location location) {
        TeleportBlock blockToRemove = getTeleportBlock(location);
        if (blockToRemove == null) {
            return;
        }

        UUID pairId = blockToRemove.getPairId();
        List<TeleportBlock> pairList = blockPairs.get(pairId);

        if (pairList != null) {
            // Remove the block from the list
            pairList.remove(blockToRemove);

            // If there's still another block, unpair it
            if (!pairList.isEmpty()) {
                TeleportBlock remainingBlock = pairList.get(0);
                remainingBlock.setPairedLocation(null);
                plugin.getLogger().info("Unpaired block at " +
                    remainingBlock.getLocation().getBlockX() + "," +
                    remainingBlock.getLocation().getBlockY() + "," +
                    remainingBlock.getLocation().getBlockZ());
            }

            // If the pair list is now empty, remove it
            if (pairList.isEmpty()) {
                blockPairs.remove(pairId);
            }
        }

        plugin.getLogger().info("Removed teleport block at " +
            location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        saveData();
    }

    public boolean isTeleportBlock(Location location) {
        return getTeleportBlock(location) != null;
    }

    public java.util.List<TeleportBlock> getAllBlocks() {
        java.util.List<TeleportBlock> allBlocks = new ArrayList<>();
        for (List<TeleportBlock> pairList : blockPairs.values()) {
            allBlocks.addAll(pairList);
        }
        return allBlocks;
    }

    public boolean isFirstBlock(TeleportBlock block) {
        List<TeleportBlock> pairList = blockPairs.get(block.getPairId());
        if (pairList != null && !pairList.isEmpty()) {
            return pairList.get(0).equals(block);
        }
        return true;
    }

    public List<TeleportBlock> getBlocksByPairId(UUID pairId) {
        List<TeleportBlock> pairList = blockPairs.get(pairId);
        if (pairList != null) {
            return new ArrayList<>(pairList);
        }
        return new ArrayList<>();
    }

    public void destroyPair(UUID pairId) {
        List<TeleportBlock> pairList = blockPairs.remove(pairId);
        if (pairList != null) {
            for (TeleportBlock block : pairList) {
                Location loc = block.getLocation();
                if (loc.getWorld() != null) {
                    // Set the block to air (destroy it)
                    loc.getBlock().setType(Material.AIR);
                    // Spawn particles to show destruction
                    loc.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                        loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                }
                plugin.getLogger().info("Destroyed orphaned teleport block at " +
                    loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            }
            saveData();
        }
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "teleportblocks.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.contains("pairs")) {
            for (String pairIdStr : dataConfig.getConfigurationSection("pairs").getKeys(false)) {
                UUID pairId = UUID.fromString(pairIdStr);
                List<TeleportBlock> pairList = new ArrayList<>();

                String pairPath = "pairs." + pairIdStr;
                if (dataConfig.contains(pairPath + ".blocks")) {
                    List<Map<?, ?>> blockMaps = dataConfig.getMapList(pairPath + ".blocks");
                    for (Map<?, ?> blockMap : blockMaps) {
                        if (blockMap.containsKey("location")) {
                            Location loc = (Location) blockMap.get("location");
                            if (loc != null) {
                                Location blockLoc = new Location(
                                    loc.getWorld(),
                                    loc.getBlockX(),
                                    loc.getBlockY(),
                                    loc.getBlockZ()
                                );
                                TeleportBlock block = new TeleportBlock(blockLoc, pairId);
                                pairList.add(block);
                            }
                        }
                    }
                }

                // Set up pairing if we have 2 blocks
                if (pairList.size() == 2) {
                    TeleportBlock first = pairList.get(0);
                    TeleportBlock second = pairList.get(1);
                    first.setPairedLocation(second.getLocation());
                    second.setPairedLocation(first.getLocation());
                }

                if (!pairList.isEmpty()) {
                    blockPairs.put(pairId, pairList);
                }
            }
        }
    }

    public int validateAndCleanup() {
        plugin.getLogger().info("Running validateAndCleanup...");
        int removed = 0;
        Iterator<Map.Entry<UUID, List<TeleportBlock>>> iterator = blockPairs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, List<TeleportBlock>> entry = iterator.next();
            List<TeleportBlock> pairList = entry.getValue();
            Iterator<TeleportBlock> blockIterator = pairList.iterator();

            while (blockIterator.hasNext()) {
                TeleportBlock teleportBlock = blockIterator.next();
                Location loc = teleportBlock.getLocation();

                // Check if the block at this location is actually a glass block
                if (loc.getWorld() == null || loc.getWorld().getBlockAt(loc).getType() != Material.REINFORCED_DEEPSLATE) {
                    plugin.getLogger().info("Removing invalid teleport block at " +
                        loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());

                    blockIterator.remove();
                    removed++;
                }
            }

            // Clean up pairing if only one block remains
            if (pairList.size() == 1) {
                pairList.get(0).setPairedLocation(null);
            }

            // Remove empty pairs
            if (pairList.isEmpty()) {
                iterator.remove();
            }
        }

        if (removed > 0) {
            saveData();
        }

        return removed;
    }

    public void saveData() {
        dataConfig = new YamlConfiguration();

        for (Map.Entry<UUID, List<TeleportBlock>> entry : blockPairs.entrySet()) {
            UUID pairId = entry.getKey();
            List<TeleportBlock> pairList = entry.getValue();

            String pairPath = "pairs." + pairId.toString();
            List<Map<String, Object>> blockMaps = new ArrayList<>();

            for (TeleportBlock block : pairList) {
                Map<String, Object> blockMap = new HashMap<>();
                blockMap.put("location", block.getLocation());
                blockMaps.add(blockMap);
            }

            dataConfig.set(pairPath + ".blocks", blockMaps);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}