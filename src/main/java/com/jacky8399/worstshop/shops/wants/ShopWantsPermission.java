package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemBuilder;
import me.lucko.luckperms.api.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ShopWantsPermission extends ShopWantsCustomizable {

    public static final LuckPermsApi PERMS;

    static {
        if (WorstShop.get().permissions == null) {
            throw new IllegalStateException("LuckPerms not found!");
        }
        PERMS = WorstShop.get().permissions;
    }

    Node permissionNode;
    String permissionDisplay;
    int durationInSeconds;
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
        NodeFactory factory = PERMS.getNodeFactory();
        Node.Builder builder = null;
        if (yaml.containsKey("permission")) {
            String permName = (String) yaml.get("permission");
            builder = factory.newBuilder(permName);
            permissionDisplay = permName;
            permType = PermissionType.PERMISSION;
        } else if (yaml.containsKey("group")) {
            String groupName = (String) yaml.get("group");
            builder = factory.makeGroupNode(groupName);
            permissionDisplay = groupName;
            permType = PermissionType.GROUP;
        } else if (yaml.containsKey("meta")) {
            Map<String, Object> innerData = (Map<String,Object>) yaml.get("meta");
            builder = factory.makeMetaNode(
                    (String) innerData.get("key"), (String) innerData.get("value")
            );
            permissionDisplay = innerData.get("key") + ": " + innerData.get("value");
            permType = PermissionType.META;
        } else if (yaml.containsKey("prefix")) {
            Map<String, Object> innerData = (Map<String,Object>) yaml.get("prefix");
            String prefix = ConfigHelper.translateString((String) innerData.get("prefix"));
            builder = factory.makePrefixNode(
                    (int) innerData.getOrDefault("priority", 100), prefix
            );
            permissionDisplay = prefix;
            permType = PermissionType.PREFIX;
        } else if (yaml.containsKey("suffix")) {
            Map<String, Object> innerData = (Map<String,Object>) yaml.get("suffix");
            String suffix = ConfigHelper.translateString((String) innerData.get("suffix"));
            builder = factory.makePrefixNode(
                    (int) innerData.getOrDefault("priority", 100), suffix
            );
            permissionDisplay = suffix;
            permType = PermissionType.SUFFIX;
        }

        // check completeness
        if (builder == null) {
            throw new IllegalArgumentException("Incomplete permission node");
        }

        // toggle
        builder.setValue((boolean) yaml.getOrDefault("value", true));
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
        durationInSeconds = (int) (old.durationInSeconds * multiplier);
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
        return PERMS.getUser(player.getUniqueId()).hasPermission(permissionNode).asBoolean();
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
        Node.Builder builder = PERMS.getNodeFactory().newBuilderFromExisting(permissionNode);
        if (durationInSeconds > 0)
                builder.setExpiry(durationInSeconds, TimeUnit.SECONDS);
        return builder.build();
    }

    @Override
    public void deduct(Player player) {
        if (revokePermission) {
            Node permissionNode = createNodeWithDuration(player);
            User user = PERMS.getUser(player.getUniqueId());
            user.unsetPermission(permissionNode);

            // save
            PERMS.getUserManager().saveUser(user);
        }
    }

    @Override
    public void grant(Player player) {
        Node permissionNode = createNodeWithDuration(player);
        User user = PERMS.getUser(player.getUniqueId());

        if (revokePermission)
            user.unsetPermission(permissionNode);
        else
            user.setPermission(permissionNode, durationShouldAppend ?
                    TemporaryMergeBehaviour.ADD_NEW_DURATION_TO_EXISTING :
                    TemporaryMergeBehaviour.REPLACE_EXISTING_IF_DURATION_LONGER);

        // save
        PERMS.getUserManager().saveUser(user);
    }

    public String formatPermission() {
        return ChatColor.WHITE + I18n.translate(permType.getLocaleKey(), permissionDisplay);
    }

    @Override
    public ItemStack getDefaultStack() {
        return ItemBuilder.of(Material.PAPER).name(formatPermission()).build();
    }
}
