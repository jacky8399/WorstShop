package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * {@link CommodityPermission} but worse
 */
public class CommodityPermissionVault extends Commodity {
    public static final Permission PERMISSION;
    public static final Chat CHAT;

    static {
        WorstShop shop = WorstShop.get();
        if (shop.vaultPermissions == null)
            throw new IllegalStateException("Vault permission plugin not found");
        PERMISSION = shop.vaultPermissions.getProvider();
        if (shop.vaultChat != null)
            CHAT = shop.vaultChat.getProvider();
        else
            CHAT = null;
    }

    transient String permission;
    Object value;


    public enum PermissionPlugin {
        PERMISSION, CHAT
    }

    public enum PermissionType {
        PERMISSION(PermissionPlugin.PERMISSION), GROUP(PermissionPlugin.PERMISSION),
        META(PermissionPlugin.CHAT), PREFIX(PermissionPlugin.CHAT), SUFFIX(PermissionPlugin.CHAT);
        public final PermissionPlugin plugin;
        PermissionType(PermissionPlugin plugin) {
            this.plugin = plugin;
        }

        public String getLocaleKey() {
            return I18n.Keys.MESSAGES_KEY + "shops.wants.permissions." + this.toString().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Whether the permission is actually a group name.
     */
    PermissionType permType;
    boolean revokePermission;

    public CommodityPermissionVault(Config config) {
        super();
        if (config.has("permission")) {
            permission = config.get("permission", String.class);
            permType = PermissionType.PERMISSION;
        } else if (config.has("group")) {
            permission = config.get("group", String.class);
            permType = PermissionType.GROUP;
        } else if (config.has("meta")) {
            Config innerData = config.get("meta", Config.class);
            permission = innerData.get("key", String.class);
            // allow booleans and numbers
            value = innerData.get("value", String.class, Boolean.class, Number.class).toString();
            permType = PermissionType.META;
        } else if (config.has("prefix")) {
            Object prefixData = config.get("prefix", String.class, Config.class);
            String prefix;
            if (prefixData instanceof Config innerData) {
                prefix = ConfigHelper.translateString(innerData.get("prefix", String.class));
                value = innerData.find("priority", Integer.class).orElse(100);
            } else {
                prefix = ConfigHelper.translateString((String) prefixData);
                value = 100;
            }
            permission = prefix;
            permType = PermissionType.PREFIX;
        } else if (config.has("suffix")) {
            Object suffixData = config.get("suffix", String.class, Config.class);
            String suffix;
            if (suffixData instanceof Config innerData) {
                suffix = ConfigHelper.translateString(innerData.get("suffix", String.class));
                value = innerData.find("priority", Integer.class).orElse(100);
            } else {
                suffix = ConfigHelper.translateString((String) suffixData);
                value = 100;
            }
            permission = suffix;
            permType = PermissionType.SUFFIX;
        }

        // check completeness
        if (permission == null) {
            throw new IllegalArgumentException("Incomplete permission");
        }

        // toggle
        if (permType == PermissionType.PERMISSION || permType == PermissionType.GROUP)
            value = config.find("value", Boolean.class).orElse(true);
        // expiry
        config.find("duration", Integer.class, String.class).ifPresent(obj -> {
            WorstShop.get().logger.warning("Vault does not support temporary permissions.");
            WorstShop.get().logger.warning("Offending commodity: " + config.getPath("duration"));
        });
        revokePermission = config.find("revoke", Boolean.class).orElse(false);
    }

    public CommodityPermissionVault(CommodityPermissionVault old) {
        permType = old.permType;
        revokePermission = old.revokePermission;
        permission = old.permission;
        value = old.value;
    }

    @Override
    public boolean canMultiply() {
        return false;
    }

    @Override
    public boolean canAfford(Player player) {
        if (permType.plugin == PermissionPlugin.CHAT && CHAT == null)
            return false;

        switch (permType) {
            case PERMISSION:
                return PERMISSION.has(player, permission) == (Boolean) value;
            case GROUP:
                return PERMISSION.playerInGroup(player, permission) == (Boolean) value;
            case META: {
                Class<?> clazz = value.getClass();
                Object val;
                if (clazz == Boolean.class)
                    val = CHAT.getPlayerInfoBoolean(player, permission, false);
                else if (clazz == Integer.class)
                    val = CHAT.getPlayerInfoInteger(player, permission, 0);
                else if (clazz == Double.class)
                    val = CHAT.getPlayerInfoDouble(player, permission, 0);
                else
                    val = CHAT.getPlayerInfoString(player, permission, null);
                return value.equals(val);
            }
            case PREFIX:
                return value.equals(CHAT.getPlayerPrefix(player));
            case SUFFIX:
                return value.equals(CHAT.getPlayerSuffix(player));
        }
        return false;
    }

    @Override
    public List<? extends Component> playerTrait(Player player) {
        return List.of(formatPermissionComponent(canAfford(player)));
    }

    @Override
    public List<? extends Component> playerResult(@Nullable Player player, TransactionType position) {
        return List.of(formatPermissionComponent(value));
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "perm");
        if (revokePermission)
            map.put("revoke", true);
        switch (permType) {
            case META -> {
                map.put("key", permission);
                map.put("value", value);
            }
            case GROUP -> {
                map.put("group", permission);
                map.put("value", value);
            }
            case PREFIX, SUFFIX -> {
                String permTypeName = permType.name().toLowerCase(Locale.ROOT);
                Map<String, Object> innerMap = new LinkedHashMap<>();
                innerMap.put(permTypeName, ConfigHelper.untranslateString(permission));
                innerMap.put("priority", value);
                map.put(permTypeName, innerMap);
            }
            case PERMISSION -> {
                map.put("permission", permission);
                map.put("value", value);
            }
        }
        return map;
    }

