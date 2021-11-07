package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommodityMultiple extends Commodity implements IFlexibleCommodity {
    public List<Commodity> wants;

    int wrapIndexOffset(int orig, int idx) {
        return (wants.size() + orig + idx) % wants.size();
    }

    public CommodityMultiple(List<Commodity> wants) {
        this.wants = new ArrayList<>();
        for (Commodity commodity : wants) {
            if (commodity instanceof CommodityMultiple)
                throw new IllegalArgumentException("Cannot embed CommodityMultiple!");
            this.wants.add(commodity);
        }
    }

    @Override
    public Commodity adjustForPlayer(Player player) {
        return new CommodityMultiple(wants.stream()
                .map(commodity -> commodity instanceof IFlexibleCommodity ?
                        ((IFlexibleCommodity) commodity).adjustForPlayer(player) :
                        commodity
                )
                .collect(Collectors.toList())
        );
    }

    @Override
    public boolean canMultiply() {
        return wants.stream().allMatch(Commodity::canMultiply);
    }

    @Override
    public int getMaximumMultiplier(Player player) {
        return wants.stream().mapToInt(wants -> getMaximumMultiplier(player)).min().orElse(0);
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityMultiple(wants.stream().map(want->want.multiply(multiplier)).collect(Collectors.toList()));
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
        Map<Commodity, Double> result = wants.stream()
                .collect(Collectors.toMap(Function.identity(), want -> want.grantOrRefund(player)));

        Map.Entry<Commodity, Double> maxRefund = Collections.max(result.entrySet(), Comparator.comparingDouble(Map.Entry::getValue));
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


    private static final EnumSet<ShopRenderer.RenderingFlag> flags = EnumSet.of(ShopRenderer.RenderingFlag.UPDATE_NEXT_TICK);
    @Override
    public ShopElement createElement(TransactionType position) {
        if (wants.size() == 1) {
            return wants.get(0).createElement(position);
        } else if (wants.size() == 2) {
            ShopElement elem1 = wants.get(0).createElement(position), elem2 = wants.get(1).createElement(position);
            SlotPos pos1 = SlotPos.of(position.pos.getRow() - 1, position.pos.getColumn()),
                    pos2 = SlotPos.of(position.pos.getRow() + 1, position.pos.getColumn());
            return new ShopElement() {
                @Override
                public List<RenderElement> getRenderElement(ShopRenderer renderer) {
                    return Arrays.asList(
                            new RenderElement(this, Collections.singleton(pos1),
                                    elem1.getRenderElement(renderer).get(0).stack(),
                                    e -> {}, flags),
                            new RenderElement(this, Collections.singleton(pos2),
                                    elem2.getRenderElement(renderer).get(0).stack(),
                                    e -> {}, flags)
                    );
                }
            };
        } else {
            // uhhhh
            // TODO fix 3 or more commodities
            return position.createElement(ItemBuilder.of(Material.BARRIER).name(ChatColor.RED + "WIP").build());
//            SlotPos pos1 = SlotPos.of(position.pos.getRow() - 1, position.pos.getColumn()),
//                    pos3 = SlotPos.of(position.pos.getRow() + 1, position.pos.getColumn());
//            String self = UUID.randomUUID().toString();
//            return new StaticShopElement() {
//                @Override
//                public void populateItems(Player player, InventoryContents contents, ElementContext pagination) {
//                    int itemSequence = contents.property(self + "_shopWantsItemSequence", 0);
//                    int nextItemSequence = wrapIndexOffset(itemSequence, 1);
//
//                    contents.setProperty(self + "_shopWantsItemSequence", nextItemSequence);
//
//                    ShopElement elem1 = wants.get(wrapIndexOffset(itemSequence, -1)).createElement(position),
//                            elem2 = wants.get(itemSequence).createElement(position),
//                            elem3 = wants.get(nextItemSequence).createElement(position);
//                    // sanitize
//                    elem1.filler = elem2.filler = elem3.filler = DefaultSlotFiller.NONE;
//                    elem1.itemPositions = Collections.singletonList(pos1);
//                    elem2.itemPositions = Collections.singletonList(position.pos);
//                    elem3.itemPositions = Collections.singletonList(pos3);
//
//                    elem1.populateItems(player, contents, pagination);
//                    elem2.populateItems(player, contents, pagination);
//                    elem3.populateItems(player, contents, pagination);
//                }
//            };
        }
    }

    @Override
    public String getPlayerTrait(Player player) {
        return wants.stream().map(want->want.getPlayerTrait(player)).collect(Collectors.joining(", "));
    }

    @Override
    public String getPlayerResult(Player player, TransactionType position) {
        return wants.stream().map(want->want.getPlayerResult(player, position)).collect(Collectors.joining(", "));
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        // handled by Commodity.class
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isElementDynamic() {
        return wants.size() >= 3 || wants.stream().anyMatch(Commodity::isElementDynamic);
    }

    @Override
    public int hashCode() {
        return wants.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CommodityMultiple && ((CommodityMultiple) obj).wants.equals(wants);
    }

    @Override
    public String toString() {
        return wants.stream().map(Commodity::toString).collect(Collectors.joining(",", "{", "}"));
    }
}
