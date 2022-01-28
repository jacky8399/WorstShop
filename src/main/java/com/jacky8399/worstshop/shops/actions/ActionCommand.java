package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.ImmutableMap;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class ActionCommand extends Action {
    public final List<String> commands;

    public ActionCommand(Config yaml) {
        super(yaml);
        Optional<String> command = yaml.find("command", String.class);
        commands = command.map(Collections::singletonList) // single command
                .or(() -> yaml.findList("commands", String.class)) // list of commands
                .orElseThrow(() -> new ConfigException("Must specify command", yaml));
        yaml.find("delay", Integer.class).ifPresent(num ->
                WorstShop.get().logger.warning("'delay' on commands is no longer supported. It has been ignored."));
    }

    public ActionCommand(List<String> commands) {
        super(null);
        this.commands = commands;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        // set noParent to improve compatibility with commands that open inventories
        InventoryContents contents = InventoryUtils.getContents(player);
        boolean oldNoParent = contents != null ? contents.property(InventoryUtils.PROPERTY_NO_PARENT, false) : false;
        if (contents != null)
            contents.setProperty(InventoryUtils.PROPERTY_NO_PARENT, true);
        commands.forEach(s -> parseCommand(player, s));
        if (contents != null)
            contents.setProperty(InventoryUtils.PROPERTY_NO_PARENT, oldNoParent);
    }

    public static void parseCommand(Player player, String in) {
        // do replacement first
        PlaceholderContext context = PlaceholderContext.guessContext(player);
        in = Placeholders.setPlaceholders(in, context);
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
        if (commands.size() == 1)
            map.put("command", commands.get(0));
        else
            map.put("commands", commands);
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
