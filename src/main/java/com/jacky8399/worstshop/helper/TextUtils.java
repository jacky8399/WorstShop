package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.StyleBuilderApplicable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public final class TextUtils {

    private TextUtils() {}

    public static HoverEvent showText(BaseComponent[] components) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(components));
    }

    public static ClickEvent suggestCommand(String command) {
        return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
    }

    public static ClickEvent runCommand(String command) {
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
    }

    public static BaseComponent[] of(String legacy) {
        return TextComponent.fromLegacyText(legacy);
    }

    public static BaseComponent[] ofTranslated(String translationKey, Object... args) {
        return of(I18n.translate(translationKey, args));
    }

    public static BaseComponent[] ofTranslated(I18n.Translatable translatable, Object... args) {
        return of(translatable.apply(args));
    }

    public static BaseComponent[] formatDuration(boolean isFuture, LocalDateTime time, Duration duration) {
        String durationStr = DateTimeUtils.formatTime(duration);
        return new ComponentBuilder(isFuture ? "in " + durationStr : durationStr + " ago")
                .color(ChatColor.YELLOW)
                .event(showText(of(DateTimeUtils.formatTime(time)))).create();
    }

    public static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat() // hahaha
            .build();
    public static net.kyori.adventure.text.TextComponent fromLegacy(String legacy) {
        return LEGACY_COMPONENT_SERIALIZER.deserialize(legacy);
    }

    public static net.kyori.adventure.text.TranslatableComponent nameStack(ItemStack stack, int amount, StyleBuilderApplicable... style) {
        return Component.translatable("container.shulkerBox.itemCount", null,
                List.of(
                        stack.getItemMeta().displayName() instanceof Component displayName ?
                                displayName :
                                Component.translatable(stack),
                        Component.text(amount)
                ), style);
    }
}
