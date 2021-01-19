package com.jacky8399.worstshop.commands;

import co.aikar.commands.CommandHelp;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.EditorMainMenu;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopDiscount;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionPermission;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.jacky8399.worstshop.I18n.translate;

@SuppressWarnings("unused")
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
            throw new InvalidCommandArgument(translate("worstshop.errors.invalid-shop", arg));
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


        issuer.sendMessage(translate("worstshop.messages.config-reloaded"));
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

        private BaseComponent[] stringifyDiscount(ShopDiscount.Entry discount, boolean putDetailsInHover) {
            boolean expires = discount.expiry != null;
            ComponentBuilder detailBuilder = new ComponentBuilder("Discount ID: ").color(ChatColor.GREEN)
                    .append(discount.name).color(ChatColor.YELLOW)
                    .append("\nExpiry: ").color(ChatColor.GREEN)
                    .append(!expires ? "never" :
                            discount.expiry.atOffset(OffsetDateTime.now().getOffset())
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    ).color(expires ? ChatColor.YELLOW : ChatColor.BLUE);
            if (expires)
                detailBuilder.append(" (expires in ").color(ChatColor.GOLD)
                        .append(DateTimeUtils.formatTime(Duration.between(LocalDateTime.now(), discount.expiry))).color(ChatColor.GOLD)
                        .append(")").color(ChatColor.GOLD);
            detailBuilder.append("\nApplicable to:").color(ChatColor.GREEN);
            boolean hasCriteria = false;
            if (discount.shop != null) {
                detailBuilder.append("\nShop: ").append(discount.shop.id);
                hasCriteria = true;
            }
            if (discount.material != null) {
                detailBuilder.append("\nMaterial: ").append(discount.material.name());
                hasCriteria = true;
            }
            if (discount.player != null) {
                detailBuilder.append("\nPlayer: ").append(Bukkit.getOfflinePlayer(discount.player).getName());
                hasCriteria = true;
            }
            if (discount.permission != null) {
                detailBuilder.append("\nPlayer w/ permission: ").append(discount.permission);
                hasCriteria = true;
            }
            if (!hasCriteria) {
                detailBuilder.append("\nEveryone");
            }
            ComponentBuilder actualComponent = new ComponentBuilder((1 - discount.percentage) * 100 + "% discount ").color(ChatColor.YELLOW)
                    .append("(" + discount.name + ")").color(ChatColor.AQUA);
            if (putDetailsInHover)
                actualComponent.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(detailBuilder.create())));
            else
                actualComponent.append("\n").append(detailBuilder.create());
            return actualComponent.create();
        }

        @Subcommand("create")
        @CommandPermission("worstshop.discount.create")
        @CommandCompletion("@nothing permanent|1h|6h|12h|1d|7d|30d 0.5|0.7|0.9 @discount_argstr")
        public void createDiscount(CommandSender sender, String name, String expiry, double discount, String argString) {
            String[] args = argString.split(" ");
            LocalDateTime expiryTime = expiry.equals("permanent") ? null : LocalDateTime.now().plus(DateTimeUtils.parseTimeStr(expiry));
            ShopDiscount.Entry entry = new ShopDiscount.Entry(name, expiryTime, discount);
            for (String str : args) {
                String[] strArgs = str.split("=");
                String specifier = strArgs[0];
                String value = strArgs[1];
                switch (specifier) {
                    case "shop": {
                        entry.shop = ShopReference.of(value);
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
            sender.spigot().sendMessage(
                    new ComponentBuilder("Added new ").color(ChatColor.GREEN)
                            .append(stringifyDiscount(entry, sender instanceof Player)).create()
            );
        }

        @Subcommand("list")
        @CommandPermission("worstshop.discount.list")
        public void listDiscounts(CommandSender sender) {
            boolean shouldPutDetailsInHover = sender instanceof Player;
            sender.sendMessage(ChatColor.GREEN + "Discounts:");
            ShopDiscount.ALL_DISCOUNTS.values().stream().filter(entry -> !entry.hasExpired())
                    .map(entry -> {
                        BaseComponent[] components = stringifyDiscount(entry, shouldPutDetailsInHover);
                        return new ComponentBuilder("A ").color(ChatColor.YELLOW)
                                .append(components)
                                .append(" ")
                                .append("[Delete]").color(ChatColor.RED).bold(true)
                                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(ChatColor.YELLOW + "Click here to delete the discount"))))
                                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/worstshop discount delete " + entry.name))
                                .create();
                    }).forEach(sender.spigot()::sendMessage);
        }

        @Subcommand("delete")
        @CommandPermission("worstshop.discount.delete")
        @CommandCompletion("@discount_ids")
        public void deleteDiscount(CommandSender sender, String name) {
            ShopDiscount.Entry realEntry = ShopDiscount.ALL_DISCOUNTS.get(name);
            if (realEntry != null) {
                ShopDiscount.removeDiscountEntry(realEntry);
                sender.spigot().sendMessage(
                        new ComponentBuilder("Successfully removed ").color(ChatColor.GREEN)
                        .append(stringifyDiscount(realEntry, sender instanceof Player)).create()
                );
            }
        }
    }

    @Subcommand("log")
    @CommandPermission("worstshop.log")
    public class Logs extends co.aikar.commands.BaseCommand {
        @Subcommand("error")
        @CommandPermission("worstshop.log.error")
        public class Error extends co.aikar.commands.BaseCommand {
            @Subcommand("list")
            public void listErrors(CommandSender sender, @Optional Integer page) {
                if (page == null) page = 0;
                LocalDateTime now = LocalDateTime.now();
                Exceptions.exceptions.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .skip(page * 10)
                        .limit(10)
                        .forEach(entry -> {
                            Duration timeElapsed = Duration.between(entry.getValue().date, now);
                            sender.spigot().sendMessage(new ComponentBuilder("")
                                    .append(TextUtils.formatDuration(false, entry.getValue().date, timeElapsed))
                                    .append(" - " + entry.getKey() + " (" + entry.getValue().exception.getMessage() + ")")
                                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new ComponentBuilder("Click to inspect!").color(ChatColor.GREEN).create())))
                                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/worstshop log error show " + entry.getKey()))
                                    .create()
                            );
                        });
            }

            @Subcommand("show")
            public void showError(CommandSender sender, String hash, @Optional String full) {
                boolean showFull = "full".equalsIgnoreCase(full);
                Exceptions.ExceptionLog log = Exceptions.exceptions.get(hash);
                if (log == null) {
                    throw new InvalidCommandArgument(translate("worstshop.errors.commands.no-error-log", hash), false);
                }
                LocalDateTime now = LocalDateTime.now();
                Duration timeElapsed = Duration.between(log.date, now);
                String stackTrace = ExceptionUtils.getStackTrace(log.exception).replace("\t", "    ").replace("\r\n","\n");
                // make fancy hover component if invoked by player
                BaseComponent[] showStackTraceComponent = sender instanceof Player ?
                        new ComponentBuilder("  [Click to see full stack trace]")
                                .color(ChatColor.GREEN)
                                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/worstshop log error show " + hash + " full")).create() :
                        new ComponentBuilder("  To show stack trace, run ").color(ChatColor.GREEN)
                                .append("/worstshop log error show " + hash + " full").color(ChatColor.YELLOW).create();
                BaseComponent[] stackTraceComponent = showFull ?
                        showStackTraceComponent :
                        new ComponentBuilder("  Stack trace:\n").color(ChatColor.GREEN)
                                .append(stackTrace).color(ChatColor.DARK_RED).create();
                // show cause of throwable
                BaseComponent[] causeComponent = log.exception.getCause() != null ?
                        new ComponentBuilder("  Caused by: ").color(ChatColor.YELLOW)
                                .append(log.exception.getCause().toString()).color(ChatColor.GREEN).create() :
                        new BaseComponent[]{new TextComponent("")};
                BaseComponent[] components = new ComponentBuilder("")
                        .append("Error " + log.exception.getClass().getSimpleName()).color(ChatColor.RED).append("\n")
                        .append("  Message: ").color(ChatColor.YELLOW)
                        .append(log.exception.getMessage()).color(ChatColor.GREEN).append("\n")
                        .append(causeComponent)
                        .append("  At: ").color(ChatColor.YELLOW)
                        .append(TextUtils.formatDuration(false, log.date, timeElapsed)).append("\n")
                        .append(stackTraceComponent)
                        .create();
                sender.spigot().sendMessage(components);
            }
        }

        @Subcommand("purchases")
        @CommandPermission("worstshop.log.purchases")
        public class InspectPurchases extends co.aikar.commands.BaseCommand {
            public InspectPurchases() {
                manager.getCommandCompletions().registerCompletion("@purchase_record_ids", ctx -> {
                    OnlinePlayer player = ctx.getContextValue(OnlinePlayer.class);
                    PlayerPurchaseRecords record = PlayerPurchaseRecords.getCopy(player.player);
                    return record.getKeys();
                });
            }

            @Subcommand("show")
            @CommandCompletion("* @purchase_record_ids")
            public void showPlayerPurchaseRecords(CommandSender sender, OnlinePlayer onlinePlayer, @Optional String recordId) {
                Player player = onlinePlayer.player;
                PlayerPurchaseRecords record = PlayerPurchaseRecords.getCopy(player);
                if (recordId == null) {
                    record.purgeOldRecords();
                    Set<String> keys = record.getKeys();
                    if (keys.size() == 0) {
                        sender.sendMessage(ChatColor.RED + player.getName() + " doesn't have any purchase records.");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + player.getName() + " has purchase record(s) in the following categories:");
                        for (String key : keys) {
                            BaseComponent[] components = new ComponentBuilder()
                                    .append(key).color(ChatColor.YELLOW)
                                    .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/worstshop purchases show " + player.getName() + " " + key))
                                    .create();
                            sender.spigot().sendMessage(components);
                        }
                    }
                } else {
                    PlayerPurchaseRecords.RecordStorage purchases = record.get(recordId);
                    if (purchases == null) {
                        throw new InvalidCommandArgument(recordId + " is not a valid record ID!", false);
                    }
                    purchases.purgeOldRecords();
                    List<Map.Entry<LocalDateTime, Integer>> entries = purchases.getEntries();
                    sender.sendMessage(ChatColor.GREEN + player.getName() + " has " + entries.size() + " purchase record(s) in " + ChatColor.YELLOW + recordId);
                    LocalDateTime now = LocalDateTime.now();
                    for (Map.Entry<LocalDateTime, Integer> entry : entries) {
                        BaseComponent[] components = new ComponentBuilder("")
                                .append("x" + entry.getValue()).color(ChatColor.YELLOW)
                                .append(" - ").color(ChatColor.YELLOW)
                                .append(TextUtils.formatDuration(false, entry.getKey(), Duration.between(entry.getKey(), LocalDateTime.now())))
                                .create();
                        sender.spigot().sendMessage(components);
                    }
                }
            }
        }
    }

    @Subcommand("inspectitem|itemmeta|item|meta")
    @CommandPermission("worstshop.inspectitem")
    public void showItem(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();

        // save to temp yaml file
        YamlConfiguration temp = new YamlConfiguration();
        StaticShopElement.serializeItemStack(stack, new HashMap<>()).forEach(temp::set);
        String yamlString = temp.saveToString();

        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText("Click to copy line", ChatColor.WHITE)));
        player.spigot().sendMessage(new ComponentBuilder("Item: ").color(ChatColor.YELLOW)
                .append("[Copy all]").color(ChatColor.GREEN)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text(TextComponent.fromLegacyText("Click to copy all lines")))).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, yamlString))
                .create());
        for (String line : yamlString.split("\n")) {
            if (line.contains("item-meta")) {
                player.spigot().sendMessage(new ComponentBuilder(StringUtils.abbreviate(line, 25)).color(ChatColor.WHITE)
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(TextComponent.fromLegacyText("This line is abbreviated!", ChatColor.YELLOW)),
                                new Text(TextComponent.fromLegacyText("Click to copy line", ChatColor.WHITE))))
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, line))
                        .create());
            } else {
                player.spigot().sendMessage(new ComponentBuilder(line).color(ChatColor.WHITE)
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
            Condition test = ConditionPermission.fromPermString(permString);
            boolean result = test.test(player.player);
            sender.sendMessage(ChatColor.WHITE + "Perm string " + ChatColor.YELLOW + permString + ChatColor.WHITE + "(" +
                    test.toString() + ") evaluated to " +
                    (result ? ChatColor.GREEN : ChatColor.RED) + result + ChatColor.WHITE + " for " + player.player.getName());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCommandArgument(ex.getMessage());
        }
    }

    @Subcommand("editor")
    @CommandCompletion("*")
    @CommandPermission("worstshop.editor")
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
