package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.shops.ElementContext;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.rendering.DefaultSlotFiller;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import com.jacky8399.worstshop.shops.rendering.SlotFiller;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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

    @Override
    public void populateItems(Player player, InventoryContents contents, ElementContext pagination) {
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
    public ItemStack createStack(ShopRenderer renderer) {
        Player player = renderer.player;
        ShopElement toApply = condition.test(player) ? elementTrue : elementFalse;
        if (toApply != null) {
            ShopElement clone = toApply.clone();
            return clone.createStack(renderer);
        }
        return null;
    }

    @Override
    public Consumer<InventoryClickEvent> getClickHandler(ShopRenderer renderer) {
        Player player = renderer.player;
        ShopElement toApply = condition.test(player) ? elementTrue : elementFalse;
        if (toApply != null) {
            ShopElement clone = toApply.clone();
            List<Action> newActions = new ArrayList<>(actions);
            newActions.addAll(clone.actions);
            clone.actions = newActions;

            return clone.getClickHandler(renderer);
        }
        return e->{};
    }

    @Override
    public SlotFiller getFiller(ShopRenderer renderer) {
        ShopElement toApply = condition.test(renderer.player) ? elementTrue : elementFalse;
        return toApply != null ?
                (ignored, ignored1) -> toApply.getFiller(renderer).fill(toApply, renderer) :
                DefaultSlotFiller.NONE;
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
