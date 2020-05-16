package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.ImmutableMap;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.shops.ParseContext;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class ActionCustom extends Action {
    List<String> commands;
    int delayInTicks = 0;

    public ActionCustom(Config yaml) {
        super(yaml);
        Optional<Object> optional = yaml.find("commands", List.class, String.class);
        if (optional.isPresent()) {
            Object comms = optional.get();
            if (comms instanceof List) {
                commands = ((List<Object>) comms).stream().map(Object::toString).collect(Collectors.toList());
            } else if (comms instanceof String) {
                commands = Collections.singletonList((String) comms);
            }
        } else {
            commands = Collections.emptyList();
        }
        yaml.find("delay", Number.class).ifPresent(num -> {
            WorstShop.get().logger.warning("'delay' on commands is deprecated. Please use 'preset: delay' instead");
            WorstShop.get().logger.warning("Parse context: " + ParseContext.getHierarchy());
            delayInTicks = num.intValue();
        });
    }

    public ActionCustom(List<String> commands) {
        super(null);
        this.commands = commands;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (delayInTicks > 0)
            Bukkit.getScheduler().runTaskLater(WorstShop.get(), ()->commands.forEach(s -> parseCommand((Player)e.getWhoClicked(), s)), delayInTicks);
        else
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
            .put("chat:", (p, in)->
                    p.spigot().sendMessage(ConfigHelper.parseComponentString(in.trim()))
            )
            .put("actionbar:", (p, in)->
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, ConfigHelper.parseComponentString(in.trim()))
            )
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
