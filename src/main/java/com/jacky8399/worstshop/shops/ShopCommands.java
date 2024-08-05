package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.WorstShop;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ShopCommands {

    public static List<ShopAliasCommand> registeredCommands = Lists.newArrayList();
    public static void loadAliases() {
        CommandMap map = Bukkit.getCommandMap();
        for (Shop shop : ShopManager.SHOPS.values()) {
            if (shop.aliases != null) {
                ShopAliasCommand command = new ShopAliasCommand(shop.id, shop.aliases, shop.aliasesIgnorePermission);
                if (!register(map, command)) {
                    WorstShop.get().logger.warning("Failed to register shop alias command /" + shop.aliases.getFirst() + " for shop " + shop.id);
                }
                registeredCommands.add(command);
            }
        }
        // reload aliases for players
        Bukkit.getScheduler().runTask(WorstShop.get(), () -> Bukkit.getOnlinePlayers().forEach(Player::updateCommands));
    }

    private static boolean register(CommandMap map, ShopAliasCommand cmd) {
        cmd.register(map);
        return map.register("worstshop", cmd);
    }

    public static void removeAliases() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> knownCommands = map.getKnownCommands();
        int[] removed = {0};
        // strangely Paper only redirects Iterator#remove() on values()
        knownCommands.values().removeIf(command -> {
            if (command instanceof ShopAliasCommand) {
                removed[0]++;
                return true;
            }
            return false;
        });
        WorstShop.get().logger.info("Removed " + removed[0] + " aliases");
    }

    public static class ShopAliasCommand extends BukkitCommand {
        private final String shopName;

        protected ShopAliasCommand(String shopName, List<String> aliases, boolean ignorePermission) {
            super(aliases.getFirst()); // pop first arg
            this.shopName = shopName;
            this.description = I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.alias.description", shopName);
            ArrayList<String> popped = new ArrayList<>(aliases);
            this.usageMessage = "/" + popped.removeFirst();
            this.setAliases(popped);
            if (!ignorePermission)
                this.setPermission("worstshop.shops." + shopName);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (sender instanceof Player player) {
                ShopManager.getShop(shopName)
                        .filter(shop -> shop.canPlayerView(player, true)) // check conditions
                        .ifPresentOrElse(
                                shop -> shop.getInventory(player).open(player),
                                () -> sender.sendMessage(ChatColor.RED + "Shop " + shopName + " not found")
                        );
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be run by a player!");
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
