package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ShopWantsCommand extends ShopWants implements INeverAffordableShopWants {

    public enum CommandInvocationMethod {
        PLAYER, PLAYER_OP, CONSOLE
    }

    String command;
    CommandInvocationMethod method;
    int multiplier;

    public ShopWantsCommand(Config config) {
        command = config.get("command", String.class);
        method = config.find("method", CommandInvocationMethod.class).orElse(CommandInvocationMethod.PLAYER);
        multiplier = config.find("multiplier", Integer.class).orElse(1);
    }

    public ShopWantsCommand(String command, CommandInvocationMethod method, int multiplier) {
        this.command = command;
        this.method = method;
        this.multiplier = multiplier;
    }

    @Override
    public boolean canAfford(Player player) {
        return false;
    }

    @Override
    public String getPlayerResult(@Nullable Player player, TransactionType position) {
        return "[command]";
    }

    @Override
    public double grantOrRefund(Player player) {
        String actualCommand = command.replace("{player}", player.getName());
        for (int i = 0; i < multiplier; i++) {
            doCommandOnce(player, actualCommand);
        }
        return 0;
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
    public ShopElement createElement(TransactionType pos) {
        return pos.createElement(ItemBuilder.of(Material.COMMAND_BLOCK).name(ChatColor.GREEN + command).build());
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsCommand(command, method, (int) (this.multiplier * multiplier));
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        super.toMap(map);
        map.put("preset", "command");
        map.put("command", command);
        map.put("multiplier", multiplier);
        map.put("method", method.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, method, multiplier);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ShopWantsCommand))
            return false;
        ShopWantsCommand other = (ShopWantsCommand) obj;
        return other.multiplier == multiplier && other.command.equals(command) && other.method == method;
    }

    @Override
    public String toString() {
        return "[runs command \"" + command + "\n as " + (method == CommandInvocationMethod.PLAYER_OP ? "PLAYER (temporarily opped)" : method.name()) + "]";
    }
}
