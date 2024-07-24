package com.jacky8399.worstshop.helper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Wrappers of various convenient methods of Paper to ensure compatibility on Spigot
 */
public class PaperHelper {
    @Deprecated
    public static String getItemName(ItemStack stack) {
        return stack.getI18NDisplayName();
    }

    // thin wrapper around paper profiles
    public record GameProfile(PlayerProfile profile) {
        public UUID getUUID() {
            return profile.getId();
        }

        public String getName() {
            return profile.getName();
        }

        public CompletableFuture<Void> completeProfile() {
            // copy unset properties to preserve worstshop_skull
            List<ProfileProperty> properties = List.copyOf(profile.getProperties());
            return CompletableFuture.runAsync(() -> {
                profile.complete();
                for (ProfileProperty property : properties) {
                    if (!profile.hasProperty(property.getName())) {
                        profile.setProperty(property);
                    }
                }
            });
        }

        public void setSkin(String base64) {
            profile.setProperty(new ProfileProperty("textures", base64, null));
        }

        public String getSkin() {
            return profile.getProperties().stream()
                    .filter(property -> property.getName().equals("textures"))
                    .findFirst()
                    .map(ProfileProperty::getValue)
                    .orElse(null);
        }

        public boolean hasSkin() {
            return profile.hasTextures();
        }
    }

    public static GameProfile createProfile(UUID uuid, String name) {
        return new GameProfile(Bukkit.createProfile(uuid, name));
    }

    public static void setSkullMetaProfile(SkullMeta meta, GameProfile profile) {
        meta.setPlayerProfile(profile.profile);
    }

    @Nullable
    public static GameProfile getSkullMetaProfile(SkullMeta meta) {
        PlayerProfile thing = meta.getPlayerProfile();
        if (thing != null)
            return new GameProfile(thing);
        return null;
    }
}
