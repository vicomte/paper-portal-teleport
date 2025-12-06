package com.ravenhurst.minecraft.paper.portalteleport;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;

import java.util.Arrays;
import java.util.UUID;

public class PortalTeleportPlugin extends JavaPlugin implements Listener {
    private TeleportBlockManager teleportBlockManager;
    private TeleportBlockVisuals visuals;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        teleportBlockManager = new TeleportBlockManager(this);
        visuals = new TeleportBlockVisuals(this, teleportBlockManager);

        // Clean up invalid blocks on startup
        getServer().getScheduler().runTaskLater(this, () -> {
            int removed = teleportBlockManager.validateAndCleanup();
            if (removed > 0) {
                getLogger().info("Cleaned up " + removed + " invalid teleport blocks on startup.");
            }
        }, 20L); // Run after 1 second

        // Schedule periodic validation every 30 seconds to catch blocks destroyed without events
        getServer().getScheduler().runTaskTimer(this, () -> {
            int removed = teleportBlockManager.validateAndCleanup();
            if (removed > 0) {
                getLogger().info("Periodic cleanup removed " + removed + " invalid teleport blocks.");
            }
        }, 600L, 600L); // 30 seconds = 600 ticks

        getServer().getPluginManager().registerEvents(
            new TeleportListener(this, teleportBlockManager),
            this
        );

        getServer().getPluginManager().registerEvents(this, this);

        // Register command
        getCommand("portalteleport").setExecutor(
            new PortalTeleportCommand(this, teleportBlockManager)
        );

        registerTeleportBlockRecipe();

        // Start particle effects and create holograms for existing blocks
        getServer().getScheduler().runTaskLater(this, () -> {
            // Clean up any orphaned hologram armor stands from previous sessions
            int cleaned = visuals.cleanupOrphanedHolograms();
            if (cleaned > 0) {
                getLogger().info("Cleaned up " + cleaned + " orphaned hologram armor stands.");
            }

            visuals.startParticleTask();
            // Create holograms for all existing blocks
            for (TeleportBlock block : teleportBlockManager.getAllBlocks()) {
                boolean isFirst = teleportBlockManager.isFirstBlock(block);
                visuals.createHologram(block, isFirst);
            }
        }, 40L); // Wait 2 seconds for world to load

        getLogger().info("PortalTeleport plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (visuals != null) {
            visuals.stopParticleTask();
            visuals.removeAllHolograms();
        }
        if (teleportBlockManager != null) {
            teleportBlockManager.saveData();
        }
        getLogger().info("PortalTeleport plugin disabled!");
    }

    public TeleportBlockVisuals getVisuals() {
        return visuals;
    }

    private void registerTeleportBlockRecipe() {
        ItemStack dummyResult = new ItemStack(Material.REINFORCED_DEEPSLATE, 2);
        ItemMeta meta = dummyResult.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Ruby Teleport Block");
            dummyResult.setItemMeta(meta);
        }

        NamespacedKey recipeKey = new NamespacedKey(this, "teleport_block_pair");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, dummyResult);

        recipe.shape(
            "ALA",
            "BCB",
            "ABA"
        );

        recipe.setIngredient('L', Material.LIGHTNING_ROD);
        recipe.setIngredient('C', Material.COMPASS);
        recipe.setIngredient('B', Material.COPPER_BLOCK);
        recipe.setIngredient('A', Material.AMETHYST_SHARD);

        boolean added = Bukkit.addRecipe(recipe);
        getLogger().info("Recipe registered: " + added);

        Bukkit.getScheduler().runTask(this, () -> {
            getServer().getOnlinePlayers().forEach(player -> {
                player.discoverRecipe(recipeKey);
                getLogger().info("Recipe discovered for player: " + player.getName());
            });
        });
    }

    private ItemStack createTeleportBlockItem(UUID pairId) {
        ItemStack item = new ItemStack(Material.REINFORCED_DEEPSLATE, 2);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Ruby Teleport Block");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click when placed to teleport",
                ChatColor.GRAY + "to its paired block.",
                ChatColor.DARK_GRAY + "Pair ID: " + pairId.toString().substring(0, 8) + "...",
                "",
                ChatColor.YELLOW + "Crafted as a pair!"
            ));

            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(this, "teleport_pair_id");
            container.set(key, PersistentDataType.STRING, pairId.toString());

            item.setItemMeta(meta);
        }

        return item;
    }

    public TeleportBlockManager getTeleportBlockManager() {
        return teleportBlockManager;
    }

    public boolean showDestinationCoords() {
        return getConfig().getBoolean("show-destination-coords", false);
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (event.getRecipe() instanceof ShapedRecipe) {
            ShapedRecipe recipe = (ShapedRecipe) event.getRecipe();
            NamespacedKey key = recipe.getKey();

            if (key.getNamespace().equals("portalteleport") &&
                key.getKey().equals("teleport_block_pair")) {

                getLogger().info("Crafting teleport blocks!");

                CraftingInventory inv = event.getInventory();
                UUID pairId = UUID.randomUUID();
                ItemStack result = createTeleportBlockItem(pairId);

                inv.setResult(result);
                getLogger().info("Created pair with ID: " + pairId.toString().substring(0, 8));
            }
        }
    }
}