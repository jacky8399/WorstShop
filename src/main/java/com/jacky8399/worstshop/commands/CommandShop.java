package com.jacky8399.worstshop.commands;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.helper.PermStringHelper;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopDiscount;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Subcommand("discount")
    @CommandPermission("worstshop.discount")
    public class Discount extends co.aikar.commands.BaseCommand {

        private final Pattern TIME_STR = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

        public Discount() {}


        private LocalDateTime parseTimeStr(String str) {
            Matcher matcher = TIME_STR.matcher(str);
            if (!str.isEmpty() && matcher.matches()) {
                int secondsIntoFuture = 0;
                String days = matcher.group(1), hours = matcher.group(2), minutes = matcher.group(3), seconds = matcher.group(4);
                if (days != null) {
                    secondsIntoFuture += Integer.parseInt(days) * 86400;
                }
                if (hours != null) {
                    secondsIntoFuture += Integer.parseInt(hours) * 3600;
                }
                if (minutes != null) {
                    secondsIntoFuture += Integer.parseInt(minutes) * 60;
                }
                if (seconds != null) {
                    secondsIntoFuture += Integer.parseInt(seconds);
                }
                return LocalDateTime.now().plusSeconds(secondsIntoFuture);
            }
            throw new IllegalArgumentException(str + " is not a valid time string");
        }

        private String stringifyDiscount(ShopDiscount.Entry discount) {
            StringBuilder builder = new StringBuilder(ChatColor.YELLOW.toString());
            builder.append((1 - discount.percentage)*100)
                    .append("% discount ending on ")
                    .append(discount.expiry.toString())
                    .append(" applicable to:");
            boolean hasCriteria = false;
            if (discount.shop != null) {
                builder.append("\nShop: ").append(discount.shop);
                hasCriteria = true;
            }
            if (discount.material != null) {
                builder.append("\nMaterial: ").append(discount.material.name());
                hasCriteria = true;
            }
            if (discount.player != null) {
                builder.append("\nPlayer: ").append(Bukkit.getOfflinePlayer(discount.player).getName());
                hasCriteria = true;
            }
            if (discount.permission != null) {
                builder.append("\nPlayer w/ permission: ").append(discount.permission);
                hasCriteria = true;
            }
            if (!hasCriteria) {
                builder.append(" everyone");
            }
            return builder.toString();
        }

        @Subcommand("create")
        @CommandPermission("worstshop.discount.create")
        @CommandCompletion("1h|6h|12h|1d|7d|30d 0.5|0.7|0.9 shop=shopname|player=playername|material=material|permission=permission")
        public void createDiscount(CommandSender sender, String expiry, double discount, String argString) {
            String[] args = argString.split(" ");
            LocalDateTime expiryTime = parseTimeStr(expiry);
            ShopDiscount.Entry entry = new ShopDiscount.Entry(expiryTime, discount);
            for (String str : args) {
                String[] strArgs = str.split("[=:]");
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
            sender.sendMessage(ChatColor.GREEN + "Added " + stringifyDiscount(entry));
        }

        @Subcommand("info|list")
        @CommandPermission("worstshop.discount.list")
        public void listDiscounts(CommandSender sender) {
            sender.sendMessage(ChatColor.GREEN + "Discounts:");
            ShopDiscount.ALL_DISCOUNTS.stream().filter(entry -> !entry.hasExpired())
                    .map(this::stringifyDiscount).forEach(sender::sendMessage);
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
