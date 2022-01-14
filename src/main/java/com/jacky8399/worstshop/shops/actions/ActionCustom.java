package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.ImmutableMap;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.InventoryUtils;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.shops.ParseContext;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class ActionCustom extends Action {
    public final List<String> commands;
    public final int delayInTicks;

    public ActionCustom(Config yaml) {
        super(yaml);
        Optional<Object> optional = yaml.find("commands", List.class, String.class);
        if (optional.isPresent()) {
            Object comms = optional.get();
            if (comms instanceof List) {
                commands = ((List<?>) comms).stream().map(Object::toString).toList();
            } else {
                commands = Collections.singletonList(comms.toString());
            }
        } else {
            commands = Collections.emptyList();
        }
        delayInTicks = yaml.find("delay", Integer.class).map(num -> {
            Logger logger = WorstShop.get().logger;
            logger.warning("'delay' on commands is deprecated. Please use 'preset: delay' instead");
            logger.warning("Offending action: " + ParseContext.getHierarchy());
            logger.warning("Equivalent code:\n" +
                    "  preset: delay\n" +
                    "  actions:\n" +
                    "  - commands: ...");
            return num;
        }).orElse(0);
    }

    public ActionCustom(List<String> commands) {
        super(null);
        this.commands = commands;
        this.delayInTicks = 0;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        if (delayInTicks > 0) {
            Bukkit.getScheduler().runTaskLater(WorstShop.get(),
                    () -> commands.forEach(s -> parseCommand((Player) e.getWhoClicked(), s)), delayInTicks);
        } else {
            // set noParent to improve compatibility with commands that open inventories
            InventoryContents contents = InventoryUtils.getContents(player);
            boolean oldNoParent = contents != null ? contents.property(InventoryUtils.PROPERTY_NO_PARENT, false) : false;
            if (contents != null)
                contents.setProperty(InventoryUtils.PROPERTY_NO_PARENT, true);
            commands.forEach(s -> parseCommand((Player) e.getWhoClicked(), s));
            if (contents != null)
                contents.setProperty(InventoryUtils.PROPERTY_NO_PARENT, oldNoParent);
        }
    }

    public static void parseCommand(Player player, String in) {
        // do replacement first
        in = I18n.doPlaceholders(player, in);
        int idx;
        if ((idx = in.indexOf(':')) > -1) {
            String prefix = in.substring(0, idx + 1);
            BiConsumer<Player, String> func = PREDEFINED_FUNCTIONS.get(prefix);
            if (func != null) {
                String cmd = in.substring(idx + 1);
                func.accept(player, cmd);
                return;
            }
        }
        // else run command as player
        player.chat("/" + in.trim());
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        // fix delay for users
        if (delayInTicks > 0) {
            new ActionDelay(delayInTicks, Collections.singletonList(new ActionCustom(commands))).toMap(map);
        } else {
            map.put("commands", commands);
        }
        return map;
    }

    public static final ImmutableMap<String, BiConsumer<Player, String>> PREDEFINED_FUNCTIONS = ImmutableMap.<String, BiConsumer<Player, String>>builder()
            .put("chat:", (p, in)->
                    p.spigot().sendMessage(ConfigHelper.parseComponentString(in.trim()))
            )
            .put("actionbar:", (p, in)->
                    PaperHelper.sendActionBar(p, ConfigHelper.parseComponentString(in.trim()))
            )
            .put("op:", (p, in)->{
                boolean oldOp = p.isOp();
                if (!oldOp)
                    p.setOp(true);
                p.chat("/" + in.trim());
                p.setOp(oldOp);
            })
            .put("chatas:", (p, in) -> p.chat(in.trim()))
            .put("console:", (p, in)-> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), in.trim()))
            .build();

}
