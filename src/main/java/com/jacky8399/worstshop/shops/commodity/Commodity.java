package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.TextUtils;
import com.jacky8399.worstshop.shops.actions.ActionShop;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.rendering.DefaultSlotFiller;
import fr.minuskube.inv.content.SlotPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Represents a commodity that can be used as the cost/reward in {@link ActionShop}.
 */
public abstract class Commodity {
    public static final HashMap<String, Function<Config, ? extends Commodity>> PRESETS = new HashMap<>();

    private static void registerPresets() {
        PRESETS.put("action", CommodityAction::new);
        PRESETS.put("money", CommodityMoney::new);
        PRESETS.put("item", CommodityItem::new);
        PRESETS.put("command", CommodityCommand::new);
        PRESETS.put("points", CommodityPlayerPoint::new);
        PRESETS.put("exp", CommodityExp::new);
        PRESETS.put("free", yaml -> CommodityFree.INSTANCE);
        PRESETS.put("permission", yaml -> {
            try {
                return new CommodityPermission(yaml);
            } catch (IllegalStateException e) {
                return new CommodityPermissionVault(yaml);
            }
        });
        PRESETS.put("perm", PRESETS.get("permission"));
    }

    public static Commodity fromMap(Config config) {
        if (PRESETS.size() == 0) {
            registerPresets();
        }

        String type = config.find("type", String.class).orElseGet(()->config.get("preset", String.class));
        Commodity commodity;
        if ("_debug_exception_grant_".equals(type)) {
            commodity = new CommodityAction(Collections.emptyList()) {
                @Override
                public double grantOrRefund(Player player) {
                    throw new RuntimeException("Debug exception");
                }
            };
        } else {
            Function<Config, ? extends Commodity> ctor = PRESETS.get(type);
            if (ctor == null)
                throw new ConfigException("Invalid commodity type " + type, config, "type");
            commodity = ctor.apply(config);
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
     * @return list of components representing the amount of commodity the player has
     */
    public List<? extends Component> playerTrait(Player player) {
        return List.of();
    }

    /**
     * Get the result of a transaction involving the commodity
     * @param player the player
     * @param position the role of the commodity in the transaction
     * @return the result
     */
    @Deprecated
    public String getPlayerResult(@Nullable Player player, TransactionType position) {
        return TextUtils.LEGACY_COMPONENT_SERIALIZER.serialize(Component.join(JoinConfiguration.newlines(), playerResult(player, position)));
    }

    /**
     * Get the result of a transaction involving the commodity
     * @param player the player
     * @param position the role of the commodity in the transaction
     * @return list of components representing the result
     */
    public List<? extends Component> playerResult(@Nullable Player player, TransactionType position) {
        return List.of();
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

    public int getMaximumPurchase(Player player) {
        return Integer.MAX_VALUE;
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
        public ShopElement createElement(@NotNull ShopElement element) {
            ShopElement clone = element.clone();
            clone.filler = DefaultSlotFiller.NONE;
            clone.itemPositions = Collections.singletonList(pos);
            return clone;
        }
    }

    /**
     * Create a ShopElement to be displayed in ActionShop GUIs. Only called once per ActionShop GUI.
     * <p>
     * To have the element be updated every tick, override {@link #isElementDynamic()}
     * @param position position of the returned element
     * @return the ShopElement to be displayed
     */
    public ShopElement createElement(TransactionType position) {
        return position.createElement(ItemBuilder.of(Material.BEDROCK).name(ChatColor.DARK_RED + "???").build());
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
        if (this instanceof CommodityMoney money && money.isFromShorthand)
            return "$" + money.money;
        else if (this instanceof CommodityMultiple multiple)
            return multiple.wants.stream().map(want -> want.toSerializable(new LinkedHashMap<>())).toList();
        return toMap(map);
    }

}
