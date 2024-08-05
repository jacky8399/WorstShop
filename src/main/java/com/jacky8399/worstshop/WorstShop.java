package com.jacky8399.worstshop;

import co.aikar.commands.PaperCommandManager;
import com.jacky8399.worstshop.commands.Commands;
import com.jacky8399.worstshop.events.Events;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.InventoryUtils;
import com.jacky8399.worstshop.helper.PlayerPurchases;
import com.jacky8399.worstshop.shops.ShopManager;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorstShop extends JavaPlugin {

    public Logger logger;
    public InventoryManager inventories;
    public PaperCommandManager commands;
    @Nullable
    public RegisteredServiceProvider<Economy> economy;
    @Nullable
    public PlayerPoints playerPoints;
    public boolean placeholderAPI = false;
    @Nullable
    public LuckPerms permissions;
    @Nullable
    public RegisteredServiceProvider<Permission> vaultPermissions;
    public RegisteredServiceProvider<Chat> vaultChat;

    public static SmartInventory.Builder buildGui(String id) {
        return SmartInventory.builder().manager(plugin.inventories).id(id).listener(new InventoryUtils());
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        PluginConfig.reload();
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;

        logger = getLogger();

        ConfigHelper.registerDefaultDeserializers();

        saveDefaultConfig();
        reloadConfig();

        PluginManager manager = Bukkit.getPluginManager();

        // setup vault dependencies
        if (manager.isPluginEnabled("Vault")) {
            economy = Bukkit.getServicesManager().getRegistration(Economy.class);
        }

        // setup LuckPerms dependency
        if (manager.isPluginEnabled("LuckPerms")) {
            try {
                permissions = LuckPermsProvider.get();
            } catch (NoClassDefFoundError ex) {
                logger.severe("LuckPermsProvider not found. Are you using LuckPerms 5.0?");
                logger.severe(ex.toString());
                permissions = null;
            }
        } else if (manager.isPluginEnabled("Vault")) {
            vaultPermissions = Bukkit.getServicesManager().getRegistration(Permission.class);
            vaultChat = Bukkit.getServicesManager().getRegistration(Chat.class);
            logger.warning("LuckPerms not found, using Vault as fallback");
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

        // init invs
        inventories = new InventoryManager(this);
        inventories.init();
        // patch middle click behaviour
        Bukkit.getPluginManager().registerEvents(new InvListenerPatch(), this);

        // init commands
        commands = new PaperCommandManager(this);
        Commands.initCommands(this);

        // listen to events
        Events.registerEvents();

        ShopManager.loadShops();

        PlayerPurchases.setupPurchaseRecorder();
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);

        // Unload locales
        I18n.shutdown();

        // Remove shops
        try {
            ShopManager.cleanUp();
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to clean up!", e);
        }
        try {
            ShopManager.saveDiscounts();
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to save discounts!", e);
        }
        try {
            PlayerPurchases.writePurchaseRecords();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write purchase records", ex);
        }
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
