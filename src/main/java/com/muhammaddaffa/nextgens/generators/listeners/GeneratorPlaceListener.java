package com.muhammaddaffa.nextgens.generators.listeners;

import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.api.events.generators.GeneratorPlaceEvent;
import com.muhammaddaffa.nextgens.generators.ActiveGenerator;
import com.muhammaddaffa.nextgens.generators.Generator;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.users.UserManager;
import com.muhammaddaffa.nextgens.utils.Utils;
import com.muhammaddaffa.nextgens.utils.VisualAction;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public record GeneratorPlaceListener(
        GeneratorManager generatorManager,
        UserManager userManager
) implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void generatorPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        ItemStack stack = event.getItemInHand();
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        Generator generator = this.generatorManager.getGenerator(stack);

        if (!isValidGenerator(generator)) return;

        if (hasExceededMaxGenerators(player)) {
            notifyMaxGenerators(player, event);
            return;
        }

        if (isPermissionDenied(player, generator, config)) {
            notifyPermissionDenied(player, event);
            return;
        }

        if (isInBlacklistedWorld(player, config)) {
            notifyInvalidWorld(player, event);
            return;
        }

        if (isTooCloseToOtherGenerators(block, config)) {
            notifyTooClose(player, event);
            return;
        }

        handleGeneratorPlacement(player, block, generator, config, event);
    }

    private boolean isValidGenerator(Generator generator) {
        return generator != null && !NextGens.STOPPING;
    }

    private boolean hasExceededMaxGenerators(Player player) {
        return this.generatorManager.getGeneratorCount(player) >= this.userManager.getMaxSlot(player);
    }

    private void notifyMaxGenerators(Player player, BlockPlaceEvent event) {
        event.setCancelled(true);
        NextGens.DEFAULT_CONFIG.sendMessage(player, "messages.max-gen");
        Utils.bassSound(player);
    }

    private boolean isPermissionDenied(Player player, Generator generator, FileConfiguration config) {
        return config.getBoolean("place-permission")
                && !player.hasPermission("nextgens.generator." + generator.id())
                && !player.hasPermission("nextgens.generator.*");
    }

    private void notifyPermissionDenied(Player player, BlockPlaceEvent event) {
        event.setCancelled(true);
        NextGens.DEFAULT_CONFIG.sendMessage(player, "messages.no-permission-gen");
        Utils.bassSound(player);
    }

    private boolean isInBlacklistedWorld(Player player, FileConfiguration config) {
        return config.getStringList("blacklisted-worlds").contains(player.getWorld().getName());
    }

    private void notifyInvalidWorld(Player player, BlockPlaceEvent event) {
        event.setCancelled(true);
        NextGens.DEFAULT_CONFIG.sendMessage(player, "messages.invalid-world");
        Utils.bassSound(player);
    }

    private boolean isTooCloseToOtherGenerators(Block block, FileConfiguration config) {
        if (!config.getBoolean("generator-place-distance.enabled")) return false;

        double distance = config.getInt("generator-place-distance.distance");
        for (ActiveGenerator active : this.generatorManager.getActiveGenerator()) {
            if (!active.getLocation().getWorld().equals(block.getWorld())) continue;
            if (active.getLocation().distance(block.getLocation()) < distance) return true;
        }
        return false;
    }

    private void notifyTooClose(Player player, BlockPlaceEvent event) {
        event.setCancelled(true);
        NextGens.DEFAULT_CONFIG.sendMessage(player, "messages.too-close");
        Utils.bassSound(player);
    }

    private void handleGeneratorPlacement(Player player, Block block, Generator generator, FileConfiguration config, BlockPlaceEvent event) {
        event.setCancelled(false);

        GeneratorPlaceEvent placeEvent = new GeneratorPlaceEvent(generator, player);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        this.generatorManager.registerGenerator(player, generator, block);

        VisualAction.send(player, config, "generator-place-options", new Placeholder()
                .add("{gen}", generator.displayName())
                .add("{current}", this.generatorManager.getGeneratorCount(player))
                .add("{max}", this.userManager.getMaxSlot(player)));

        Executor.async(() -> playPlacementParticles(block, config, generator));
    }

    private void playPlacementParticles(Block block, FileConfiguration config, Generator generator) {
        if (!config.getBoolean("generator-place-options.particles")) return;

        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.85, 0.5), 30, 0.5, 0.5, 0.5, 2.5, generator.item().getType().createBlockData());
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.85, 0.5), 50, 0.5, 0.5, 0.5, 2.5);
    }

}
