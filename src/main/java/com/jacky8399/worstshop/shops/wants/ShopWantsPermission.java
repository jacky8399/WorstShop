package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemBuilder;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ShopWantsPermission extends ShopWantsCustomizable {

    public static final LuckPerms PERMS;

    static {
        if (WorstShop.get().permissions == null) {
            throw new IllegalStateException("LuckPerms not found!");
        }
        PERMS = WorstShop.get().permissions;
    }

    Node permissionNode;
    String permissionDisplay;
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

    public ShopWantsPermission(Map<String, Object> yaml) {
        super(yaml);
        if (PERMS == null)
            throw new IllegalStateException();
        NodeBuilder builder = null;
        if (yaml.containsKey("permission")) {
            String permName = (String) yaml.get("permission");
            builder = Node.builder(permName);
            permissionDisplay = permName;
            permType = PermissionType.PERMISSION;
        } else if (yaml.containsKey("group")) {
            String groupName = (String) yaml.get("group");
            builder = InheritanceNode.builder(groupName);
            permissionDisplay = groupName;
            permType = PermissionType.GROUP;
        } else if (yaml.containsKey("meta")) {
            Map<String, Object> innerData = (Map<String,Object>) yaml.get("meta");
            builder = MetaNode.builder(
                    (String) innerData.get("key"), (String) innerData.get("value")
            );
            permissionDisplay = innerData.get("key") + ": " + innerData.get("value");
            permType = PermissionType.META;
        } else if (yaml.containsKey("prefix")) {
            Map<String, Object> innerData = (Map<String,Object>) yaml.get("prefix");
            String prefix = ConfigHelper.translateString((String) innerData.get("prefix"));
            builder = PrefixNode.builder(
                    prefix, (int) innerData.getOrDefault("priority", 100)
            );
            permissionDisplay = prefix;
            permType = PermissionType.PREFIX;
        } else if (yaml.containsKey("suffix")) {
            Map<String, Object> innerData = (Map<String,Object>) yaml.get("suffix");
            String suffix = ConfigHelper.translateString((String) innerData.get("suffix"));
            builder = SuffixNode.builder(
                    suffix, (int) innerData.getOrDefault("priority", 100)
            );
            permissionDisplay = suffix;
            permType = PermissionType.SUFFIX;
        }

        // check completeness
        if (builder == null) {
            throw new IllegalArgumentException("Incomplete permission node");
        }

        // toggle
        builder.value((boolean) yaml.getOrDefault("value", true));
        // expiry
        if (yaml.containsKey("duration")) {
            durationInSeconds = (int) yaml.get("duration");
            durationShouldAppend = (boolean) yaml.getOrDefault("duration-append", true);
            // set it later
        }
        revokePermission = (boolean) yaml.getOrDefault("revoke", false);

        permissionNode = builder.build();
    }

    public ShopWantsPermission(ShopWantsPermission old, double multiplier) {
        super(old);
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
    public ShopWants multiply(double multiplier) {
        return new ShopWantsPermission(this, multiplier);
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
    public String getPlayerResult(Player player, ElementPosition position) {
        return formatPermission() + ": " + permissionNode.getValue();
    }

    public Node createNodeWithDuration(Player player) {
        NodeBuilder builder = permissionNode.toBuilder();
        if (durationInSeconds > 0)
                builder.expiry((long) (durationInSeconds * multiplier), TimeUnit.SECONDS);
        return builder.build();
    }

    @Override
    public void deduct(Player player) {
        if (revokePermission) {
            ContextManager ctx = PERMS.getContextManager();
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
    public ItemStack getDefaultStack() {
        return ItemBuilder.of(Material.PAPER).name(formatPermission()).build();
    }
}
