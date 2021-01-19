package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.I18n;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;

public final class TextUtils {
    private TextUtils() {}

    public static HoverEvent showText(BaseComponent[] components) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(components));
    }

    public static BaseComponent[] of(String legacy) {
        return TextComponent.fromLegacyText(legacy);
    }

    public static BaseComponent[] ofTranslated(String translationKey, Object... args) {
        return of(I18n.translate(translationKey, args));
    }

    public static BaseComponent[] ofTranslated(String translationKey, Player player, Object... args) {
        return of(I18n.translate(translationKey, player, args));
    }

    public static BaseComponent[] ofTranslated(I18n.Translatable translatable, String... args) {
        return of(translatable.apply(args));
    }

    public static BaseComponent[] ofTranslated(I18n.Translatable translatable, Player player, String... args) {
        return of(translatable.apply(player, args));
    }

    public static BaseComponent[] formatDuration(boolean isFuture, LocalDateTime time, Duration duration) {
        String durationStr = DateTimeUtils.formatTime(duration);
        return new ComponentBuilder(isFuture ? "in " + durationStr : durationStr + " ago")
                .color(ChatColor.YELLOW)
                .event(showText(of(DateTimeUtils.formatTime(time)))).create();
    }
}
