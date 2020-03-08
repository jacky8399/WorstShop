package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;

public class ShopWantsCommand extends ShopWantsCustomizable {

    public enum CommandInvocationMethod {
        PLAYER, PLAYER_OP, CONSOLE
    }

    String command;
    CommandInvocationMethod method = CommandInvocationMethod.PLAYER;
    int multiplier = 1;

    public ShopWantsCommand(Map<String, Object> yaml) {
        super(yaml);
        command = (String) yaml.get("command");
        if (yaml.containsKey("method")) {
            method = ConfigHelper.parseEnum((String) yaml.get("method"), CommandInvocationMethod.class);
        }
        if (yaml.containsKey("multiplier")) {
            multiplier = (int) yaml.get("multiplier");
        }
    }

    public ShopWantsCommand(@Nullable ShopWantsCommand self, String command, CommandInvocationMethod method, int multiplier) {
        super(self);
        this.command = command;
        this.method = method;
        this.multiplier = multiplier;
    }

    @Override
    public boolean canAfford(Player player) {
        return false;
    }

    @Override
    public void grant(Player player) {
        String actualCommand = command.replace("{player}", player.getName());
        for (int i = 0; i < multiplier; i++) {
            doCommandOnce(player, actualCommand);
        }
    }

    public void doCommandOnce(Player player, String command) {

        switch (method) {
            case CONSOLE:
                if (command.startsWith("/"))
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                break;
            case PLAYER_OP:
                boolean playerWasOp = player.isOp();
                if (!playerWasOp) {
                    player.setOp(true);
                }
                player.chat(command);
                player.setOp(playerWasOp);
                break;
            case PLAYER:
                player.chat(command);
                break;
        }
    }

    @Override
    public ItemStack getDefaultStack() {
        return ItemBuilder.of(Material.COMMAND_BLOCK).name(ChatColor.GREEN + command).build();
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsCommand(this, command, method, (int) (this.multiplier * multiplier));
    }
}
