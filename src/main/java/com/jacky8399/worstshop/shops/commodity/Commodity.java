package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionCommodity;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Commodity {
    public static Commodity fromMap(Config config) {
        String type = config.get("type", String.class);
        Commodity commodity;
        switch (type) {
            case "money":
                commodity = new CommodityMoney(config);
                break;
            case "item":
                commodity = new CommodityItem(config);
                break;
            case "command":
                commodity = new CommodityCommand(config);
                break;
            case "points":
                commodity = new CommodityPlayerPoint(config);
                break;
            case "exp":
                commodity = new CommodityExp(config);
                break;
            case "perm":
                try {
                    commodity = new CommodityPermission(config);
                } catch (IllegalStateException e) {
                    commodity = new CommodityPermissionVault(config);
                }
                break;
            case "free":
                commodity = CommodityFree.INSTANCE;
                break;
            default:
                throw new IllegalArgumentException("Invalid commodity type " + type);
        }

        // special case
        if (config.has("display")) {
            commodity = new CommodityCustomizable(commodity, config);
        }
        return commodity;
    }

    public static Commodity fromObject(Object yaml) {
        // read type
        if (yaml instanceof String) { // one string
            return fromString((String) yaml);
        } else if (yaml instanceof Config) { // section
            return fromMap((Config) yaml);
        }
        throw new IllegalStateException("Invalid object type " + yaml.getClass().getSimpleName());
    }

    public static Commodity fromString(@NotNull String str) {
        if (str.startsWith("$")) {
            // money
            String number = str.substring(1);
            try {
                return new CommodityMoney(Double.parseDouble(number), 1, true);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid commodity shorthand value: " + number + " is not a valid number");
            }
        }
        throw new IllegalArgumentException("Invalid commodity shorthand '" + str + "'");
    }

    public Commodity() {}

    /**
     * Denotes whether the commodity can be multiplied with {@link #multiply(double)}
     * @return whether the commodity can be multiplied
     */
    public boolean canMultiply() { return true; }

    /**
     * Multiply the commodity with the specified multiplier, <br>rounded down if decimals are not accepted.
     * <p>
     * Note: it is recommended that the multiplier be stored in a variable
     * @param multiplier the multiplier
     * @return a new commodity with the applied multiplier
     */
    public Commodity multiply(double multiplier) {
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
    public String getPlayerResult(@Nullable Player player, TransactionType position) {
        return "";
    }

    /**
     * Deduct the commodity from the player
     * @param player the player
     */
    public void deduct(Player player) {}

    /**
     * Grant the commodity to the player, optionally refunding if the player cannot accept the commodity
     * @param player the player
     * @return the amount to refund. 0 = don't refund anything, 1 = refund one transaction
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

    /**
     * Denotes the type of transaction
     */
    public enum TransactionType {
        /**
         * The ShopElement should in the COST slot (left hand side)
         */
        COST(SlotPos.of(1,1)),
        /**
         * The ShopElement should be in the REWARD slot (right hand side)
         */
        REWARD(SlotPos.of(1,7));

        public final SlotPos pos;
        TransactionType(SlotPos pos) {
            this.pos = pos;
        }

        @NotNull
        public ShopElement createElement(ItemStack stack) {
            return createElement(StaticShopElement.fromStack(stack));
        }

        @NotNull
        public ShopElement createElement(ShopElement element) {
            ShopElement clone = element == null ? StaticShopElement.fromStack(UNDEFINED) : element.clone();
            clone.filler = ShopElement.DefaultSlotFiller.NONE;
            clone.itemPositions = Collections.singletonList(pos);
            return clone;
        }
    }

    public static final ItemStack UNDEFINED = ItemBuilder.of(Material.BEDROCK).name(ChatColor.DARK_RED + "???").build();
    /**
     * Create a ShopElement to be displayed in ActionShop GUIs. Only called once per ActionShop GUI.
     * <p>
     * To have the element be updated every tick, override {@link #isElementDynamic()}
     * @param position position of the returned element
     * @return the ShopElement to be displayed
     */
    public ShopElement createElement(TransactionType position) {
        return position.createElement(UNDEFINED);
    }

    /**
     * Denotes whether the ShopElement created in {@link #createElement(TransactionType)} is dynamic. <br>
     * A dynamic element means that the ShopElement will repopulate its items occasionally.
     * @return whether the ShopElement is dynamic
     */
    public boolean isElementDynamic() {
        return false;
    }

    /**
     * Attempts to serialize the commodity.
     */
    public Map<String, Object> toMap(Map<String, Object> map) {
        return map;
    }

    /**
     * Attempts to serialize the commodity with respect to whether it was created via a shorthand.
     */
    public final Object toSerializable(Map<String, Object> map) {
        if (this instanceof CommodityMoney && ((CommodityMoney) this).fromShorthand)
            return "$" + ((CommodityMoney) this).money;
        else if (this instanceof CommodityMultiple)
            return ((CommodityMultiple) this).wants.stream().map(want -> want.toSerializable(new HashMap<>())).collect(Collectors.toList());
        return toMap(map);
    }

    public Condition toCondition() {
        return this instanceof IUnaffordableCommodity ? ConditionConstant.FALSE : new ConditionCommodity(this);
    }
}
