package com.muhammaddaffa.nextgens.refund;

import com.muhammaddaffa.nextgens.generators.Generator;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.utils.Common;
import com.muhammaddaffa.nextgens.utils.Config;
import com.muhammaddaffa.nextgens.utils.Executor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;

public class RefundManager implements Listener {

    private final Map<UUID, List<String>> itemMap = new HashMap<>();

    private final GeneratorManager generatorManager;
    public RefundManager(GeneratorManager generatorManager) {
        this.generatorManager = generatorManager;
    }

    public void registerItem(UUID uuid, String id) {
        List<String> generators = this.itemMap.computeIfAbsent(uuid, k -> new ArrayList<>());
        generators.add(id);
    }

    public void giveItemJoin(Player player) {
        List<String> generators = this.itemMap.remove(player.getUniqueId());
        // if there is no generators, return
        if (generators == null) {
            return;
        }
        // loop through all generators
        for (String id : generators) {
            // get the generator
            Generator generator = this.generatorManager.getGenerator(id);
            // if generator doesn't exist, skip it
            if (generator == null) {
                continue;
            }
            // proceed to give player the generator
            Common.addInventoryItem(player, generator.createItem(1));
        }
    }

    public void load() {
        FileConfiguration config = Config.DATA.getConfig();
        // check if there are any data
        if (!config.isConfigurationSection("items")) {
            return;
        }
        // loop through all data
        for (String uuidString : config.getConfigurationSection("items").getKeys(false)) {
            // get the data
            UUID uuid = UUID.fromString(uuidString);
            List<String> generators = config.getStringList("items." + uuidString);

            // store it on the cache
            this.itemMap.put(uuid, generators);
        }
    }

    public void save() {
        Config data = Config.DATA;
        FileConfiguration config = data.getConfig();
        // loop through all data
        this.itemMap.forEach((uuid, generators) -> {
            config.set("items." + uuid.toString(), generators);
        });
        // save the config
        data.saveConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onJoin(PlayerJoinEvent event) {
        // execute code a task later
        Executor.syncLater(3L, () -> this.giveItemJoin(event.getPlayer()));
    }

}
