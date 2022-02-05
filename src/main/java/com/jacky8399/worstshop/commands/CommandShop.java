package com.jacky8399.worstshop.commands;

import co.aikar.commands.CommandHelp;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.PluginConfig;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.EditorMainMenu;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopDiscount;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.commodity.CommodityItem;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionPermission;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.jacky8399.worstshop.I18n.translate;
import static net.md_5.bungee.api.ChatColor.*;

@SuppressWarnings("unused")
@CommandAlias("worstshop|shop")
@CommandPermission("worstshop.command.shop")
public class CommandShop extends BaseCommand {
    public CommandShop() {
        // register shops autocompletion
        manager.getCommandContexts().registerContext(Shop.class, ctx->{
            String arg = ctx.popFirstArg();
            Shop shop = ShopManager.SHOPS.get(arg);
            if (shop != null && shop.checkPlayerPerms(ctx.getIssuer().getIssuer())) {
                return shop;
            }
            throw new InvalidCommandArgument(translate("worstshop.errors.invalid-shop", arg));
        });

        manager.getCommandCompletions().registerCompletion("shops", ctx -> ShopManager.SHOPS.keySet().stream()
                .filter(shop->shop.startsWith(ctx.getInput())) // filter once to prevent unnecessary perm checks
                .filter(shop->ShopManager.checkPermsOnly(ctx.getIssuer().getIssuer(), shop))
                .collect(Collectors.toList()));

        manager.getCommandCompletions().setDefaultCompletion("shops", Shop.class);
    }

    @Subcommand("reload")
    @CommandPermission("worstshop.command.shop.reload")
    public void reload(CommandIssuer issuer) {
        // close all shops
        WorstShop plugin = WorstShop.get();

        ShopManager.closeAllShops();
        ShopManager.saveDiscounts();
        plugin.reloadConfig();
        ShopManager.loadShops();
        I18n.loadLang();


        issuer.sendMessage(translate("worstshop.messages.config-reloaded"));
    }

    @Subcommand("version|ver|info")
    @CommandPermission("worstshop.command.shop.version")
    public void showVersion(CommandSender sender) {
        sender.sendMessage(GREEN + "You are running WorstShop " + WorstShop.get().getDescription().getVersion());
        // Statistics
        sender.sendMessage(GREEN + "Shops loaded: " + YELLOW + ShopManager.SHOPS.size());
        sender.sendMessage(GREEN + "Total shop elements: " + YELLOW + ShopManager.SHOPS.values()
                .stream().mapToInt(shop -> shop.elements.size()).sum());
    }

    @Subcommand("discount")
    @CommandPermission("worstshop.command.shop.discount")
    public class Discount extends co.aikar.commands.BaseCommand {
        public Discount() {
            manager.getCommandCompletions().registerStaticCompletion("discount_ids", ShopDiscount.ALL_DISCOUNTS::keySet);
            manager.getCommandCompletions().registerCompletion("discount_argstr", ctx->{
               String input = ctx.getInput();
               if (input.contains("=")) {
                   // provide autocomplete based on left hand side
                   String[] split = input.split("=");
                   String left = split[0];
                   String right =  split.length > 1 ? split[1] : "";
                   return switch (left) {
                       case "shop" -> ShopManager.SHOPS.keySet().stream()
                               .filter(shopName -> shopName.startsWith(right))
                               .map(shopName -> "shop=" + shopName)
                               .collect(Collectors.toList());
                       case "material" -> Arrays.stream(Material.values())
                               .map(Enum::name)
                               .filter(mat -> mat.startsWith(right))
                               .map(mat -> "material=" + mat)
                               .collect(Collectors.toList());
                       case "player" -> Bukkit.getOnlinePlayers().stream()
                               .map(Player::getName)
                               .filter(player -> player.startsWith(right))
                               .map(player -> "player=" + player)
                               .collect(Collectors.toList());
                       default -> Collections.emptyList();
                   };
               } else {
                   return Arrays.asList("shop=", "material=", "player=", "permission=");
               }
            });
        }

