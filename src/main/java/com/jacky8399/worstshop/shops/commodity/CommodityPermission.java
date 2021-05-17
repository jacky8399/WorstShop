package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.DateTimeUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.types.*;
import net.luckperms.api.query.QueryOptions;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CommodityPermission extends Commodity {

    public static final LuckPerms PERMS;

    static {
        if (WorstShop.get().permissions == null) {
            throw new IllegalStateException("LuckPerms not found!");
        }
        PERMS = WorstShop.get().permissions;
    }

    Node permissionNode;
    transient String permissionDisplay;
    int durationInSeconds;
    double multiplier = 1;
    // whether the duration should be added to the existing duration
    boolean durationShouldAppend;


    public enum PermissionType {
        PERMISSION, GROUP, META, PREFIX, SUFFIX;

        public String getLocaleKey() {
            return I18n.Keys.MESSAGES_KEY + "shops.wants.permissions." + this.toString().toLowerCase();
        }
    }

    /**
     * Whether the permission is actually a group name.
     */
    PermissionType permType;
    boolean revokePermission;

    public CommodityPermission(Config config) {
        super();
        if (PERMS == null)
            throw new IllegalStateException();
        NodeBuilder<?, ?> builder = null;
        if (config.has("permission")) {
            String permName = config.get("permission", String.class);
            builder = PermissionNode.builder(permName);
            permissionDisplay = permName;
            permType = PermissionType.PERMISSION;
        } else if (config.has("group")) {
            String groupName = config.get("group", String.class);
            builder = InheritanceNode.builder(groupName);
            permissionDisplay = groupName;
            permType = PermissionType.GROUP;
        } else if (config.has("meta")) {
            Config innerData = config.get("meta", Config.class);
            String key = innerData.get("key", String.class),
                    value = innerData.get("value", Object.class).toString(); // allow booleans and numbers
            builder = MetaNode.builder(key, value);
            permissionDisplay = key + ": " + value;
            permType = PermissionType.META;
        } else if (config.has("prefix")) {
            Object prefixData = config.get("prefix", String.class, Config.class);
            String prefix;
            if (prefixData instanceof Config) {
                Config innerData = (Config) prefixData;
                prefix = ConfigHelper.translateString(innerData.get("prefix", String.class));
                builder = PrefixNode.builder(
                        prefix, innerData.find("priority", Integer.class).orElse(100)
                );
            } else {
                prefix = ConfigHelper.translateString((String) prefixData);
                builder = PrefixNode.builder(prefix, 100);
            }
            permissionDisplay = prefix;
            permType = PermissionType.PREFIX;
        } else if (config.has("suffix")) {
            Object suffixData = config.get("suffix", String.class, Config.class);
            String suffix;
            if (suffixData instanceof Config) {
                Config innerData = (Config) suffixData;
                suffix = ConfigHelper.translateString(innerData.get("suffix", String.class));
                builder = PrefixNode.builder(
                        suffix, innerData.find("priority", Integer.class).orElse(100)
                );
            } else {
                suffix = ConfigHelper.translateString((String) suffixData);
                builder = SuffixNode.builder(suffix, 100);
            }
            permissionDisplay = suffix;
            permType = PermissionType.SUFFIX;
        }

        // check completeness
        if (builder == null) {
            throw new IllegalArgumentException("Incomplete permission node");
        }

        // toggle
        if (permType != PermissionType.META)
            builder.value(config.find("value", Boolean.class).orElse(true));
        // expiry
        config.find("duration", Integer.class, String.class).ifPresent(obj -> {
            if (obj instanceof Integer)
                durationInSeconds = (Integer) obj;
            else
                durationInSeconds = (int) DateTimeUtils.parseTimeStr((String) obj).getSeconds();
            durationShouldAppend = config.find("duration-append", Boolean.class).orElse(true);
            // set duration later
        });
        revokePermission = config.find("revoke", Boolean.class).orElse(false);

        permissionNode = builder.build();
    }

    public CommodityPermission(CommodityPermission old, double multiplier) {
        permissionNode = old.permissionNode;
        permType = old.permType;
        durationInSeconds = old.durationInSeconds;
        this.multiplier = multiplier * old.multiplier;
        durationShouldAppend = old.durationShouldAppend;
        revokePermission = old.revokePermission;
        permissionDisplay = old.permissionDisplay;
    }

    @Override
    public boolean canMultiply() {
        return durationInSeconds > 0;
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityPermission(this, multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return PERMS.getUserManager().getUser(player.getUniqueId()).getCachedData().permissionData()
                .get(QueryOptions.defaultContextualOptions()).checkPermission(permissionNode.getKey()).asBoolean() == permissionNode.getValue();
    }

    @Override
    public String getPlayerTrait(Player player) {
        return formatPermission() + ":" + canAfford(player);
    }

    @Override
    public String getPlayerResult(Player player, TransactionType position) {
        return formatPermission() + ": " + permissionNode.getValue();
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "perm");
        if (!permissionNode.getValue())
            map.put("value", false);
        if (revokePermission)
            map.put("revoke", true);
        switch (permType) {
            case META: {
                MetaNode node = (MetaNode) permissionNode;
                map.put("key", node.getKey());
                map.put("value", node.getMetaValue());
                break;
            }
            case GROUP: {
                InheritanceNode node = (InheritanceNode) permissionNode;
                map.put("group", node.getGroupName());
                break;
            }
            case PREFIX:
            case SUFFIX: {
                ChatMetaNode<?, ?> node = (ChatMetaNode<?, ?>) permissionNode;
                map.put(permType == PermissionType.PREFIX ? "prefix" : "suffix", ConfigHelper.untranslateString(node.getMetaValue()));
                break;
            }
            case PERMISSION: {
                PermissionNode node = (PermissionNode) permissionNode;
                map.put("permission", node.getPermission());
                break;
            }
        }
        return map;
    }

    public Node createNodeWithDuration(Player player) {
        NodeBuilder<?,?> builder = permissionNode.toBuilder();
        if (durationInSeconds > 0)
                builder.expiry((long) (durationInSeconds * multiplier), TimeUnit.SECONDS);
        return builder.build();
    }

    @Override
    public void deduct(Player player) {
        if (revokePermission) {
            Node permissionNode = createNodeWithDuration(player);
            User user = PERMS.getUserManager().getUser(player.getUniqueId());
            user.data().remove(permissionNode);

            // save
            PERMS.getUserManager().saveUser(user);
        }
    }

    @Override
    public double grantOrRefund(Player player) {
        Node permissionNode = createNodeWithDuration(player);
        User user = PERMS.getUserManager().getUser(player.getUniqueId());

        DataMutateResult result;
        if (revokePermission) {
            result = user.data().remove(permissionNode);
        } else {
            result = user.data().add(permissionNode, durationShouldAppend ?
                    TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING :
                    TemporaryNodeMergeStrategy.REPLACE_EXISTING_IF_DURATION_LONGER)
                    .getResult();
        }

        // save
        PERMS.getUserManager().saveUser(user);
        return result.wasSuccessful() ? 0 : multiplier;
    }

    public String formatPermission() {
        return ChatColor.WHITE + I18n.translate(permType.getLocaleKey(), permissionDisplay);
    }

    @Override
    public ShopElement createElement(TransactionType pos) {
        return pos.createElement(ItemBuilder.of(Material.PAPER).name(formatPermission()).build());
    }

    @Override
    public int hashCode() {
        return Objects.hash(permissionNode, durationInSeconds, durationShouldAppend, revokePermission, multiplier);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommodityPermission))
            return false;
        CommodityPermission other = (CommodityPermission) obj;
        return other.permissionNode.equals(permissionNode) && other.durationInSeconds == durationInSeconds &&
                other.durationShouldAppend == durationShouldAppend && other.revokePermission == revokePermission;
    }

    @Override
    public String toString() {
        return "[" + (revokePermission ? "give/take " : "give ") + permType.name() + " " + permissionDisplay +
                (durationInSeconds > 0 ? " for " + (durationShouldAppend ? "+" : "") + durationInSeconds + "s" : "");
    }
}
