package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ActionCustom extends ShopAction {
    List<String> commands;

    public ActionCustom(Map<String, Object> yaml) {
        super(yaml);
        Object comms = yaml.get("commands");
        if (comms instanceof List) {
            commands = ((List<Object>) comms).stream().map(Object::toString).collect(Collectors.toList());
        } else if (comms instanceof String) {
            commands = Collections.singletonList((String) comms);
        } else {
            commands = Collections.emptyList();
        }
    }

    public ActionCustom(List<String> commands) {
        super(null);
        this.commands = commands;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        super.onClick(e);
        commands.forEach(s -> parseCommand((Player)e.getWhoClicked(), s));
    }

    public static void parseCommand(Player player, String in) {
        // do replacement first
        in = in.replace("{player}", player.getName());
        int idx;
        if ((idx = in.indexOf(':')) > -1) {
            String prefix = in.substring(0, idx + 1);
            BiConsumer<Player, String> func = PREDIFINED_FUNCTIONS.get(prefix);
            if (func != null) {
                String cmd = in.substring(idx + 1);
                func.accept(player, cmd);
                return;
            }
        }
        // else run command as player
        player.chat("/" + in.trim());
    }

    public static final ImmutableMap<String, BiConsumer<Player, String>> PREDIFINED_FUNCTIONS = ImmutableMap.<String, BiConsumer<Player, String>>builder()
            .put("chat:", (p, in)->p.sendMessage(ChatColor.translateAlternateColorCodes('&', in.trim())))
            .put("actionbar:", (p, in)->p.sendActionBar(ChatColor.translateAlternateColorCodes('&', in.trim())))
            .put("op:", (p, in)->{
                boolean oldOp = p.isOp();
                if (!oldOp)
                    p.setOp(true);
                p.chat("/" + in.trim());
                p.setOp(oldOp);
            })
            .put("chatas:", (p, in)->p.chat(in.trim()))
            .put("console:", (p, in)-> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), in.trim()))
            .build();

}