        private BaseComponent[] stringifyDiscount(ShopDiscount.Entry discount, boolean putDetailsInHover) {
            boolean expires = discount.expiry() != null;
            ComponentBuilder detailBuilder = new ComponentBuilder("Discount ID: ").color(GREEN)
                    .append(discount.name()).color(YELLOW)
                    .append("\nExpiry: ").color(GREEN)
                    .append(!expires ? "never" :
                            discount.expiry().atOffset(OffsetDateTime.now().getOffset())
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    ).color(expires ? YELLOW : BLUE);
            if (expires)
                detailBuilder.append(" (expires in ").color(GOLD)
                        .append(DateTimeUtils.formatTime(Duration.between(LocalDateTime.now(), discount.expiry()))).color(GOLD)
                        .append(")").color(GOLD);
            detailBuilder.append("\nApplicable to:").color(GREEN);
            boolean hasCriteria = false;
            if (discount.shop() != null) {
                detailBuilder.append("\nShop: ").append(discount.shop().id);
                hasCriteria = true;
            }
            if (discount.material() != null) {
                detailBuilder.append("\nMaterial: ").append(discount.material().name());
                hasCriteria = true;
            }
            if (discount.player() != null) {
                detailBuilder.append("\nPlayer: ").append(Bukkit.getOfflinePlayer(discount.player()).getName());
                hasCriteria = true;
            }
            if (discount.permission() != null) {
                detailBuilder.append("\nPlayer w/ permission: ").append(discount.permission());
                hasCriteria = true;
            }
            if (!hasCriteria) {
                detailBuilder.append("\nEveryone");
            }
            ComponentBuilder actualComponent = new ComponentBuilder((1 - discount.percentage()) * 100 + "% discount ").color(YELLOW)
                    .append("(" + discount.name() + ")").color(AQUA);
            if (putDetailsInHover)
                actualComponent.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(detailBuilder.create())));
            else
                actualComponent.append("\n").append(detailBuilder.create());
            return actualComponent.create();
        }

        @Subcommand("create")
        @CommandPermission("worstshop.command.shop.discount.create")
        @CommandCompletion("@nothing permanent|1h|6h|12h|1d|7d|30d 0.5|0.7|0.9 @discount_argstr")
        public void createDiscount(CommandSender sender, String name, String expiry, double discount, String argString) {
            String[] args = argString.split(" ");
            LocalDateTime expiryTime = expiry.equals("permanent") ? null : LocalDateTime.now().plus(DateTimeUtils.parseTimeStr(expiry));
            ShopReference shop = null;
            Material material = null;
            UUID player = null;
            String permission = null;
            for (String str : args) {
                String[] strArgs = str.split("=");
                String specifier = strArgs[0];
                String value = strArgs[1];
                switch (specifier) {
                    case "shop" -> shop = ShopReference.of(value);
                    case "player" -> {
                        try {
                            player = UUID.fromString(value);
                        } catch (IllegalArgumentException e) {
                            Player playerObj = Bukkit.getPlayer(value);
                            if (playerObj == null) {
                                throw new InvalidCommandArgument(value + " is an online player/valid UUID");
                            }
                            player = playerObj.getUniqueId();
                        }
                    }
                    case "material" -> {
                        material = Material.getMaterial(value);
                        if (material == null || ItemUtils.isAir(material)) {
                            throw new InvalidCommandArgument("Invalid material " + value);
                        }
                    }
                    case "permission" -> permission = value;
                    default -> throw new InvalidCommandArgument("Unknown argument " + specifier);
                }
            }

            ShopDiscount.Entry entry = new ShopDiscount.Entry(name, expiryTime, discount,
                    shop, material, player, permission);
            ShopDiscount.addDiscountEntry(entry);
            sender.spigot().sendMessage(new ComponentBuilder("Added new ").color(GREEN)
                    .append(stringifyDiscount(entry, sender instanceof Player)).create());
        }

        @Subcommand("list")
        @CommandPermission("worstshop.command.shop.discount.list")
        public void listDiscounts(CommandSender sender) {
            boolean shouldPutDetailsInHover = sender instanceof Player;
            sender.sendMessage(GREEN + "Discounts:");
            ShopDiscount.ALL_DISCOUNTS.values().stream().filter(entry -> !entry.hasExpired())
                    .map(entry -> {
                        BaseComponent[] components = stringifyDiscount(entry, shouldPutDetailsInHover);
                        return new ComponentBuilder("A ").color(YELLOW)
                                .append(components)
                                .append(" ")
                                .append("[Delete]").color(RED).bold(true)
                                .event(TextUtils.showText(TextUtils.of(YELLOW + "Click here to delete the discount")))
                                .event(TextUtils.suggestCommand("/worstshop discount delete " + entry.name()))
                                .create();
                    }).forEach(sender.spigot()::sendMessage);
        }

        @Subcommand("delete")
        @CommandPermission("worstshop.command.shop.discount.delete")
        @CommandCompletion("@discount_ids")
        public void deleteDiscount(CommandSender sender, String name) {
            ShopDiscount.Entry realEntry = ShopDiscount.ALL_DISCOUNTS.get(name);
            if (realEntry != null) {
                ShopDiscount.removeDiscountEntry(realEntry);
                sender.spigot().sendMessage(
                        new ComponentBuilder("Successfully removed ").color(GREEN)
                                .append(stringifyDiscount(realEntry, sender instanceof Player)).create()
                );
            }
        }
    }

    @Subcommand("inspectitem|itemmeta|item|meta")
    @CommandPermission("worstshop.command.shop.inspectitem")
    public void showItem(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();

        // save to temp yaml file
        YamlConfiguration temp = new YamlConfiguration();
        StaticShopElement.serializeItemStack(stack, new HashMap<>()).forEach(temp::set);
        String yamlString = temp.saveToString();

        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText("Click to copy line", WHITE)));
        player.spigot().sendMessage(new ComponentBuilder("Item: ").color(YELLOW)
                .append("[Copy all]").color(GREEN)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text(TextComponent.fromLegacyText("Click to copy all lines")))).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, yamlString))
                .create());
        for (String line : yamlString.split("\n")) {
            if (line.contains("item-meta")) {
                player.spigot().sendMessage(new ComponentBuilder(StringUtils.abbreviate(line, 25)).color(WHITE)
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(TextComponent.fromLegacyText("This line is abbreviated!", YELLOW)),
                                new Text(TextComponent.fromLegacyText("Click to copy line", WHITE))))
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, line))
                        .create());
            } else {
                player.spigot().sendMessage(new ComponentBuilder(line).color(WHITE)
                        .event(hoverEvent).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, line))
                        .create());
            }
        }
    }

    @Private
    @Subcommand("debugshop")
    @CommandPermission("worstshop.debug")
    @CommandCompletion("*")
    public void debugShop(CommandSender sender, Shop shop) {
        YamlConfiguration temp = new YamlConfiguration();
        shop.toYaml(temp);
        String str = temp.saveToString();
        for (String line : str.split("\n"))
            sender.sendMessage(line);
    }

    @Private
    @Subcommand("debugmatcher")
    @CommandPermission("worstshop.debug")
    @CommandCompletion("* @nothing")
    public void debugItemMatcher(Player player, Material material, String base64) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        ItemMeta meta = StaticShopElement.deserializeBase64ItemMeta(base64);
        ItemStack toCompare = new ItemStack(material, 1);
        toCompare.setItemMeta(meta);

        player.sendMessage(GREEN + "Testing held item against " + toCompare);
        for (CommodityItem.ItemMatcher matcher : CommodityItem.ItemMatcher.ITEM_MATCHERS.values()) {
            player.sendMessage(GREEN + matcher.name + ": " + matcher.test(stack, toCompare));
        }
    }

    @Private
    @Subcommand("debugplaceholder")
    @CommandCompletion("* @nothing")
    @CommandPermission("worstshop.debug")
    public void debugPlaceholder(CommandSender sender, OnlinePlayer player, String str) {
        PlaceholderContext context = PlaceholderContext.guessContext(player.player);

        sender.sendMessage(GREEN + "Placeholder string: " + str);
        String result = Placeholders.setPlaceholders(ConfigHelper.translateString(str), context);
        sender.sendMessage(GREEN + "Result: " + ConfigHelper.untranslateString(result));
    }

    @Subcommand("open")
    @CommandCompletion("* *")
    @CommandPermission("worstshop.command.shop.open.others")
    public void openOthers(CommandSender sender, Shop shop, OnlinePlayer player) {
        shop.getInventory(player.player).open(player.player);
    }

    @Subcommand("testpermission")
    @CommandCompletion("* @nothing")
    @CommandPermission("worstshop.command.shop.testpermission")
    public void testPermString(CommandSender sender, OnlinePlayer player, String permString) {
        try {
            Condition test = ConditionPermission.fromPermString(permString);
            boolean result = test.test(player.player);
            sender.sendMessage(WHITE + "Perm string " + YELLOW + permString +
                    WHITE + "(" + test + ") evaluated to " +
                    (result ? GREEN : RED) + result + WHITE + " for " + player.player.getName());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCommandArgument(ex.getMessage());
        }
    }


    @Subcommand("editor")
    @CommandCompletion("*")
    @CommandPermission("worstshop.command.shop.editor")
    public void openEditor(Player player, @Optional Shop shop) {
        if (shop == null)
            EditorMainMenu.getInventory().open(player);
        else
            EditorUtils.findAdaptorForClass(Shop.class, shop).onInteract(player, shop, null);
    }

    @HelpCommand
    public void help(CommandHelp help) {
        help.showHelp();
    }

    @Default
    @Subcommand("open")
    @CommandPermission("worstshop.command.shop.open")
    @CommandCompletion("*")
    public void open(Player player, @Optional Shop shop) {
        if (shop != null && shop.canPlayerView(player))
            shop.getInventory(player).open(player);
        else {
            Shop defaultShop = ShopManager.SHOPS.get(PluginConfig.defaultShop);
            if (defaultShop != null && defaultShop.canPlayerView(player)) {
                defaultShop.getInventory(player).open(player);
            }
        }
    }
}
