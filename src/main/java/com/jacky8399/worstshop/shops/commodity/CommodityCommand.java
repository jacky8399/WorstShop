package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CommodityCommand extends Commodity implements IUnaffordableCommodity {

    public enum CommandInvocationMethod {
        PLAYER, PLAYER_OP, CONSOLE
    }

    List<String> commands;
    CommandInvocationMethod method;
    int multiplier;

    public CommodityCommand(Config config) {
        if (config.has("commands")) {
            commands = new ArrayList<>(config.getList("commands", String.class));
        } else {
            commands = Collections.singletonList(config.get("command", String.class));
        }
        method = config.find("method", CommandInvocationMethod.class).orElse(CommandInvocationMethod.PLAYER);
        multiplier = config.find("multiplier", Integer.class).orElse(1);
    }

    public CommodityCommand(List<String> commands, CommandInvocationMethod method, int multiplier) {
        this.commands = new ArrayList<>(commands);
        this.method = method;
        this.multiplier = multiplier;
    }

    @Override
    public boolean canAfford(Player player) {
        return false;
    }

    @Override
    public String getPlayerResult(@Nullable Player player, TransactionType position) {
        return I18n.translate("worstshop.messages.shops.wants.command", commands.size());
    }

    @Override
    public double grantOrRefund(Player player) {
        PlaceholderContext context = PlaceholderContext.guessContext(player);
        for (int i = 0; i < multiplier; i++) {
            for (String command : commands) {
                String actualCommand = Placeholders.setPlaceholders(command, context);
                doCommandOnce(player, actualCommand);
            }
        }
        return 0;
    }

    @Override
    public void deduct(Player player) {
        // no-op
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
        return pos.createElement(ItemBuilder.of(Material.COMMAND_BLOCK)
                .name(I18n.translate("worstshop.messages.shops.wants.command", commands.size()))
                .build());
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityCommand(commands, method, (int) (this.multiplier * multiplier));
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        super.toMap(map);
        map.put("preset", "command");
        if (commands.size() == 1)
            map.put("command", commands.get(0));
        else
            map.put("commands", commands);
        map.put("multiplier", multiplier);
        map.put("method", method.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(commands, method, multiplier);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommodityCommand other))
            return false;
        return other.multiplier == multiplier && other.commands.equals(commands) && other.method == method;
    }

    @Override
    public String toString() {
        return "[runs " + commands.size() + " commands as " +
                (method == CommandInvocationMethod.PLAYER_OP ? "PLAYER (temporarily opped)" : method.name()) + "]";
    }
}
