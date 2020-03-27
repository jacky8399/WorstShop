package com.jacky8399.worstshop.shops.wants;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ShopWants implements Predicate<Player> {

    public static ShopWants fromMap(Map<String, Object> map) {
        String type = (String) map.getOrDefault("type", "free");
        switch (type) {
            case "money":
                return new ShopWantsMoney(map);
            case "item":
                return new ShopWantsItem(map);
            case "command":
                return new ShopWantsCommand(map);
            case "points":
                return new ShopWantsPlayerPoint(map);
            case "exp":
                return new ShopWantsExp(map);
            case "perm":
                return new ShopWantsPermission(map);
            case "free":
            default:
                return new ShopWantsFree();
        }
    }

    public static ShopWants fromYamlNode(Object yaml) {
        // read type
        if (yaml instanceof String) { // one string
            // TODO parse simple string
        } else if (yaml instanceof Map<?, ?>) { // section
            return fromMap((Map<String, Object>) yaml);
        } else if (yaml instanceof List<?>) {
            List<ShopWants> wants = Lists.newArrayList();
            for (Map<String, Object> want : (List<Map<String, Object>>)yaml) {
                wants.add(fromMap(want));
            }
            return new ShopWantsMultiple(wants);
        }
        return new ShopWantsFree();
    }

    public ShopWants() {

    }

    /**
     * Denotes whether the commodity can be multiplied with {@link #multiply(double)}
     * @return whether the commodity can be multiplied
     */
    public boolean canMultiply() { return true; }

    /**
     * Multiply the commodity with the specified multiplier, rounded down if decimals are not accepted.
     * @param multiplier the multiplier
     * @return a new commodity with the applied multiplier
     */
    public ShopWants multiply(double multiplier) {
        return this;
    }

    /**
     * Test if a player matches the commodity's requirements
     * @param player the player to test
     * @return whether the player matches
     */
    public boolean canAfford(Player player) {
        return false;
    }

    /**
     * Get the amount of the commodity the player already possesses
     * @param player the player
     * @return the amount of commodity the player has
     */
    public String getPlayerTrait(Player player) {
        return "";
    }

    /**
     * Get the result of a transaction involving the commodity
     * @param player the player
     * @param position the role of the commodity in the transaction
     * @return the result
     */
    public String getPlayerResult(Player player, ElementPosition position) {
        return "";
    }

    /**
     * Deduct the commodity from the player
     * @param player the player
     */
    public void deduct(Player player) {

    }

    /**
     * Grant the commodity to the player, optionally refunding if the player cannot accept the commodity
     * @param player the player
     * @return the amount to refund
     */
    public double grantOrRefund(Player player) {
        return 0;
    }

    /**
     * Find the maximum multiplier that the player can still afford
     * @param player the player
     * @return the maximum multiplier
     */
    public int getMaximumMultiplier(Player player) {
        int canAfford = 0;
        while (this.multiply(canAfford + 1).canAfford(player)) {
            canAfford++;
        }
        return canAfford;
    }

    @Deprecated
    public ItemStack createStack() {
        return null;
    }

    @Override
    public boolean test(Player player) {
        return canAfford(player);
    }

    private static List<ShopWants> mergeWants(ShopWants orig, ShopWants other) {
        List<ShopWants> wants = Lists.newArrayList();
        if (other instanceof ShopWantsMultiple) {
            wants.addAll(((ShopWantsMultiple) other).wants);
        } else {
            wants.add(other);
        }
        if (orig instanceof ShopWantsMultiple) {
            wants.addAll(((ShopWantsMultiple) orig).wants);
        } else {
            wants.add(orig);
        }
        return wants;
    }

    @Override
    public Predicate<Player> and(Predicate<? super Player> other) {
        if (other instanceof ShopWants) {
            // merge into one ShopWantsMultiple
            return new ShopWantsMultiple(mergeWants(this, (ShopWants) other));
        }
        return Predicate.super.and(other);
    }
    // don't override other Predicate methods as it makes no sense to ShopWants

    /**
     * Denotes the position the ShopElement should be at
     */
    public enum ElementPosition {
        /**
         * The ShopElement should in the COST slot (left hand side)
         */
        COST(1,1),
        /**
         * The ShopElement should be in the REWARD slot (right hand side)
         */
        REWARD(1,7);

        public final SlotPos pos;
        ElementPosition(int row, int column) {
            pos = SlotPos.of(row, column);
        }
    }

    /**
     * Create a ShopElement to be displayed in ActionShop GUIs. Only called once per ActionShop GUI.
     *
     * To have the element be updated every tick, override {@link #isElementDynamic()}
     * @param position position of the returned element
     * @return the ShopElement to be displayed
     */
    public ShopElement createElement(ElementPosition position) {
        StaticShopElement elem = StaticShopElement.fromStack(createStack());
        elem.fill = ShopElement.FillType.NONE;
        elem.itemPositions = Collections.singletonList(position.pos);
        return elem;
    }

    /**
     * Denotes whether the ShopElement created in {@link #createElement(ElementPosition)} is dynamic.
     * A dynamic element means that the ShopElement will repopulate its items every tick.
     * @return whether the ShopElement is dynamic
     */
    public boolean isElementDynamic() {
        return false;
    }
}
