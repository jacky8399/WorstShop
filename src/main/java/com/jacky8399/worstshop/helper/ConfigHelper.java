package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.ParseContext;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigHelper {
    private ConfigHelper() {}

    public static <T extends Enum<T>> T parseEnum(String input, Class<T> clazz) {
        return Enum.valueOf(clazz, input.toUpperCase().replace(' ', '_'));
    }

    public static String translateString(String input) {
        return input != null ? ChatColor.translateAlternateColorCodes('&', input) : null;
    }

    public static String untranslateString(String input) {
        return input != null ? input.replace(ChatColor.COLOR_CHAR, '&') : null;
    }

    // haha regex magic
    private static final Pattern SPECIAL = Pattern.compile("\\((.+?)\\)\\[(.*?)(?<!\\\\)]");
    // don't split on escaped semicolons
    private static final Pattern EVENT_SPLITTER = Pattern.compile("(?<!\\\\);");

    public static BaseComponent[] parseComponentString(String input) {
        try {
            return ComponentSerializer.parse(input);
        } catch (com.google.gson.JsonSyntaxException ex) {
            // check if it looks like old components first
            if (!SPECIAL.matcher(input).matches()) {
                // probably just a plain string
                return TextComponent.fromLegacyText(translateString(input));
            }
            Logger logger = WorstShop.get().logger;
            logger.warning("Custom syntax for chat components is deprecated. Please use proper JSON.");
            logger.warning("Parse context: " + ParseContext.getHierarchy());
            BaseComponent[] components = parseComponentStringOld(input);
            // convert to proper JSON
            logger.warning("The equivalent JSON string:");
            logger.warning(ComponentSerializer.toString(components));
            return components;
        }
    }

    /**
     * Parse strings with Markdown-like link specifiers into {@link BaseComponent}s <br>
     * e.g. {@code Click (here)[hover=Click me!]}
     * @param input input string
     * @return resultant {@link BaseComponent}s
     */
    @Deprecated
    public static BaseComponent[] parseComponentStringOld(String input) {
        int index = 0;
        Matcher matcher = SPECIAL.matcher(input);
        ComponentBuilder builder = new ComponentBuilder();
        while (matcher.find()) {
            // append text before
            builder.append(translateString(input.substring(index, matcher.start())))
                    // append text in brackets
                    .append(translateString(matcher.group(1)));
            String eventsString = matcher.group(2).replace("\\\\]", ";");
            // parse string in square brackets
            if (!eventsString.trim().isEmpty()) {
                String[] split = EVENT_SPLITTER.split(eventsString);
                for (String s : split) {
                    // split on first '='
                    int idx = s.indexOf("=");
                    if (idx == -1) {
                        throw new IllegalArgumentException("Invalid event specifier " + s + ", should be in format event=value");
                    }
                    String eventType = s.substring(0, idx);
                    String arg = translateString(s.substring(idx + 1).replaceAll("\\\\;",";"));
                    if ("hover".equals(eventType)) {
                        builder.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(arg)));
                    } else {
                        ClickEvent.Action action = parseEnum(eventType, ClickEvent.Action.class);
                        builder.event(new ClickEvent(action, arg));
                    }
                }
            }
            index = matcher.end();
        }
        // append remaining text
        builder.append(translateString(input.substring(index)));
        return builder.create();
    }
}
