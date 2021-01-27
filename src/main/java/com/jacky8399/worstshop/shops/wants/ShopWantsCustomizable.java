package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class ShopWantsCustomizable extends ShopWants {
    private final ShopElement element;
    transient boolean copyFromParent;
    public ShopWantsCustomizable(@Nullable ShopWantsCustomizable carryOver) {
        this.element = carryOver != null ? carryOver.element : null;
        this.copyFromParent = carryOver != null && carryOver.copyFromParent;
    }

    public ShopWantsCustomizable(@Nullable Map<String, Object> yaml) {
        element = yaml != null && yaml.containsKey("display") ?
                fromYaml((Map<String, Object>) yaml.get("display")) : null;
    }

    public ShopElement fromYaml(Map<String, Object> yaml) {
        if (yaml.containsKey("from") && ((String)yaml.get("from")).equalsIgnoreCase("parent")) {
            // copy from parent
            copyFromParent = true; // for serialization
            ShopElement parent = ParseContext.findLatest(ShopElement.class);
            return parent != null ? parent.clone() : null;
        } else {
            return ShopElement.fromConfig(new Config(yaml));
        }
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        if (element != null) {
            // sanitize element
            element.fill = ShopElement.FillType.NONE;
            element.itemPositions = Collections.singletonList(position.pos);
            element.actions = Collections.emptyList();
            return element;
        } else {
            return getDefaultElement(position);
        }
    }

    @Override
    public boolean isElementDynamic() {
        return element instanceof DynamicShopElement;
    }

    public ShopElement getDefaultElement(TransactionType position) {
        StaticShopElement elem = StaticShopElement.fromStack(getDefaultStack());
        elem.fill = ShopElement.FillType.NONE;
        elem.itemPositions = Collections.singletonList(position.pos);
        return elem;
    }

    public ItemStack getDefaultStack() {
        return null;
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        if (copyFromParent)
            map.put("display", Collections.singletonMap("from", "parent"));
        else
            map.put("display", element.toMap(new HashMap<>()));
        return map;
    }
}
