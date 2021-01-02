package com.jacky8399.worstshop.shops.elements.dynamic;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.content.InventoryContents;
import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AnimationShopElement extends DynamicShopElement {
    int intervalInTicks;
    // to prevent overlapping with other AnimationShopElements
    private String self = UUID.randomUUID().toString();
    ArrayList<ShopElement> elements;
    public AnimationShopElement(Config config) {
        intervalInTicks = config.find("interval", Integer.class).orElse(1);
        elements = Lists.newArrayList();
        ParseContext.pushContext(this);
        config.getList("elements", Config.class).stream().map(ShopElement::fromConfig).forEach(elements::add);
        ParseContext.popContext();
        Validate.notEmpty(elements, "Elements cannot be empty!");
    }

    private int wrapIndexOffset(int orig, int idx) {
        return (elements.size() + orig + idx) % elements.size();
    }

    @Override
    public void populateItems(Player player, InventoryContents contents, Shop.PaginationHelper pagination) {
        if (!condition.test(player)) {
            return;
        }
        int animationSequence = contents.property(self + "_animationSequence", 0);
        int ticksPassed = contents.property(self + "_ticksPassed", 0);
        if (++ticksPassed >= intervalInTicks) {
            // next element
            animationSequence = wrapIndexOffset(animationSequence, 1);
            contents.setProperty(self + "_animationSequence", animationSequence);
            contents.setProperty(self + "_ticksPassed", 0);
        } else {
            contents.setProperty(self + "_ticksPassed", ticksPassed);
        }
        // clone the element to add our actions
        ShopElement current = elements.get(animationSequence).clone();
        // override the list as the cloned element still has the same ref to list of action
        List<Action> newAction = Lists.newArrayList(actions);
        if (current instanceof StaticShopElement) {
            newAction.addAll(((StaticShopElement) current).actions);
            ((StaticShopElement) current).actions = newAction;
        } else {
            newAction.addAll(((DynamicShopElement) current).actions);
            ((DynamicShopElement) current).actions = newAction;
        }
        current.populateItems(player, contents, pagination);
    }
}
