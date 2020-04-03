package com.jacky8399.worstshop.commands;

import co.aikar.commands.CommandHelp;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.DateTimeUtils;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.helper.PermStringHelper;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopDiscount;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import net.md_5.bungee.api.chat.*;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
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
        ShopManager.saveDiscounts();
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

    @Subcommand("discount")
    @CommandPermission("worstshop.discount")
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
                   switch (left) {
                       case "shop":
                           return ShopManager.SHOPS.keySet().stream()
                                   .filter(shopName -> shopName.startsWith(right))
                                   .map(shopName -> "shop=" + shopName).collect(Collectors.toList());
                       case "material":
                           return Arrays.stream(Material.values())
                                   .map(Enum::name)
                                   .filter(mat -> mat.startsWith(right))
                                   .map(mat -> "material=" + mat).collect(Collectors.toList());
                       case "player":
                           return Bukkit.getOnlinePlayers().stream()
                                   .map(Player::getName)
                                   .filter(player -> player.startsWith(right))
                                   .map(player -> "player=" + player).collect(Collectors.toList());
                       default:
                           return Collections.emptyList();
                   }
               } else {
                   return Arrays.asList("shop=", "material=", "player=", "permission=");
               }
            });
        }

        private BaseComponent[] stringifyDiscount(ShopDiscount.Entry discount) {
            ComponentBuilder hoverBuilder = new ComponentBuilder("Discount ID: ").color(net.md_5.bungee.api.ChatColor.GREEN)
                    .append(discount.name).color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .append("\nExpiry: ").color(net.md_5.bungee.api.ChatColor.GREEN)
                    .append(discount.expiry.atOffset(OffsetDateTime.now().getOffset()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .append(" (expires in ").color(net.md_5.bungee.api.ChatColor.GOLD)
                    .append(DateTimeUtils.formatTime(Duration.between(LocalDateTime.now(), discount.expiry))).color(net.md_5.bungee.api.ChatColor.GOLD)
                    .append(")").color(net.md_5.bungee.api.ChatColor.GOLD)
                    .append("\nApplicable to:").color(net.md_5.bungee.api.ChatColor.GREEN);
            boolean hasCriteria = false;
            if (discount.shop != null) {
                hoverBuilder.append("\nShop: ").append(discount.shop);
                hasCriteria = true;
            }
            if (discount.material != null) {
                hoverBuilder.append("\nMaterial: ").append(discount.material.name());
                hasCriteria = true;
            }
            if (discount.player != null) {
                hoverBuilder.append("\nPlayer: ").append(Bukkit.getOfflinePlayer(discount.player).getName());
                hasCriteria = true;
            }
            if (discount.permission != null) {
                hoverBuilder.append("\nPlayer w/ permission: ").append(discount.permission);
                hasCriteria = true;
            }
            if (!hasCriteria) {
                hoverBuilder.append("\nEveryone");
            }
            return new ComponentBuilder((1 - discount.percentage)*100 + "% discount ").color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .append("(" + discount.name + ")").color(net.md_5.bungee.api.ChatColor.AQUA).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBuilder.create())).create();
        }

        @Subcommand("create")
        @CommandPermission("worstshop.discount.create")
        @CommandCompletion("@nothing 1h|6h|12h|1d|7d|30d 0.5|0.7|0.9 @discount_argstr")
        public void createDiscount(CommandSender sender, String name, String expiry, double discount, String argString) {
            String[] args = argString.split(" ");
            LocalDateTime expiryTime = LocalDateTime.now().plus(DateTimeUtils.parseTimeStr(expiry));
            ShopDiscount.Entry entry = new ShopDiscount.Entry(name, expiryTime, discount);
            for (String str : args) {
                String[] strArgs = str.split("=");
                String specifier = strArgs[0];
                String value = strArgs[1];
                switch (specifier) {
                    case "shop": {
                        entry.shop = value;
                    }
                    break;
                    case "player": {
                        Player player = Bukkit.getPlayer(value);
                        if (player == null) {
                            throw new InvalidCommandArgument(value + " is not online");
                        }
                        entry.player = player.getUniqueId();
                    }
                    break;
                    case "material": {
                        Material material = Material.getMaterial(value);
                        if (material == null || ItemUtils.isAir(material)) {
                            throw new InvalidCommandArgument("Invalid material " + value);
                        }
                        entry.material = material;
                    }
                    break;
                    case "permission": {
                        entry.permission = value;
                    }
                    break;
                    default:
                        throw new InvalidCommandArgument("Unknown argument " + specifier);
                }
            }
            ShopDiscount.addDiscountEntry(entry);
            sender.sendMessage(new ComponentBuilder("Added new ").color(net.md_5.bungee.api.ChatColor.GREEN).append(stringifyDiscount(entry)).create());
        }

        @Subcommand("list|info")
        @CommandPermission("worstshop.discount.list")
        public void listDiscounts(CommandSender sender) {
            sender.sendMessage(ChatColor.GREEN + "Discounts:");
            ShopDiscount.ALL_DISCOUNTS.values().stream().filter(entry -> !entry.hasExpired())
                    .map(entry -> {
                        BaseComponent[] components = stringifyDiscount(entry);
                        return new ComponentBuilder("A ").color(net.md_5.bungee.api.ChatColor.YELLOW)
                                .append(components)
                                .append(" ")
                                .append("[Delete]").color(net.md_5.bungee.api.ChatColor.RED).bold(true)
                                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "Click here to delete the discount")))
                                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/worstshop discount delete " + entry.name))
                                .create();
                    }).forEach(sender::sendMessage);
        }

        @Subcommand("delete")
        @CommandPermission("worstshop.discount.delete")
        @CommandCompletion("@discount_ids")
        public void deleteDiscount(CommandSender sender, String name) {
            ShopDiscount.Entry realEntry = ShopDiscount.ALL_DISCOUNTS.get(name);
            if (realEntry != null) {
                ShopDiscount.removeDiscountEntry(realEntry);
                sender.sendMessage(
                        new ComponentBuilder("Successfully removed ").color(net.md_5.bungee.api.ChatColor.GREEN)
                        .append(stringifyDiscount(realEntry)).create()
                );
            }
        }

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

        //<editor-fold defaultstate="collapsed" desc="Item meta serialization">
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            if (damageable.hasDamage()) {
                serialized.add("damage: " + damageable.getDamage());
            }
        }
        if (meta.hasCustomModelData()) {
            serialized.add("custom-model-data: " + meta.getCustomModelData());
        }
        if (meta.hasEnchants()) {
            serialized.add("enchants:");
            meta.getEnchants().forEach((ench, level)->serialized.add("  " + ench.getKey().getKey() + ": " + level));
        }
        if (meta.hasDisplayName()) {
            serialized.add("name: '" + replaceColor(meta.getDisplayName()) + "'");
        }
        if (meta.hasLocalizedName()) {
            serialized.add("loc-name: '" + meta.getLocalizedName() + "'");
        }
        if (meta.hasLore()) {
            serialized.add("lore:");
            meta.getLore().forEach(lore -> serialized.add("- '" + replaceColor(lore) + "'"));
        }
        if (meta.getItemFlags().size() != 0) {
            serialized.add("hide-flags:");
            meta.getItemFlags().stream().map(ItemFlag::name)
                    .map(str -> str.substring("HIDE_".length())) // strip hide
                    .map(str -> str.toLowerCase().replace('_', ' ')) // to lowercase
                    .map(str -> "- " + str)
                    .forEach(serialized::add);
        }
        if (meta instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) meta;
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(skullMeta);
            serialized.add("skull: " + profile.getUUID());
        }
        //</editor-fold>

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
        player.sendMessage(new ComponentBuilder(StringUtils.abbreviate(metaStr, 25)).color(net.md_5.bungee.api.ChatColor.WHITE)
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

    @HelpCommand
    public void help(CommandHelp help) {
        help.showHelp();
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
