package com.jacky8399.worstshop.shops.wants;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Special commodity to let users specify the display themselves
 */
public final class ShopWantsCustomizable extends ShopWants implements IFlexibleShopWants {
    @NotNull
    public final ShopWants base;
    @NotNull
    private final ShopElement element;
    transient boolean copyFromParent;
    public ShopWantsCustomizable(@NotNull ShopWantsCustomizable carryOver) {
        this(carryOver.base, carryOver.element, carryOver.copyFromParent);
    }

    public ShopWantsCustomizable(@NotNull ShopWants base, @NotNull ShopElement element) {
        this(base, element, false);
    }

    public ShopWantsCustomizable(@NotNull ShopWants base, @NotNull ShopElement element, boolean copyFromParent) {
        this.base = base;
        this.element = element;
        this.copyFromParent = copyFromParent;
    }

    public ShopWantsCustomizable(@NotNull ShopWants base, @NotNull Config config) {
        this.base = base;
        ShopElement element = fromYaml(config.get("display", Config.class));
        this.element = element != null ? element : StaticShopElement.fromStack(ShopWants.UNDEFINED);
    }

    public ShopElement fromYaml(Config config) {
        return config.find("from", String.class).map(from -> {
            if (from.equalsIgnoreCase("parent")) {
                copyFromParent = true;
                ShopElement parent = ParseContext.findLatest(ShopElement.class);
                if (parent == null)
                    throw new ConfigException("Failed to find parent element with 'from: parent'", config, "from");
                return parent.clone();
            }
            throw new ConfigException("Unsupported source " + from, config, "from");
        }).orElseGet(()->ShopElement.fromConfig(config));
    }

    @Override
    public boolean canMultiply() {
        return base.canMultiply();
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsCustomizable(base.multiply(multiplier), element, copyFromParent);
    }

    @Override
    public boolean canAfford(Player player) {
        return base.canAfford(player);
    }

    @Override
    public String getPlayerTrait(Player player) {
        return base.getPlayerTrait(player);
    }

    @Override
    public String getPlayerResult(@Nullable Player player, TransactionType position) {
        return base.getPlayerResult(player, position);
    }

    @Override
    public void deduct(Player player) {
        base.deduct(player);
    }

    @Override
    public double grantOrRefund(Player player) {
        return base.grantOrRefund(player);
    }

    @Override
    public int getMaximumMultiplier(Player player) {
        return base.getMaximumMultiplier(player);
    }

    @Override
    public ShopWants adjustForPlayer(Player player) {
        return base instanceof IFlexibleShopWants ?
                new ShopWantsCustomizable(((IFlexibleShopWants) base).adjustForPlayer(player), element, copyFromParent) :
                this;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        // sanitize element
        ShopElement element = this.element.clone();
        element.fill = ShopElement.FillType.NONE;
        element.itemPositions = Collections.singletonList(position.pos);
        element.actions = Lists.newArrayList();
        return element;
    }

    @Override
    public boolean isElementDynamic() {
        return element.isDynamic();
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        base.toMap(map);
        if (copyFromParent)
            map.put("display", Collections.singletonMap("from", "parent"));
        else
            map.put("display", element.toMap(new HashMap<>()));
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, element);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ShopWantsCustomizable))
            return false;
        ShopWantsCustomizable other = (ShopWantsCustomizable) obj;
        return other.base.equals(base) && other.element.equals(element);
    }

    @Override
    public Condition toCondition() {
        return base.toCondition();
    }

    @Override
    public String toString() {
        return base.toString() + " (w/ custom display)";
    }
}
