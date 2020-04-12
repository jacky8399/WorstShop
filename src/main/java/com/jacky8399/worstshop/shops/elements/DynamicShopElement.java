package com.jacky8399.worstshop.shops.elements;

import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopCondition;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.actions.IParentElementReader;
import com.jacky8399.worstshop.shops.conditions.ConditionPermission;
import com.jacky8399.worstshop.shops.elements.dynamic.AnimationShopElement;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DynamicShopElement extends ShopElement {
    public List<Action> actions;
    public ShopCondition condition;
    public static DynamicShopElement fromYaml(Map<String, Object> yaml) {
        DynamicShopElement inst;
        String preset = (String) yaml.get("preset");
        switch (preset) {
            case "animation": {
                inst = new AnimationShopElement(yaml);
            }
            break;
            default:
                throw new IllegalArgumentException(preset + " is not a valid preset!");
        }
        inst.owner = ParseContext.findLatest(Shop.class);
        ParseContext.pushContext(inst);

        // Permissions
        inst.condition = new ShopCondition();
        if (yaml.containsKey("view-perm")) {
            inst.condition.add(ConditionPermission.fromPermString((String) yaml.get("view-perm")));
        }

        if (yaml.containsKey("condition")) {
            inst.condition.add(ShopCondition.parseFromYaml((Map<String, Object>) yaml.get("condition")));
        }

        // Action parsing
        inst.actions = ((List<?>) yaml.getOrDefault("actions", Collections.emptyList())).stream()
                .map(obj -> obj instanceof Map ?
                        Action.fromYaml((Map<String, Object>) obj) :
                        Action.fromCommand(obj.toString()))
                .filter(Objects::nonNull).collect(Collectors.toList());

        inst.actions.stream().filter(action -> action instanceof IParentElementReader)
                .forEach(action -> ((IParentElementReader) action).readElement(inst));

        ParseContext.popContext();
        return inst;
    }

    @Override
    public ItemStack createStack(Player player) {
        return ItemBuilder.of(Material.BARRIER).name(ChatColor.DARK_RED + "DYNAMIC").build();
    }
}
