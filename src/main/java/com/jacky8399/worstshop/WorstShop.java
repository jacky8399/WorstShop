package com.jacky8399.worstshop;

import co.aikar.commands.PaperCommandManager;
import com.jacky8399.worstshop.commands.Commands;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.shops.ShopManager;
import fr.minuskube.inv.InventoryManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorstShop extends JavaPlugin {

    public Logger logger;
    public InventoryManager inventories;
    public PaperCommandManager commands;
    public RegisteredServiceProvider<Economy> economy;
    public PlayerPoints playerPoints;
    public boolean placeholderAPI = false;
    public LuckPerms permissions;

    public FileConfiguration config;

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;

        logger = getLogger();

        logger.setLevel(Level.FINEST);

        logger.info(ChatColor.GOLD + "Enabling WorstShop");

        // check is Paper
        PaperHelper.checkIsPaper();
        if (!PaperHelper.isPaper) {
            logger.info(ChatColor.YELLOW + "Not using PaperSpigot. Using alternative methods.");
        }

        // setup vault dependencies
        economy = getServer().getServicesManager().getRegistration(Economy.class);

        // setup LuckPerms dependency
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                permissions = LuckPermsProvider.get();
            } catch (NoClassDefFoundError ex) {
                logger.severe("LuckPermsProvider not found. Are you using LuckPerms 5.0?");
                logger.severe(ex.toString());
                permissions = null;
            }
        }

        // setup playerpoints dependency
        if (getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            playerPoints = (PlayerPoints) getServer().getPluginManager().getPlugin("PlayerPoints");
        }

        // setup placeholderapi dependency
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderAPI = true;
            logger.info("Enabled PlaceholderAPI support");
        }

        // load config
        config = getConfig();

        // init invs
        inventories = new InventoryManager(this);
        inventories.init();
        // patch middle click behaviour
        Bukkit.getPluginManager().registerEvents(new InvListenerPatch(), this);

        // init commands
        commands = new PaperCommandManager(this);
        Commands.initCommands(this);

        ShopManager.loadShops();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        logger.info(ChatColor.GOLD + "Disabling WorstShop");

        // Unschedule all events
        Bukkit.getScheduler().cancelTasks(this);

        // Unload locales
        I18n.shutdown();

        // Remove shops
        ShopManager.cleanUp();
        ShopManager.saveDiscounts();
    }

    private static WorstShop plugin;
    public static WorstShop get() {
        return plugin;
    }

    public static class InvListenerPatch implements Listener {
        public InvListenerPatch() {
            try {
                ACTION_FIELD = InventoryClickEvent.class.getDeclaredField("action");
                ACTION_FIELD.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                throw new RuntimeException(ex);
            }
            MANAGER = WorstShop.get().inventories;
        }

        // brutally override action using reflection
        final Field ACTION_FIELD;
        final InventoryManager MANAGER;

        @EventHandler(priority = EventPriority.LOWEST)
        public void onInventoryClick(InventoryClickEvent e) {
            Player p = (Player) e.getWhoClicked();
            if (p.getGameMode() != GameMode.CREATIVE) {
                if (e.getClick() == ClickType.MIDDLE && e.getAction() == InventoryAction.NOTHING) {
                    if (MANAGER.getInventory(p).isPresent() &&
                            e.getClickedInventory() == p.getOpenInventory().getTopInventory()) {
                        // do override if player is in SmartInventory
                        try {
                            ACTION_FIELD.set(e, InventoryAction.CLONE_STACK);
                        } catch (IllegalAccessException ex) {
                            throw new Error(ex);
                        }
                    }
                }
            }
        }
    }
}
