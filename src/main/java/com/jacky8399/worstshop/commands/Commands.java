package com.jacky8399.worstshop.commands;

import co.aikar.commands.BukkitLocales;
import co.aikar.commands.PaperCommandManager;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;

public class Commands {
    public static void initCommands(WorstShop plugin) {
        PaperCommandManager manager = plugin.commands;
        // load locales
        loadLocales(plugin, manager);

        manager.registerDependency(BukkitLocales.class, manager.getLocales());
        manager.registerDependency(PaperCommandManager.class, manager);

        // enable help
        manager.enableUnstableAPI("help");

        // register commands
        manager.registerCommand(new CommandShop(plugin, manager));
        manager.registerCommand(new CommandSell(plugin, manager));
    }

    private static void loadLocales(WorstShop plugin, PaperCommandManager manager) {
        I18n.loadLang();
    }
}
