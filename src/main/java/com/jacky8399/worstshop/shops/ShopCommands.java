package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.PaperHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ShopCommands {

    public static List<ShopAliasCommand> registeredCommands = Lists.newArrayList();
    public static void loadAliases() {
        CommandMap map = PaperHelper.getCommandMap();
        for (Shop shop : ShopManager.SHOPS.values()) {
            if (shop.aliases != null) {
                ShopAliasCommand command = new ShopAliasCommand(shop.id, shop.aliases, shop.aliasesIgnorePermission);
                register(map, command);
                registeredCommands.add(command);
            }
        }
        // reload aliases for players
        Bukkit.getScheduler().runTask(WorstShop.get(), () -> Bukkit.getOnlinePlayers().forEach(Player::updateCommands));
    }

    private static void register(CommandMap map, ShopAliasCommand cmd) {
        cmd.register(map);
        map.register("worstshop", cmd);
    }

    public static void removeAliases() {
        try {
            CommandMap map = PaperHelper.getCommandMap();
            Map<String, Command> knownCommands = PaperHelper.getKnownCommands(map);
            if (knownCommands == null)
                return;
            for (Iterator<Map.Entry<String, Command>> it = knownCommands.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Command> entry = it.next();
                if (entry.getValue() instanceof ShopAliasCommand) {
                    ShopAliasCommand c = (ShopAliasCommand) entry.getValue();
                    c.unregister(map);
                    it.remove();
                }
            }
        } catch (Exception ex) {
            // ignore
        }
    }

    public static class ShopAliasCommand extends BukkitCommand {

        private String shopName;

        protected ShopAliasCommand(String shopName, List<String> aliases, boolean ignorePermission) {
            super(aliases.remove(0)); // pop first arg
            this.shopName = shopName;
            this.description = I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.alias.description", shopName);
            this.usageMessage = "/" + shopName;
            this.setAliases(aliases);
            if (!ignorePermission)
                this.setPermission("worstshop.shops." + shopName);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (sender instanceof Player) {
                Player player = (Player)sender;
                ShopManager.getShop(shopName)
                        .filter(shop -> shop.canPlayerView(player, true)) // check conditions
                        .ifPresent(shop -> shop.getInventory(player).open(player));
            }
            return true;
        }

        @NotNull
        @Override
        public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args, @Nullable Location location) throws IllegalArgumentException {
            return Collections.emptyList();
        }
    }

}
