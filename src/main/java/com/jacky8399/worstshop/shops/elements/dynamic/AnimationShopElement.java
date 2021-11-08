package com.jacky8399.worstshop.shops.elements.dynamic;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.rendering.DefaultSlotFiller;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import org.apache.commons.lang.Validate;

import java.util.*;
import java.util.stream.Collectors;

public class AnimationShopElement extends DynamicShopElement {
    int intervalInTicks;
    List<ShopElement> elements;
    public AnimationShopElement(Config config) {
        intervalInTicks = config.find("interval", Integer.class).orElse(1);
        elements = new ArrayList<>();
        ParseContext.pushContext(this);
        config.getList("elements", Config.class).stream()
                .map(ShopElement::fromConfig)
                .forEach(elements::add);
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

    // to prevent overlapping with other AnimationShopElements
    private transient final String self = UUID.randomUUID().toString();

    private List<ShopElement> elementsCache;
    public void sanitize() {
        elementsCache = elements.stream()
                // to prevent unintended side effects
                .map(ShopElement::clone)
                .peek(elem -> {
                    // if this element has a more specific pos, use that
                    if (filler != DefaultSlotFiller.NONE || itemPositions != null) {
                        elem.filler = filler;
                        elem.itemPositions = itemPositions != null ? new ArrayList<>(itemPositions) : null;
                    }
                    if (actions.size() != 0) {
                        List<Action> newActions = new ArrayList<>(actions);
                        newActions.addAll(elem.actions);
                        elem.actions = newActions;
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<RenderElement> getRenderElement(ShopRenderer renderer) {
        if (elementsCache == null)
            sanitize();

        int animationSequence = renderer.property(self + "_animationSequence", 0);
        int ticksPassed = renderer.property(self + "_ticksPassed", 0);
//        renderer.player.sendMessage("[Animation] #Seq: " + animationSequence + ", ticks: " + ticksPassed + "/" + intervalInTicks);
        if (++ticksPassed >= intervalInTicks) {
            // next element
            animationSequence = (elements.size() + animationSequence + 1) % elements.size();
            renderer.setProperty(self + "_animationSequence", animationSequence);
            renderer.setProperty(self + "_ticksPassed", 0);
        } else {
            renderer.setProperty(self + "_ticksPassed", ticksPassed);
        }
        ShopElement current = elementsCache.get(animationSequence);
        List<RenderElement> items = current.getRenderElement(renderer);
        return items.stream()
                // hijack the render elements to reroute updates to animation
                .map(item -> new RenderElement(this, item.positions(), item.stack(), item.handler(), ShopElement.DYNAMIC_FLAGS))
                .collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, intervalInTicks);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnimationShopElement other))
            return false;
        return other.elements.equals(elements) && other.intervalInTicks == intervalInTicks;
    }

    @Override
    public String toString() {
        return "animation " + super.toString() + " (" + intervalInTicks + "t; elements=" + elements.stream().map(ShopElement::toString).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public AnimationShopElement clone() {
        AnimationShopElement element = (AnimationShopElement) super.clone();
        element.elements = elements.stream().map(ShopElement::clone).collect(Collectors.toCollection(ArrayList::new));
        return element;
    }
}
