package com.muhammaddaffa.nextgens;

import com.muhammaddaffa.nextgens.commands.MainCommand;
import com.muhammaddaffa.nextgens.commands.PickupCommand;
import com.muhammaddaffa.nextgens.commands.SellCommand;
import com.muhammaddaffa.nextgens.commands.ShopCommand;
import com.muhammaddaffa.nextgens.database.DatabaseManager;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorListener;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.generators.runnables.CorruptionTask;
import com.muhammaddaffa.nextgens.generators.runnables.GeneratorTask;
import com.muhammaddaffa.nextgens.generators.runnables.NotifyTask;
import com.muhammaddaffa.nextgens.hooks.bento.BentoListener;
import com.muhammaddaffa.nextgens.hooks.papi.GensExpansion;
import com.muhammaddaffa.nextgens.hooks.ssb2.SSB2Listener;
import com.muhammaddaffa.nextgens.hooks.vault.VaultEconomy;
import com.muhammaddaffa.nextgens.refund.RefundManager;
import com.muhammaddaffa.nextgens.users.managers.UserManager;
import com.muhammaddaffa.nextgens.utils.Config;
import com.muhammaddaffa.nextgens.utils.Executor;
import com.muhammaddaffa.nextgens.utils.Logger;
import com.muhammaddaffa.nextgens.utils.gui.SimpleInventoryManager;
import com.tchristofferson.configupdater.ConfigUpdater;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public final class NextGens extends JavaPlugin {

    private static final int BSTATS_ID = 19417;

    private static NextGens instance;
    public static NamespacedKey generator_id;
    public static NamespacedKey drop_value;

    private final DatabaseManager dbm = new DatabaseManager();
    private final GeneratorManager generatorManager = new GeneratorManager(this.dbm);
    private final UserManager userManager = new UserManager(this.dbm);
    private final RefundManager refundManager = new RefundManager(this.generatorManager);

    @Override
    public void onEnable() {
        instance = this;
        generator_id = new NamespacedKey(this, "nextgens_generator_id");
        drop_value = new NamespacedKey(this, "nextgens_drop_value");

        // fancy big text
        Logger.info("""
                NextGens plugin by aglerr - starting...
                
                ███╗░░██╗███████╗██╗░░██╗████████╗░██████╗░███████╗███╗░░██╗░██████╗
                ████╗░██║██╔════╝╚██╗██╔╝╚══██╔══╝██╔════╝░██╔════╝████╗░██║██╔════╝
                ██╔██╗██║█████╗░░░╚███╔╝░░░░██║░░░██║░░██╗░█████╗░░██╔██╗██║╚█████╗░
                ██║╚████║██╔══╝░░░██╔██╗░░░░██║░░░██║░░╚██╗██╔══╝░░██║╚████║░╚═══██╗
                ██║░╚███║███████╗██╔╝╚██╗░░░██║░░░╚██████╔╝███████╗██║░╚███║██████╔╝
                ╚═╝░░╚══╝╚══════╝╚═╝░░╚═╝░░░╚═╝░░░░╚═════╝░╚══════╝╚═╝░░╚══╝╚═════╝░
                """);

        // initialize stuff
        Config.init();
        VaultEconomy.init();

        // update config
        this.updateConfig();

        // connect to database and create the table
        this.dbm.connect();
        this.dbm.createGeneratorTable();
        this.dbm.createUserTable();

        // load all generators
        this.generatorManager.loadGenerators();
        // delayed active generator load
        Executor.asyncLater(3L, this.generatorManager::loadActiveGenerator);

        // load users
        this.userManager.loadUser();

        // load the refund
        this.refundManager.load();

        // register commands & listeners
        this.registerCommands();
        this.registerListeners();

        // register task
        this.registerTask();
        // register hook
        this.registerHook();
    }

    @Override
    public void onDisable() {
        // remove all holograms
        GeneratorTask.flush();
        // save refunds
        this.refundManager.save();
        // save the generators
        this.generatorManager.saveActiveGenerator();
        // save the users
        this.userManager.saveUser();
        // close the database
        this.dbm.close();
    }

    private void registerTask() {
        // start generator task
        GeneratorTask.start(this.generatorManager);
        // start auto-save task
        this.generatorManager.startAutosaveTask();
        // corruption task
        CorruptionTask.start(this.generatorManager);
        // notify task
        NotifyTask.start(this.generatorManager);
    }

    private void registerHook() {
        PluginManager pm = Bukkit.getPluginManager();
        // papi hook
        if (pm.getPlugin("PlaceholderAPI") != null) {
            Logger.info("Found PlaceholderAPI! Registering hook...");
            new GensExpansion(this.generatorManager, this.userManager).register();
        }
        if (pm.getPlugin("SuperiorSkyblock2") != null) {
            Logger.info("Found SuperiorSkyblock2! Registering hook...");
            pm.registerEvents(new SSB2Listener(this.generatorManager, this.refundManager), this);
        }
        if (pm.getPlugin("BentoBox") != null) {
            Logger.info("Found BentoBox! Registering hook...");
            pm.registerEvents(new BentoListener(this.generatorManager, this.refundManager), this);
        }
        if (pm.getPlugin("HolographicDisplays") != null) {
            Logger.info("Found HolographicDisplays! Registering hook...");
        }
        if (pm.getPlugin("DecentHolograms") != null) {
            Logger.info("Found DecentHolograms! Registering hook...");
        }
        // register bstats metrics hook
        this.connectMetrics();
    }

    private void updateConfig() {
        File configFile = new File(this.getDataFolder(), "config.yml");

        try {
            ConfigUpdater.update(this, "config.yml", configFile, new ArrayList<>());
        } catch (IOException ex) {
            Logger.severe("Failed to update the config.yml!");
            ex.printStackTrace();
        }

        // reload the config afterwards
        Config.reload();
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();

        // register events
        pm.registerEvents(new GeneratorListener(this.generatorManager, this.userManager), this);
        pm.registerEvents(this.refundManager, this);
        // register the gui lib
        SimpleInventoryManager.register(this);
    }

    private void registerCommands() {
        // main commands
        MainCommand command = new MainCommand(this.generatorManager, this.userManager);
        this.getCommand("nextgens").setExecutor(command);
        this.getCommand("nextgens").setTabCompleter(command);
        // sell commands
        this.getCommand("sell").setExecutor(new SellCommand(this.generatorManager));
        // shop command
        this.getCommand("genshop").setExecutor(new ShopCommand(this.generatorManager));
        // pickup command
        this.getCommand("pickupgens").setExecutor(new PickupCommand(this.generatorManager));
    }

    private void connectMetrics() {
        // connect to bstats metrics
        Metrics metrics = new Metrics(this, BSTATS_ID);
        FileConfiguration config = Config.CONFIG.getConfig();
        // add custom charts
        metrics.addCustomChart(new SimplePie("corruption", () -> this.yesOrNo(config.getBoolean("corruption.enabled"))));
        metrics.addCustomChart(new SimplePie("auto_save", () -> this.yesOrNo(config.getBoolean("auto-save.enabled"))));
        metrics.addCustomChart(new SimplePie("place_permission", () -> this.yesOrNo(config.getBoolean("place-permission"))));
        metrics.addCustomChart(new SimplePie("online_only", () -> this.yesOrNo(config.getBoolean("online-only"))));
        metrics.addCustomChart(new SimplePie("anti_explosion", () -> this.yesOrNo(config.getBoolean("anti-explosion"))));
        metrics.addCustomChart(new SimplePie("disable_drop_place", () -> this.yesOrNo(config.getBoolean("disable-drop-place"))));
        metrics.addCustomChart(new SimplePie("shift_pickup", () -> this.yesOrNo(config.getBoolean("shift-pickup"))));
        metrics.addCustomChart(new SimplePie("island_pickup", () -> this.yesOrNo(config.getBoolean("island-pickup"))));
        metrics.addCustomChart(new SimplePie("upgrade_gui", () -> this.yesOrNo(config.getBoolean("upgrade-gui"))));
        metrics.addCustomChart(new SimplePie("close_on_purchase", () -> this.yesOrNo(config.getBoolean("close-on-purchase"))));
        metrics.addCustomChart(new SimplePie("close_on_no_money", () -> this.yesOrNo(config.getBoolean("close-on-no-money"))));
        metrics.addCustomChart(new SimplePie("hook_shopguiplus", () -> this.yesOrNo(config.getBoolean("sell-options.hook_shopguiplus"))));
        metrics.addCustomChart(new SimplePie("drop_on_break", () -> this.yesOrNo(config.getBoolean("drop-on-break"))));
        metrics.addCustomChart(new SimplePie("broken_pickup", () -> this.yesOrNo(config.getBoolean("broken-pickup"))));

    }

    private String yesOrNo(boolean status) {
        if (status) {
            return "yes";
        } else {
            return "no";
        }
    }

    public static NextGens getInstance() {
        return instance;
    }

}
