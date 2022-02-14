package com.jacky8399.worstshop.commands;

import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Exceptions;
import com.jacky8399.worstshop.helper.PlayerPurchases;
import com.jacky8399.worstshop.helper.TextUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jacky8399.worstshop.I18n.translate;

@CommandAlias("worstshop|shop")
@Subcommand("log|logs")
@CommandPermission("worstshop.command.log")
public class CommandLogs extends BaseCommand {

    public CommandLogs() {
        manager.getCommandCompletions().registerCompletion("error_hash_ids", ctx -> Exceptions.exceptions.keySet());
//        manager.getCommandCompletions().registerCompletion("purchase_record_ids", ctx -> );
    }

    @CommandAlias("worstshoplog|worstshoplogs|shoplog|shoplogs")
    public class Inner extends BaseCommand {
        @Subcommand("error")
        @CommandPermission("worstshop.command.log.error")
        public class Error extends BaseCommand {
            @Subcommand("list")
            public void listErrors(CommandSender sender, @Optional Integer page) {
                if (page == null) page = 0;
                LocalDateTime now = LocalDateTime.now();
                Exceptions.exceptions.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.comparing(log -> log.date)))
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
            @CommandCompletion("@error_hash_ids full")
            public void showError(CommandSender sender, String hash, @Optional String full) {
                boolean showFull = "full".equalsIgnoreCase(full);
                Exceptions.ExceptionLog log = Exceptions.exceptions.get(hash);
                if (log == null) {
                    throw new InvalidCommandArgument(translate("worstshop.errors.commands.no-error-log", hash), false);
                }
                LocalDateTime now = LocalDateTime.now();
                Duration timeElapsed = Duration.between(log.date, now);
                String stackTrace = ExceptionUtils.getStackTrace(log.exception)
                        .replace("\t", "    ").replace("\r\n", "\n");
                // make fancy hover component if invoked by player
                BaseComponent[] showStackTraceComponent = sender instanceof Player ?
                        new ComponentBuilder("  [Click to see full stack trace]").color(ChatColor.GREEN)
                                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/worstshop log error show " + hash + " full")).create() :
                        new ComponentBuilder("  To show stack trace, run ").color(ChatColor.GREEN)
                                .append("/worstshop log error show " + hash + " full").color(ChatColor.YELLOW).create();
                BaseComponent[] stackTraceComponent = showFull ?
                        new ComponentBuilder("  Stack trace:\n").color(ChatColor.GREEN)
                                .append(stackTrace.replace("Caused by:", ChatColor.RED + "Caused by:"))
                                .color(ChatColor.DARK_RED)
                                .create() :
                        showStackTraceComponent;
                // show cause of throwable
                BaseComponent[] causeComponent = log.exception.getCause() != null ?
                        new ComponentBuilder("  Caused by: ").color(ChatColor.YELLOW)
                                .append(log.exception.getCause().toString()).color(ChatColor.GREEN).create() :
                        new BaseComponent[]{new TextComponent("")};
                BaseComponent[] components = new ComponentBuilder("")
                        .append("Error " + log.exception.getClass().getSimpleName()).color(ChatColor.RED).append("\n")
                        .append("  Message: ").color(ChatColor.YELLOW)
                        .append(log.exception.getMessage()).color(ChatColor.GREEN).append("\n")
                        .append(causeComponent).append("\n")
                        .append("  At: ").color(ChatColor.YELLOW)
                        .append(TextUtils.formatDuration(false, log.date, timeElapsed)).append("\n")
                        .retain(ComponentBuilder.FormatRetention.NONE) // why would it inherit the ClickEvent
                        .append(stackTraceComponent)
                        .create();
                sender.spigot().sendMessage(components);
            }
        }

        @Subcommand("purchases")
        @CommandPermission("worstshop.command.log.purchases")
        public class InspectPurchases extends BaseCommand {
            public InspectPurchases() {
                WorstShop.get().commands.getCommandCompletions().registerCompletion("@purchase_record_ids", ctx -> {
                    OnlinePlayer player = ctx.getContextValue(OnlinePlayer.class);
                    PlayerPurchases record = PlayerPurchases.getCopy(player.player);
                    return record.getKeys();
                });
            }

            @Subcommand("show")
            @CommandCompletion("* @purchase_record_ids")
            public void showPlayerPurchaseRecords(CommandSender sender, OnlinePlayer onlinePlayer, @Optional String recordId) {
                Player player = onlinePlayer.player;
                PlayerPurchases record = PlayerPurchases.getCopy(player);
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
                    PlayerPurchases.RecordStorage purchases = record.get(recordId);
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
}
