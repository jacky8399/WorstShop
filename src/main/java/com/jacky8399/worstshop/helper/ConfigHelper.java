package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.HashMap;
import java.util.Optional;

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

//    public static ConfigurationOptions applyDefaultOptions(ConfigurationOptions def) {
//        return def.serializers(builder -> builder
//                .register(new FlexibleEnumSerializer())
//                .register(LOCAL_DATE_TIME)
//                .register(ShopReference.Serializer.INSTANCE)
//        );
//    }
//
//    public static YamlConfigurationLoader createLoader(Path path) {
//        return YamlConfigurationLoader.builder()
//                .path(path)
//                .defaultOptions(ConfigHelper::applyDefaultOptions)
//                .nodeStyle(NodeStyle.BLOCK).indent(2)
//                .build();
//    }
//
//    public static YamlConfigurationLoader createLoader(File file) {
//        return createLoader(file.toPath());
//    }
//
//    private static class FlexibleEnumSerializer extends ScalarSerializer<Enum<?>> {
//        FlexibleEnumSerializer() {
//            super(new TypeToken<>() {});
//        }
//
//        @SuppressWarnings({"unchecked", "rawtypes"})
//        @Override
//        public Enum<?> deserialize(Type type, Object obj) throws SerializationException {
//            String enumConstant = obj.toString();
//            try {
//                return parseEnum(enumConstant, (Class) type);
//            } catch (IllegalArgumentException | NullPointerException e) {
//                throw new SerializationException(type, "Invalid enum constant provided, expected a value of enum, got " + enumConstant);
//            }
//        }
//
//        @Override
//        protected Object serialize(Enum<?> item, Predicate<Class<?>> typeSupported) {
//            return item.name();
//        }
//    }
//
//    private static final ScalarSerializer<LocalDateTime> LOCAL_DATE_TIME = TypeSerializer.of(LocalDateTime.class,
//            // serialize
//            (time, isNative) -> time.toString(),
//            // deserialize
//            input -> {
//                if (input == null) {
//                    return null;
//                } else if (input instanceof Integer num) {
//                    return num != -1 ? LocalDateTime.ofEpochSecond(num, 0, ZoneOffset.UTC) : null;
//                } else {
//                    try {
//                        return LocalDateTime.parse(input.toString());
//                    } catch (DateTimeParseException e) {
//                        throw new SerializationException(e);
//                    }
//                }
//            }
//    );
}
