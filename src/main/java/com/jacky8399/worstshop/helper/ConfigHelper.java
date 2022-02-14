package com.jacky8399.worstshop.helper;

import com.google.common.collect.ImmutableMap;
import com.jacky8399.worstshop.PluginConfig;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.elements.ShopElement;
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
                String.class, (String string) -> Condition.fromShorthand(string)));
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
