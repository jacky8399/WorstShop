package com.jacky8399.worstshop.helper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Wrappers of various convenient methods of Paper to ensure compatibility on Spigot
 */
public class PaperHelper {
    public static boolean isPaper;

    public static void sendActionBar(Player player, BaseComponent[] components) {
        if (isPaper) {
            player.sendActionBar(components);
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
        }
    }

    public static String getItemName(ItemStack stack) {
        if (isPaper) {
            return stack.getI18NDisplayName();
        } else {
            // return name from material type
            var temp = stack.getType().toString().replace('_', ' ');

            return Character.toUpperCase(temp.charAt(0)) + temp.substring(1);
        }
    }

    public static CommandMap getCommandMap() {
        if (isPaper) {
            return Bukkit.getCommandMap();
        } else {
            // use reflection
            try {
                Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
                field.setAccessible(true);
                return (CommandMap) field.get(Bukkit.getServer());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Command> getKnownCommands(CommandMap map) {
        if (isPaper) {
            return map.getKnownCommands();
        } else {
            // use reflection
            try {
                if (map instanceof SimpleCommandMap) {
                    Field field = map.getClass().getDeclaredField("knownCommands");
                    field.setAccessible(true);
                    return (Map<String, Command>) field.get(map);
                } else {
                    return null;
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }
        }
    }

    public static abstract class GameProfile {
        @Nullable
        public abstract UUID getUUID();
        @Nullable
        public abstract String getName();
        public abstract CompletableFuture<Void> completeProfile();
        // for serialization purposes
        public boolean skinNotLoaded = true;
        public abstract void setSkin(String base64);
        public abstract String getSkin();
        public abstract boolean hasSkin();
        public boolean equals(Object other) {
            if (!(other instanceof GameProfile profile)) {
                return false;
            }
            return Objects.equals(getUUID(), profile.getUUID()) && Objects.equals(getName(), profile.getName());
        }
    }

    // TODO use Bukkit implementation
    public static class PaperGameProfile extends GameProfile {
        PlayerProfile obj;
        public PaperGameProfile(UUID uuid, String name) {
            if (uuid == null && (name == null || name.isBlank())) {
                throw new IllegalArgumentException("Name and ID cannot both be blank");
            }
            obj = Bukkit.createProfileExact(uuid, name);
        }

        private PaperGameProfile(Object impl) {
            if (PaperHelper.isPaper) {
                obj = (PlayerProfile) impl;
            }
        }

        @Override
        public UUID getUUID() {
            return obj.getId();
        }

        @Override
        public String getName() {
            return obj.getName();
        }

        @Override
        public CompletableFuture<Void> completeProfile() {
            // copy unset properties to preserve worstshop_skull
            List<ProfileProperty> properties = List.copyOf(obj.getProperties());
            return CompletableFuture.runAsync(() -> {
                obj.complete();
                for (ProfileProperty property : properties) {
                    if (!obj.hasProperty(property.getName())) {
                        obj.setProperty(property);
                    }
                }
                skinNotLoaded = false;
            });
        }

        @Override
        public void setSkin(String base64) {
            obj.setProperty(new ProfileProperty("textures", base64, null));
        }

        @Override
        public String getSkin() {
            return obj.getProperties().stream()
                    .filter(property -> property.getName().equals("textures"))
                    .findFirst()
                    .map(ProfileProperty::getValue)
                    .orElse(null);
        }

        @Override
        public boolean hasSkin() {
            return obj.hasTextures();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PaperGameProfile && ((PaperGameProfile) other).obj.equals(obj);
        }
    }

    public static GameProfile createProfile(UUID uuid, String name) {
        return new PaperGameProfile(uuid, name);
    }

    public static void setSkullMetaProfile(SkullMeta meta, GameProfile profile) {
        meta.setPlayerProfile(((PaperGameProfile) profile).obj);
    }

    @Nullable
    public static GameProfile getSkullMetaProfile(SkullMeta meta) {
        Object thing = meta.getPlayerProfile();
        if (thing != null)
            return new PaperGameProfile(thing);
        return null;
    }

    public static void checkIsPaper() {
        try {
            Class.forName("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent");
            isPaper = true;
        } catch (ClassNotFoundException ex) {
            isPaper = false;
            throw new IllegalStateException("Please use Paper");
        }
    }
}
