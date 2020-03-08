package com.jacky8399.worstshop.shops.wants;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ShopWants {

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
        } else if (yaml instanceof Map) { // section
            return fromMap((Map<String, Object>) yaml);
        } else if (yaml instanceof List) {
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

    public boolean canMultiply() { return true; }

    public ShopWants multiply(double multiplier) {
        return this;
    }

    public boolean canAfford(Player player) {
        return false;
    }

    public String getPlayerTrait(Player player) {
        return "";
    }

    public String getPlayerResult(Player player, ElementPosition position) {
        return "";
    }

    public void deduct(Player player) {

    }

    @Deprecated
    protected void grant(Player player) {

    }

    public double grantOrRefund(Player player) {
        grant(player);
        return 0;
    }

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

    public boolean isElementDynamic() {
        return false;
    }

    public enum ElementPosition {
        COST(1,1), REWARD(1,7);

        public final SlotPos pos;
        ElementPosition(int row, int column) {
            pos = SlotPos.of(row, column);
        }
    }

    public ShopElement createElement(ElementPosition position) {
        StaticShopElement elem = StaticShopElement.fromStack(createStack());
        elem.fill = ShopElement.FillType.NONE;
        elem.itemPositions = Collections.singletonList(position.pos);
        return elem;
    }
}
