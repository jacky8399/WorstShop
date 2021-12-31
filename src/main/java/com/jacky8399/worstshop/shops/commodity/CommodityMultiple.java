package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import fr.minuskube.inv.content.SlotPos;
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
        return wants.stream().mapToInt(commodity -> commodity.getMaximumMultiplier(player)).min().orElse(0);
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityMultiple(wants.stream().map(commodity -> commodity.multiply(multiplier)).collect(Collectors.toList()));
    }

    @Override
    public boolean canAfford(Player player) {
        return wants.stream().allMatch(commodity -> commodity.canAfford(player));
    }

    @Override
    public void deduct(Player player) {
        wants.forEach(commodity -> commodity.deduct(player));
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

    private RenderElement createRenderElement(SlotPos pos, ShopElement fake, ShopElement element, ShopRenderer renderer) {
        return new RenderElement(fake, Collections.singletonList(pos),
                element.getRenderElement(renderer, new PlaceholderContext(renderer, element)).get(0).stack(),
                e -> {}, ShopElement.DYNAMIC_FLAGS
        );
    }

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
                public List<RenderElement> getRenderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
                    return Arrays.asList(
                            createRenderElement(pos1, this, elem1, renderer),
                            createRenderElement(pos2, this, elem2, renderer)
                    );
                }
            };
        } else {
            SlotPos pos1 = SlotPos.of(position.pos.getRow() - 1, position.pos.getColumn()),
                    pos3 = SlotPos.of(position.pos.getRow() + 1, position.pos.getColumn());
            String self = UUID.randomUUID().toString();
            return new ShopElement() {
                @Override
                public List<RenderElement> getRenderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
                    int itemSequence = renderer.property(self + "_shopWantsItemSequence", 0);
                    int nextItemSequence = wrapIndexOffset(itemSequence, 1);
                    if (wants.size() != 3) // don't animate when there's only 3 elements
                        renderer.setProperty(self + "_shopWantsItemSequence", nextItemSequence);
                    ShopElement elem1 = wants.get(wrapIndexOffset(itemSequence, -1)).createElement(position),
                            elem2 = wants.get(itemSequence).createElement(position),
                            elem3 = wants.get(nextItemSequence).createElement(position);
                    return Arrays.asList(
                            createRenderElement(pos1, this, elem1, renderer),
                            createRenderElement(position.pos, this, elem2, renderer),
                            createRenderElement(pos3, this, elem3, renderer)
                    );
                }
            };
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
        // handled by Commodity.toSerializable
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