    @Override
    public void deduct(Player player) {
        if (revokePermission) {
            if (permType.plugin == PermissionPlugin.CHAT && CHAT == null)
                return;

            switch (permType) {
                case PERMISSION:
                    PERMISSION.playerRemove(player, permission);
                case GROUP:
                    PERMISSION.playerRemoveGroup(player, permission);
                case META:
                    CHAT.setPlayerInfoString(player, permission, null);
                case PREFIX:
                    CHAT.setPlayerPrefix(player, null);
                case SUFFIX:
                    CHAT.setPlayerSuffix(player, null);
            }
        }
    }

    @Override
    public double grantOrRefund(Player player) {
        if (permType.plugin == PermissionPlugin.CHAT && CHAT == null)
            return 1;

        switch (permType) {
            case PERMISSION:
                return PERMISSION.playerAdd(player, permission) ? 0 : 1;
            case GROUP:
                return PERMISSION.playerAddGroup(player, permission) ? 0 : 1;
            case META: {
                Class<?> clazz = value.getClass();
                if (clazz == Boolean.class)
                    CHAT.setPlayerInfoBoolean(player, permission, (Boolean) value);
                else if (clazz == Integer.class)
                    CHAT.setPlayerInfoInteger(player, permission, (Integer) value);
                else if (clazz == Double.class)
                    CHAT.setPlayerInfoDouble(player, permission, (Double) value);
                else
                    CHAT.setPlayerInfoString(player, permission, value.toString());
                return 0;
            }
            case PREFIX:
                CHAT.setPlayerPrefix(player, permission);
                return 0;
            case SUFFIX:
                CHAT.setPlayerSuffix(player, permission);
                return 0;
        }
        return 1;
    }

    public Component formatPermissionComponent() {
        return I18n.translateAsComponent(permType.getLocaleKey(), permission + "=" + value).colorIfAbsent(NamedTextColor.WHITE);
    }

    public Component formatPermissionComponent(Object value) {
        return Component.textOfChildren(
                I18n.translateAsComponent(permType.getLocaleKey(), permission + "=" + value),
                Component.text(": " + value)
        ).color(NamedTextColor.WHITE);
    }

    @Override
    public ShopElement createElement(TransactionType pos) {
        return pos.createElement(ItemBuilder.of(Material.PAPER).name(formatPermissionComponent()).build());
    }

    @Override
    public int hashCode() {
        return Objects.hash(permType, permission, value, revokePermission);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommodityPermissionVault other))
            return false;
        return other.permType == permType && other.permission.equals(permission) &&
                other.value.equals(value) && other.revokePermission == revokePermission;
    }

    @Override
    public String toString() {
        return "[" + (revokePermission ? "give/take " : "give ") + permType.name() + " " + permission;
    }
}
