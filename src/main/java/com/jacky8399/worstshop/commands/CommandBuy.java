package com.jacky8399.worstshop.commands;

import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.CommandAlias;
import com.jacky8399.worstshop.WorstShop;

@CommandAlias("buy")
public class CommandBuy extends BaseCommand {
    public CommandBuy(WorstShop plugin, PaperCommandManager manager) {
        super(plugin, manager);
    }
}
