package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.shops.ElementPopulationContext;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.conditions.Condition;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        Config ifSection = config.get("if", Config.class);
        Condition condition = Condition.fromMap(ifSection);
        Config thenSection = config.get("then", Config.class);
        ShopElement element = ShopElement.fromConfig(thenSection);
        if (element == null)
            throw new ConfigException("'then' must not be empty", config, "then");
        ShopElement elementFalse = config.find("else", Config.class).map(ShopElement::fromConfig).orElse(null);

        ShopReference owner = ShopReference.of(ParseContext.findLatest(Shop.class));
        ConditionalShopElement ret = new ConditionalShopElement(condition, element, elementFalse);
        ret.owner = owner;
        return ret;
    }

    @Override
    public boolean isDynamic() {
        return elementTrue.isDynamic() || (elementFalse != null && elementFalse.isDynamic());
    }

    @Override
    public void populateItems(Player player, InventoryContents contents, ElementPopulationContext pagination) {
        ShopElement toApply = condition.test(player) ? elementTrue : elementFalse;

        if (toApply != null) {
            ShopElement clone = toApply.clone();
            List<Action> newActions = Lists.newArrayList(actions);
            newActions.addAll(clone.actions);
            clone.actions = newActions;

            clone.populateItems(player, contents, pagination);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, elementTrue, elementFalse);
    }

    @Override
    public String toString() {
        return elementTrue.toString() + " if " + condition.toString() + (elementFalse != null ? " else " + elementFalse.toString() : "");
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
