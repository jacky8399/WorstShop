package com.jacky8399.worstshop.helper;

import com.google.common.collect.ImmutableMap;
import com.jacky8399.worstshop.PluginConfig;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyFormat;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class ConfigHelper {
    private ConfigHelper() {}

    record ConfigDeserializer(Class<?>[] acceptedTypes, Map<Class<?>, Function<?, ?>> constructors) {}
    static final HashMap<Class<?>, ConfigDeserializer> configDeserializers = new HashMap<>();

    @SafeVarargs
    public static <T, C> void registerConfigDeserializer(Class<T> clazz, Function<C, T> function, Class<? extends C>... classes) {
        ImmutableMap.Builder<Class<?>, Function<?, ?>> map = ImmutableMap.builder();
        for (Class<? extends C> accepted : classes) {
            map.put(accepted, function);
        }
        configDeserializers.put(clazz, new ConfigDeserializer(classes, map.build()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, C> void registerConfigDeserializer(Class<T> clazz, Map<Class<? extends C>, Function<?, T>> functionMap) {
        Class<?>[] classes = functionMap.keySet().toArray(new Class[0]);
        configDeserializers.put(clazz, new ConfigDeserializer(classes, (Map) functionMap));
    }

    public static void registerDefaultDeserializers() {
        ConfigHelper.registerConfigDeserializer(Action.class, ImmutableMap.of(
                Config.class, (Config config) -> Action.fromConfig(config),
                String.class, (String string) -> Action.fromCommand(string)));

        ConfigHelper.registerConfigDeserializer(Condition.class, ImmutableMap.of(
                Config.class, (Config config) -> Condition.fromMap(config),
                String.class, (String string) -> Condition.fromShorthand(string),
                Boolean.class, (Boolean bool) -> ConditionConstant.valueOf(bool)
        ));
    }

    @NotNull
    public static <T extends Enum<T>> T parseEnum(String input, Class<T> clazz) throws IllegalArgumentException {
        input = input.toUpperCase().replace(' ', '_');
        if (clazz == Material.class) { // ensure that legacy names are not used
            @SuppressWarnings("unchecked")
            T mat = (T) Material.matchMaterial(input);
            if (mat == null)
                throw new IllegalArgumentException(input + " is not a valid material");
            return mat;
        }
        return Enum.valueOf(clazz, input);
    }

    public static String translateString(String input) {
        if (input == null)
            return null;
        input = PluginConfig.rgbRegex.matcher(input).replaceAll(result -> ChatColor.of("#" + result.group(1)).toString());
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private static final Pattern LEGACY_FORMAT = Pattern.compile("&(.)");
    /**
     * Migrates the string to MiniMessage format
     * @param input The string
     * @return The string in MiniMessage format
     */
    public static String migrateString(String input) {
        input = input.replace(LegacyComponentSerializer.SECTION_CHAR, '&');
        input = PluginConfig.rgbRegex.matcher(input).replaceAll("<#$1>");
        return LEGACY_FORMAT.matcher(input).replaceAll(matchResult -> {
            char chr = matchResult.group(1).charAt(0);
            LegacyFormat legacyFormat = LegacyComponentSerializer.parseChar(chr);
            if (legacyFormat == null) {
                return matchResult.group(); // skip
            } else if (legacyFormat.color() != null) {
                return "<reset><" + legacyFormat.color() + ">";
            } else if (legacyFormat.decoration() != null) {
                return switch (legacyFormat.decoration()) {
                    case BOLD -> "<b>";
                    case ITALIC -> "<i>";
                    case OBFUSCATED -> "<obf>";
                    case UNDERLINED -> "<u>";
                    case STRIKETHROUGH -> "<st>";
                };
            } else { // reset
                return "<reset>";
            }
        });
    }

    public static String untranslateString(String input) {
        return input != null ? input.replace(ChatColor.COLOR_CHAR, '&') : null;
    }

    public static BaseComponent[] parseComponentString(String input) {
        try {
            return ComponentSerializer.parse(input);
        } catch (com.google.gson.JsonSyntaxException ex) {
            // probably just a plain string
            return TextComponent.fromLegacyText(translateString(input));
        }
    }

    public static HashMap<String, Object> parseVariables(Config variables) {
        HashMap<String, Object> var = new HashMap<>();
        variables.getKeys().forEach(key -> {
            Optional<Config> complexConfigOptional = variables.tryFind(key, Config.class);
            if (complexConfigOptional.isPresent()) {
                Config complexConfig = complexConfigOptional.get();
                String type = complexConfig.get("type", String.class);
                Config valueYaml = complexConfig.get("value", Config.class);
                Object value = switch (type) {
                    case "element" -> ShopElement.fromConfig(valueYaml);
                    default -> throw new ConfigException("Unsupported variable type "  + type, complexConfig, "type");
                };
                var.put(key, value);
            } else {
                var.put(key, variables.get(key, Object.class));
            }
        });
        return var;
    }

    public static Object stringifyVariable(Object variable) {
        if (variable instanceof ShopElement element) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("type", "element");
            map.put("value", element.toMap(new HashMap<>()));
            return map;
        }
        return variable;
    }
}
