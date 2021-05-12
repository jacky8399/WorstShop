package com.jacky8399.worstshop.commands;

import co.aikar.commands.PaperCommandManager;
import com.jacky8399.worstshop.WorstShop;
import net.md_5.bungee.api.ChatColor;

public class BaseCommand extends co.aikar.commands.BaseCommand {
    public BaseCommand() {
        manager = WorstShop.get().commands;

        setExceptionHandler((command, registeredCommand, sender, args, t) -> {
            sender.sendMessage(ChatColor.DARK_RED + "Fatal exception while running /" + command.getName() + ": " + t.toString());
            t.printStackTrace();
            return true;
        });
    }

    PaperCommandManager manager;
}
