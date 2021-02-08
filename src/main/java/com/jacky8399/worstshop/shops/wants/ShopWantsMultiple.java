package com.jacky8399.worstshop.shops.wants;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ShopWantsMultiple extends ShopWants {
    public List<ShopWants> wants;

    int wrapIndexOffset(int orig, int idx) {
        return (wants.size() + orig + idx) % wants.size();
    }

    public ShopWantsMultiple(List<ShopWants> wants) {
        this.wants = Lists.newArrayList();
        for (ShopWants commodity : wants) {
            if (commodity instanceof ShopWantsMultiple)
                throw new IllegalArgumentException("Cannot embed ShopWantsMultiple!");
            this.wants.add(commodity);
        }
    }

    @Override
    public boolean canMultiply() {
        return wants.stream().allMatch(ShopWants::canMultiply);
    }

    @Override
    public int getMaximumMultiplier(Player player) {
        return wants.stream().mapToInt(wants -> getMaximumMultiplier(player)).min().orElse(0);
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsMultiple(wants.stream().map(want->want.multiply(multiplier)).collect(Collectors.toList()));
    }

    @Override
    public boolean canAfford(Player player) {
        return wants.stream().allMatch(want->want.canAfford(player));
    }

    @Override
    public void deduct(Player player) {
        wants.forEach(want->want.deduct(player));
    }

    @Override
    public double grantOrRefund(Player player) {
        // need to refund other commodities ourselves
        Map<ShopWants, Double> result = wants.stream()
                .collect(Collectors.toMap(Function.identity(), want -> want.grantOrRefund(player)));

        Map.Entry<ShopWants, Double> maxRefund = Collections.max(result.entrySet(), Comparator.comparingDouble(Map.Entry::getValue));
        if (maxRefund != null && maxRefund.getValue() != 0) {
            result.forEach((want, refund)->{
                if (want != maxRefund.getKey()) {
                    double refundDifference = maxRefund.getValue() - refund;
                    want.multiply(refundDifference).deduct(player);
                }
            });
            return maxRefund.getValue();
        }
        return 0;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        if (wants.size() == 1) {
            return wants.get(0).createElement(position);
        } else if (wants.size() == 2) {
            ShopElement elem1 = wants.get(0).createElement(position), elem2 = wants.get(1).createElement(position);
            SlotPos pos1 = SlotPos.of(position.pos.getRow() - 1, position.pos.getColumn()),
                    pos2 = SlotPos.of(position.pos.getRow() + 1, position.pos.getColumn());
            // sanitize elements
            elem1.fill = elem2.fill = ShopElement.FillType.NONE;
            elem1.itemPositions = Collections.singletonList(pos1);
            elem2.itemPositions = Collections.singletonList(pos2);
            return new StaticShopElement() {
                @Override
                public void populateItems(Player player, InventoryContents contents, Shop.PaginationHelper pagination) {
                    elem1.populateItems(player, contents, pagination);
                    elem2.populateItems(player, contents, pagination);
                }
            };
        } else {
            SlotPos pos1 = SlotPos.of(position.pos.getRow() - 1, position.pos.getColumn()),
                    pos3 = SlotPos.of(position.pos.getRow() + 1, position.pos.getColumn());
            String self = UUID.randomUUID().toString();
            return new StaticShopElement() {
                @Override
                public void populateItems(Player player, InventoryContents contents, Shop.PaginationHelper pagination) {
                    int itemSequence = contents.property(self + "_shopWantsItemSequence", 0);
                    int nextItemSequence = wrapIndexOffset(itemSequence, 1);

                    contents.setProperty(self + "_shopWantsItemSequence", nextItemSequence);

                    ShopElement elem1 = wants.get(wrapIndexOffset(itemSequence, -1)).createElement(position),
                            elem2 = wants.get(itemSequence).createElement(position),
                            elem3 = wants.get(nextItemSequence).createElement(position);
                    // sanitize
                    elem1.fill = elem2.fill = elem3.fill = ShopElement.FillType.NONE;
                    elem1.itemPositions = Collections.singletonList(pos1);
                    elem2.itemPositions = Collections.singletonList(position.pos);
                    elem3.itemPositions = Collections.singletonList(pos3);

                    elem1.populateItems(player, contents, pagination);
                    elem2.populateItems(player, contents, pagination);
                    elem3.populateItems(player, contents, pagination);
                }
            };
        }
    }

    @Override
    public String getPlayerTrait(Player player) {
        return wants.stream().map(want->want.getPlayerTrait(player)).collect(Collectors.joining("\n"));
    }

    @Override
    public String getPlayerResult(Player player, TransactionType position) {
        return wants.stream().map(want->want.getPlayerResult(player, position)).collect(Collectors.joining("\n"));
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        // handled by ShopWants.class
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isElementDynamic() {
        return wants.size() >= 3;
    }

    @Override
    public int hashCode() {
        return wants.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ShopWantsMultiple && ((ShopWantsMultiple) obj).wants.equals(wants);
    }

    @Override
    public String toString() {
        return wants.stream().map(ShopWants::toString).collect(Collectors.joining(",", "{", "}"));
    }
}
