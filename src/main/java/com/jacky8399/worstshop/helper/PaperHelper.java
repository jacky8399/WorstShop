package com.jacky8399.worstshop.helper;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * Wrappers of various convenient methods of PaperSpigot to ensure compatibility on Spigot
 */
public class PaperHelper {
    public static boolean isPaper;

    public static String getItemName(ItemStack stack) {
        if (isPaper) {
            return stack.getI18NDisplayName();
        } else {
            ItemMeta meta = stack.getItemMeta();
            if (meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
            // return name from material type
            return StringUtils.capitalize(
                    stack.getType().toString().replace('_', ' ').toLowerCase()
            );
        }
    }

    public static CommandMap getCommandMap() {
        if (isPaper) {
            return Bukkit.getCommandMap();
        } else {
            // use reflection
            try {
                Field field = Server.class.getDeclaredField("commandMap");
                field.setAccessible(true);
                return (CommandMap) field.get(Bukkit.getServer());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }
        }
    }

    public static Map<String, Command> getKnownCommands(CommandMap map) {
        if (isPaper) {
            return map.getKnownCommands();
        } else {
            // use reflection
            try {
                Field field = map.getClass().getDeclaredField("knownCommands");
                field.setAccessible(true);
                return (Map<String, Command>) field.get(map);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }
        }
    }

    public static class GameProfile {
        Object nmsImpl;
        Object paperImpl;
        private static final Class<?> gameProfileClazz;

        static {
            try {
                gameProfileClazz = Class.forName("com.mojang.authlib.GameProfile");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        public GameProfile(UUID uuid, String name) {
            if (uuid == null && StringUtils.isBlank(name)) {
                throw new IllegalArgumentException("Name and ID cannot both be blank");
            }
            if (PaperHelper.isPaper) {
                paperImpl = Bukkit.createProfile(uuid, name);
            } else {
                try {
                    Constructor<?> gameProfileConstructor = gameProfileClazz.getConstructor(UUID.class, String.class);
                    nmsImpl = gameProfileConstructor.newInstance(uuid, name);
                } catch (Exception ex) {
                    // should never fail
                    throw new RuntimeException(ex);
                }
            }
        }

        private GameProfile(Object impl) {
            if (PaperHelper.isPaper) {
                // assume Paper impl
                paperImpl = impl;
            } else {
                nmsImpl  = impl;
            }
        }

        public UUID getUUID() {
            if (paperImpl != null) {
                return ((PlayerProfile) paperImpl).getId();
            } else if (nmsImpl != null) {
                try {
                    return (UUID) gameProfileClazz.getMethod("getId").invoke(nmsImpl);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("Invalid profile??");
            }
        }

        public String getName() {
            if (paperImpl != null) {
                return ((PlayerProfile) paperImpl).getName();
            } else if (nmsImpl != null) {
                try {
                    return (String) gameProfileClazz.getMethod("getName").invoke(nmsImpl);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("Invalid profile??");
            }
        }
    }

    public static GameProfile createProfile(UUID uuid, String name) {
        return new GameProfile(uuid, name);
    }

    public static void setSkullMetaProfile(SkullMeta meta, GameProfile profile) {
        if (isPaper) {
            meta.setPlayerProfile((PlayerProfile) profile.paperImpl);
        } else {
            try {
                // set profile to GameProfile (NMS class) using reflection
                Field profileField = SkullMeta.class.getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, profile.nmsImpl);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // lol
                throw new RuntimeException(e);
            }
        }
    }

    @Nullable
    public static GameProfile getSkullMetaProfile(SkullMeta meta) {
        if (isPaper) {
            Object thing = meta.getPlayerProfile();
            if (thing != null)
                return new GameProfile(thing);
        } else {
            try {
                // get profile using reflection
                Field profileField = SkullMeta.class.getDeclaredField("profile");
                profileField.setAccessible(true);
                Object thing = profileField.get(meta);
                if (thing != null)
                    return new GameProfile(thing);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // lol
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static void checkIsPaper() {
        try {
            isPaper = Class.forName("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent") != null;
        } catch (ClassNotFoundException ex) {
            isPaper = false;
        }
    }
}
