package com.jacky8399.worstshop.commands;

import co.aikar.commands.BukkitLocales;
import co.aikar.commands.PaperCommandManager;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;

public class Commands {
    public static void initCommands(WorstShop plugin) {
        PaperCommandManager manager = plugin.commands;
        // load locales
        I18n.loadLang();

        manager.registerDependency(BukkitLocales.class, manager.getLocales());
        manager.registerDependency(PaperCommandManager.class, manager);

        // enable help
        manager.enableUnstableAPI("help");

        // register commands
        try {
            manager.registerCommand(new CommandShop());
            manager.registerCommand(new CommandSell());
//            manager.registerCommand(new CommandBuy());
            manager.registerCommand(new CommandLogs());
        } catch (Exception e) {
            plugin.logger.severe("Failed to register commands!");
            e.printStackTrace();
        }
    }
}
