package com.jacky8399.worstshop.helper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
            // lol
//            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
//                    "title actionbar " + player.getName() + " " + ComponentSerializer.toString(components));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
        }
    }

    public static String getItemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        if (isPaper) {
            return stack.getI18NDisplayName();
        } else {
            // return name from material type
            return WordUtils.capitalize(stack.getType().toString().replace('_', ' ').toLowerCase());
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

    public static class NmsGameProfile extends GameProfile {
        private static final GameProfileRepository repository;
        private static final MinecraftSessionService session;

        static {
            YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(Proxy.NO_PROXY, null);
            repository = auth.createProfileRepository();
            session = auth.createMinecraftSessionService();
        }

        private com.mojang.authlib.GameProfile obj;

        public NmsGameProfile(UUID uuid, String name) {
            if (uuid == null && StringUtils.isBlank(name)) {
                throw new IllegalArgumentException("Name and ID cannot both be blank");
            }
            try {
                obj = new com.mojang.authlib.GameProfile(uuid, name);
            } catch (Exception e) {
                throw new Error(e);
            }
        }

        public NmsGameProfile(Object nmsProfile) {
            obj = (com.mojang.authlib.GameProfile) nmsProfile;
        }

        @Override
        public UUID getUUID() {
            return obj.getId();
        }

        @Override
        public String getName() {
            return obj.getName();
        }

        private CompletableFuture<Void> completeUUID() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            final NmsGameProfile self = this;
            ProfileLookupCallback callback = new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(com.mojang.authlib.GameProfile gameProfile) {
                    self.obj = gameProfile;
                    future.complete(null);
                }

                @Override
                public void onProfileLookupFailed(com.mojang.authlib.GameProfile gameProfile, Exception e) {
                    self.obj = gameProfile;
                    future.complete(null);
                }
            };
            repository.findProfilesByNames(new String[]{getName()}, Agent.MINECRAFT, callback);
            return future;
        }

        @Override
        public CompletableFuture<Void> completeProfile() {
            if (!obj.isComplete()) {
                // fill UUID
                CompletableFuture<Void> uuidFilled;
                uuidFilled = getUUID() == null ? completeUUID() : CompletableFuture.completedFuture(null);

                return uuidFilled.thenRunAsync(() -> {
                    try {
                        obj = session.fillProfileProperties(obj, true);
                        skinNotLoaded = false;
                    } catch (Exception e) {
                        throw new Error(e);
                    }
                }).toCompletableFuture();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void setSkin(String base64) {
            PropertyMap propertyMap = obj.getProperties();
            propertyMap.put("skin", new Property("skin", base64, null));
        }

        @Override
        public String getSkin() {
            try {
                return obj.getProperties().get("skin").iterator().next().getValue();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public boolean hasSkin() {
            return obj.getProperties().containsKey("skin") || obj.isComplete();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NmsGameProfile && ((NmsGameProfile) other).obj.equals(obj);
        }
    }

    public static class PaperGameProfile extends GameProfile {
        PlayerProfile obj;
        public PaperGameProfile(UUID uuid, String name) {
            if (uuid == null && StringUtils.isBlank(name)) {
                throw new IllegalArgumentException("Name and ID cannot both be blank");
            }
            obj = Bukkit.createProfile(uuid, name);
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
            return CompletableFuture.runAsync(()->{
                obj.complete();
                skinNotLoaded = false;
            });
        }

        @Override
        public void setSkin(String base64) {
            obj.setProperty(new ProfileProperty("skin", base64, null));
        }

        @Override
        public String getSkin() {
            return obj.getProperties().stream()
                    .filter(property -> property.getName().equals("skin"))
                    .findFirst()
                    .map(ProfileProperty::getValue)
                    .orElse(null);
        }

        @Override
        public boolean hasSkin() {
            return obj.hasProperty("skin") || obj.hasTextures();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PaperGameProfile && ((PaperGameProfile) other).obj.equals(obj);
        }
    }

    public static GameProfile createProfile(UUID uuid, String name) {
        return isPaper ? new PaperGameProfile(uuid, name) : new NmsGameProfile(uuid, name);
    }

    public static void setSkullMetaProfile(SkullMeta meta, GameProfile profile) {
        if (profile instanceof PaperGameProfile) {
            meta.setPlayerProfile(((PaperGameProfile) profile).obj);
        } else {
            try {
                // set profile to GameProfile (NMS class) using reflection
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, ((NmsGameProfile) profile).obj);
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    @Nullable
    public static GameProfile getSkullMetaProfile(SkullMeta meta) {
        if (isPaper) {
            Object thing = meta.getPlayerProfile();
            if (thing != null)
                return new PaperGameProfile(thing);
            return null;
        } else {
            try {
                // get profile using reflection
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                Object thing = profileField.get(meta);
                if (thing != null)
                    return new NmsGameProfile(thing);
                return null;
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    public static void checkIsPaper() {
        try {
            isPaper = Class.forName("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent") != null;
        } catch (ClassNotFoundException ex) {
            isPaper = false;
        }
    }
}
