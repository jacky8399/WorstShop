package com.jacky8399.worstshop.commands;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.PermStringHelper;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Predicate;
import java.util.stream.Collectors;

@CommandAlias("worstshop|shop")
@CommandPermission("worstshop.shop")
public class CommandShop extends BaseCommand {
    public CommandShop(WorstShop plugin, PaperCommandManager manager) {
        super(plugin, manager);
        // register shops autocompletion
        manager.getCommandContexts().registerContext(Shop.class, ctx->{
            String arg = ctx.getFirstArg();
            if (ShopManager.SHOPS.containsKey(arg) && ShopManager.checkPerms(ctx.getIssuer().getIssuer(), arg)) {
                return ShopManager.SHOPS.get(arg);
            }
            throw new InvalidCommandArgument(I18n.translate("worstshop.errors.invalid-shop", arg));
        });

        manager.getCommandCompletions().registerCompletion("shops", ctx-> ShopManager.SHOPS.keySet().stream()
                .filter(shop->shop.startsWith(ctx.getInput())) // filter once to prevent unnecessary perm checks
                .filter(shop->ShopManager.checkPerms(ctx.getIssuer().getIssuer(), shop))
                .collect(Collectors.toList()));

        manager.getCommandCompletions().setDefaultCompletion("shops", Shop.class);
    }

    @Subcommand("reload")
    @CommandPermission("worstshop.reload")
    public void reload(CommandIssuer issuer) {
        // close all shops
        WorstShop plugin = WorstShop.get();

        ShopManager.closeAllShops();

        plugin.reloadConfig();
        ShopManager.loadShops();
        I18n.loadLang();


        issuer.sendMessage(I18n.translate("worstshop.messages.config-reloaded"));
    }

    @Subcommand("version")
    @CommandPermission("worstshop.version")
    public void showVersion(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "You are running WorstShop " + WorstShop.get().getDescription().getVersion());
    }

    @Subcommand("open")
    @CommandCompletion("* *")
    @CommandPermission("worstshop.shop.open.others")
    public void openOthers(CommandSender sender, Shop shop, OnlinePlayer player) {
        shop.getInventory(player.player).open(player.player);
    }

    @Subcommand("testpermission")
    @CommandCompletion("* @nothing")
    @CommandPermission("worstshop.shop.testpermission")
    public void testPermString(CommandSender sender, OnlinePlayer player, String permString) {
        try {
            Predicate<Player> test = PermStringHelper.parsePermString(permString);
            boolean result = test.test(player.player);
            sender.sendMessage(ChatColor.WHITE + "Perm string " + ChatColor.YELLOW + permString + ChatColor.WHITE + "(" +
                    test.toString() + ") evaluated to " +
                    (result ? ChatColor.GREEN : ChatColor.RED) + result + ChatColor.WHITE + " for " + player.player.getName());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCommandArgument(ex.getMessage());
        }
    }

    @Default
    @Subcommand("open")
    @CommandPermission("worstshop.shop.open")
    @CommandCompletion("*")
    public void open(Player player, @Optional Shop shop) {
        if (shop != null)
            shop.getInventory(player).open(player);
        else {
            if (ShopManager.SHOPS.containsKey("default") && ShopManager.checkPerms(player, "default")) {
                ShopManager.SHOPS.get("default").getInventory(player).open(player);
            }
        }
    }
}
