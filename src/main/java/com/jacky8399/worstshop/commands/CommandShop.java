package com.jacky8399.worstshop.commands;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.helper.PermStringHelper;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
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
            Shop shop = ShopManager.SHOPS.get(arg);
            if (shop != null && shop.checkPlayerPerms(ctx.getIssuer().getIssuer())) {
                return shop;
            }
            throw new InvalidCommandArgument(I18n.translate("worstshop.errors.invalid-shop", arg));
        });

        manager.getCommandCompletions().registerCompletion("shops", ctx-> ShopManager.SHOPS.keySet().stream()
                .filter(shop->shop.startsWith(ctx.getInput())) // filter once to prevent unnecessary perm checks
                .filter(shop->ShopManager.checkPermsOnly(ctx.getIssuer().getIssuer(), shop))
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

    @Subcommand("version|ver|info")
    @CommandPermission("worstshop.version")
    public void showVersion(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "You are running WorstShop " + WorstShop.get().getDescription().getVersion());
        // Statistics
        sender.sendMessage(ChatColor.GREEN + "Shops loaded: " + ChatColor.YELLOW + ShopManager.SHOPS.size());
        sender.sendMessage(ChatColor.GREEN + "Total shop elements: " + ChatColor.YELLOW + ShopManager.SHOPS.values()
                .stream().mapToInt(shop -> shop.staticElements.size() + shop.dynamicElements.size()).sum());
    }

    private static String replaceColor(String str) {
        return str.replace(ChatColor.COLOR_CHAR, '&');
    }

    @Subcommand("inspectitem|itemmeta|item|meta")
    @CommandPermission("worstshop.inspectitem")
    public void showItem(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        List<String> serialized = Lists.newArrayList();
        serialized.add("item: " + stack.getType().name().toLowerCase().replace('_', ' '));
        serialized.add("count: " + stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            if (damageable.hasDamage()) {
                serialized.add("damage: " + damageable.getDamage());
            }
        }
        if (meta.hasCustomModelData()) {
            serialized.add("custom-model-data: " + meta.getCustomModelData());
        }
        if (meta.hasDisplayName()) {
            serialized.add("name: " + replaceColor(meta.getDisplayName()));
        }
        if (meta.hasLocalizedName()) {
            serialized.add("loc-name: " + meta.getLocalizedName());
        }
        if (meta.hasLore()) {
            serialized.add("lore:");
            meta.getLore().forEach(lore -> serialized.add("- " + replaceColor(lore)));
        }
        if (meta instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) meta;
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(skullMeta);
            serialized.add("skull: " + profile.getUUID());
        }

        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Click to copy line", net.md_5.bungee.api.ChatColor.WHITE));
        player.sendMessage(new ComponentBuilder("Item: ").color(net.md_5.bungee.api.ChatColor.YELLOW)
                .append("[Copy all]").color(net.md_5.bungee.api.ChatColor.GREEN)
                .event(hoverEvent).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, String.join("\n", serialized)))
                .create());
        for (String line : serialized) {
            player.sendMessage(new ComponentBuilder(line).color(net.md_5.bungee.api.ChatColor.WHITE)
                    .event(hoverEvent).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, line))
                    .create());
        }
        player.sendMessage(ChatColor.YELLOW + "For more complex items (e.g. plugin items), use the following:");
        String metaStr = "item-meta: " + StaticShopElement.serializeBase64ItemMeta(meta);
        player.sendMessage(new ComponentBuilder(metaStr).color(net.md_5.bungee.api.ChatColor.WHITE)
                .event(hoverEvent).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, metaStr))
                .create()
        );
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
        if (shop != null && shop.canPlayerView(player))
            shop.getInventory(player).open(player);
        else {
            Shop defaultShop = ShopManager.SHOPS.get("default");
            if (defaultShop != null && defaultShop.canPlayerView(player)) {
                defaultShop.getInventory(player).open(player);
            }
        }
    }
}
