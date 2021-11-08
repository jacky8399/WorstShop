package com.jacky8399.worstshop.shops.elements;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.rendering.DefaultSlotFiller;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ConditionalShopElement extends ShopElement {
    @NotNull
    public Condition condition;
    @NotNull
    public ShopElement elementTrue;
    @Nullable
    public ShopElement elementFalse;

    public ConditionalShopElement(@NotNull Condition condition, @NotNull ShopElement element) {
        this(condition, element, null);
    }

    public ConditionalShopElement(@NotNull Condition condition, @NotNull ShopElement elementTrue, @Nullable ShopElement elementFalse) {
        this.condition = condition;
        this.elementTrue = elementTrue;
        this.elementFalse = elementFalse;
    }

    public static ShopElement fromYaml(Config config) {
        @SuppressWarnings("ConstantConditions")
        ConditionalShopElement ret = new ConditionalShopElement(null, null, null);

        ParseContext.pushContext(ret);

        Config ifSection = config.get("if", Config.class);
        ret.condition = Condition.fromMap(ifSection);

        Config thenSection = config.get("then", Config.class);
        ShopElement element = ShopElement.fromConfig(thenSection);
        if (element == null)
            throw new ConfigException("'then' must not be empty", config, "then");
        ret.elementTrue = element;
        ret.elementFalse = config.find("else", Config.class).map(ShopElement::fromConfig).orElse(null);
        ret.owner = ShopReference.of(ParseContext.findLatest(Shop.class));
        return ret;
    }

    @Override
    public boolean isDynamic() {
        return elementTrue.isDynamic() || (elementFalse != null && elementFalse.isDynamic());
    }

    ShopElement cacheTrue, cacheFalse;
    @Override
    public List<RenderElement> getRenderElement(ShopRenderer renderer) {
        if (cacheTrue == null) {
            sanitize();
        }
        ShopElement toApply = condition.test(renderer.player) ? cacheTrue : cacheFalse;
        return toApply != null ? toApply.getRenderElement(renderer) : Collections.emptyList();
    }

    public void sanitize() {
        cacheTrue = elementTrue.clone();
        sanitize(cacheTrue);

        if (elementFalse != null) {
            cacheFalse = elementFalse.clone();
            sanitize(cacheFalse);
        }
    }

    public void sanitize(ShopElement element) {
        if (filler != DefaultSlotFiller.NONE || itemPositions != null) {
            element.filler = filler;
            element.itemPositions = itemPositions != null ? new ArrayList<>(itemPositions) : null;
        }
        if (actions.size() != 0) {
            List<Action> newActions = new ArrayList<>(actions);
            newActions.addAll(element.actions);
            element.actions = newActions;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, elementTrue, elementFalse);
    }

    @Override
    public String toString() {
        return elementTrue + " if " + condition + (elementFalse != null ? " else " + elementFalse : "");
    }

    @Override
    public ShopElement clone() {
        ConditionalShopElement element = (ConditionalShopElement) super.clone();
        element.elementTrue = elementTrue.clone();
        if (elementFalse != null)
            element.elementFalse = elementFalse.clone();
        return element;
    }
}
