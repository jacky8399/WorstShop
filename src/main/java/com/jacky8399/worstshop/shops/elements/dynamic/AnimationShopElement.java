package com.jacky8399.worstshop.shops.elements.dynamic;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.ElementContext;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.content.InventoryContents;
import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AnimationShopElement extends DynamicShopElement {
    int intervalInTicks;
    // to prevent overlapping with other AnimationShopElements
    private final String self = UUID.randomUUID().toString();
    ArrayList<ShopElement> elements;
    public AnimationShopElement(Config config) {
        intervalInTicks = config.find("interval", Integer.class).orElse(1);
        elements = new ArrayList<>();
        ParseContext.pushContext(this);
        config.getList("elements", Config.class).stream().map(ShopElement::fromConfig).forEach(elements::add);
        ParseContext.popContext();
        Validate.notEmpty(elements, "Elements cannot be empty!");
    }

    public AnimationShopElement(int interval, ShopElement... elements) {
        intervalInTicks = interval;
        this.elements = Lists.newArrayList(elements);
    }

    public AnimationShopElement(int interval, Collection<? extends ShopElement> elements) {
        intervalInTicks = interval;
        this.elements = new ArrayList<>(elements);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        super.toMap(map);
        map.put("interval", intervalInTicks);
        map.put("elements", elements.stream().map(element -> element.toMap(new HashMap<>())).collect(Collectors.toList()));
        return map;
    }

    @Override
    public void populateItems(Player player, InventoryContents contents, ElementContext pagination) {
        if (!condition.test(player)) {
            return;
        }
        int animationSequence = contents.property(self + "_animationSequence", 0);
        int ticksPassed = contents.property(self + "_ticksPassed", 0);
        if (++ticksPassed >= intervalInTicks) {
            // next element
            animationSequence = (elements.size() + animationSequence + 1) % elements.size();
            contents.setProperty(self + "_animationSequence", animationSequence);
            contents.setProperty(self + "_ticksPassed", 0);
        } else {
            contents.setProperty(self + "_ticksPassed", ticksPassed);
        }
        // clone the element to add our actions
        ShopElement current = elements.get(animationSequence).clone();
        // if this element has a more specific pos, use that
        if (filler != DefaultSlotFiller.NONE || itemPositions != null) {
            current.filler = filler;
            current.itemPositions = itemPositions != null ? new ArrayList<>(itemPositions) : null;
        }
        // override the list as the cloned element still has the same ref to list of action
        List<Action> newAction = new ArrayList<>(actions);
        newAction.addAll(current.actions);
        current.actions = newAction;
        current.populateItems(player, contents, pagination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, intervalInTicks);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnimationShopElement))
            return false;
        AnimationShopElement other = (AnimationShopElement) obj;
        return other.elements.equals(elements) && other.intervalInTicks == intervalInTicks;
    }

    @Override
    public String toString() {
        return "animation (every " + intervalInTicks + " ticks; elements = " + elements.stream().map(ShopElement::toString).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public AnimationShopElement clone() {
        AnimationShopElement element = (AnimationShopElement) super.clone();
        element.elements = elements.stream().map(ShopElement::clone).collect(Collectors.toCollection(ArrayList::new));
        return element;
    }
}
