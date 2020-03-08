package com.jacky8399.worstshop.commands;

import co.aikar.commands.BukkitLocales;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.Dependency;
import com.jacky8399.worstshop.WorstShop;
import org.bukkit.ChatColor;

public class BaseCommand extends co.aikar.commands.BaseCommand {
    public BaseCommand(WorstShop plugin, PaperCommandManager manager) {
        setExceptionHandler((command, registeredCommand, sender, args, t) -> {
            sender.sendMessage(ChatColor.DARK_RED + "Fatal exception while running /" + command.getName() + ": " + t.toString());
            t.printStackTrace();
            return true;
        });
    }
    @Dependency
    PaperCommandManager manager;
    @Dependency
    BukkitLocales locales;
}
