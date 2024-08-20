package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.i18n.ComponentTranslatable;
import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.shops.actions.ActionItemShop;
import com.jacky8399.worstshop.shops.commodity.CommodityMoney;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Utility class to display the current state of an item/player shop
 */
public class ItemShopFormatter {
    private ItemShopFormatter() {}

    public static final String KEY = I18n.Keys.MESSAGES_KEY + "shops.item-shop.";

    public record ShopTypeSelector(
            ComponentTranslatable player,
            ComponentTranslatable playerMissing,
            ComponentTranslatable server
    ) {
        public static ShopTypeSelector fromSectionKey(String key) {
            return new ShopTypeSelector(
                    I18n.createComponentTranslatable(key + ".player"),
                    I18n.createComponentTranslatable(key + ".player-missing"),
                    I18n.createComponentTranslatable(key + ".server")
            );
        }
    }

    public static final ShopTypeSelector SOURCE = ShopTypeSelector.fromSectionKey(KEY + "source");
    public static final ShopTypeSelector PRICE_BUY = ShopTypeSelector.fromSectionKey(KEY + "price.buy");
    public static final ShopTypeSelector PRICE_SELL = ShopTypeSelector.fromSectionKey(KEY + "price.sell");
    public static final ComponentTranslatable LIMIT = I18n.createComponentTranslatable(KEY + "limit");
    public static final ComponentTranslatable REMAINING = I18n.createComponentTranslatable(KEY + "remaining");
    public static final ComponentTranslatable BUY = I18n.createComponentTranslatable(KEY + "buy");
    public static final ComponentTranslatable SELL = I18n.createComponentTranslatable(KEY + "sell");

    private static List<Component> formatPlayer(ComponentTranslatable baseFormat, ShopTypeSelector priceFormat, int shops, double price) {
        boolean isEmpty = shops == 0;
        Component sourceComponent = isEmpty ? SOURCE.playerMissing.apply() : SOURCE.player.apply(Component.text(shops));
        Component priceComponent = isEmpty ? priceFormat.playerMissing.apply() : priceFormat.player.apply(CommodityMoney.formatMoneyComponent(price));
        return List.of(
                baseFormat.apply(sourceComponent),
                priceComponent
        );
    }

    public static List<Component> formatPlayerBuy(int shops, double price) {
        return formatPlayer(BUY, PRICE_BUY, shops, price);
    }

    public static List<Component> formatPlayerSell(int shops, double price) {
        return formatPlayer(SELL, PRICE_SELL, shops, price);
    }

    private static List<Component> formatServer(ComponentTranslatable baseFormat, ShopTypeSelector priceFormat, Component price,
                                                @Nullable Component limitInterval, int limit, int remaining) {
        Component sourceComponent = SOURCE.server.apply();
        Component priceComponent = priceFormat.server.apply(price);
        Component baseComponent = baseFormat.apply(sourceComponent);
        if (limitInterval == null) {
            return List.of(
                    baseComponent,
                    priceComponent
            );
        } else {
            return List.of(
                    baseComponent,
                    priceComponent,
                    LIMIT.apply(Component.text(limit), limitInterval),
                    REMAINING.apply(Component.text(remaining))
            );
        }
    }

    public static List<Component> formatServerBuy(Component price) {
        return formatServer(BUY, PRICE_BUY, price, null, 0, 0);
    }

    public static List<Component> formatServerSell(Component price) {
        return formatServer(SELL, PRICE_SELL, price, null, 0, 0);
    }

    public static List<Component> formatServerBuy(Component price, Component limitInterval, int limit, int remaining) {
        return formatServer(BUY, PRICE_BUY, price, limitInterval, limit, remaining);
    }

    public static List<Component> formatServerSell(Component price, Component limitInterval, int limit, int remaining) {
        return formatServer(SELL, PRICE_SELL, price, limitInterval, limit, remaining);
    }

    public static List<Component> formatServerBuy(ActionItemShop itemShop, Player player) {
        Component price = itemShop.formatPriceDiscountComponent(itemShop.buyPrice, itemShop.getDiscount(player));
        if (itemShop.buyLimitTemplate != null) {
            int purchased = PlayerPurchases.getCopy(player).applyTemplate(itemShop.buyLimitTemplate).getTotalPurchases();
            return formatServerBuy(
                    price,
                    DateTimeUtils.formatReadableDuration(itemShop.buyLimitTemplate.retentionTime(), player.locale()),
                    itemShop.buyLimit,
                    itemShop.buyLimit - purchased
            );
        } else {
            return formatServerBuy(price);
        }
    }

    public static List<Component> formatServerSell(ActionItemShop itemShop, Player player) {
        Component price = itemShop.formatPriceDiscountComponent(itemShop.sellPrice, itemShop.getDiscount(player));
        if (itemShop.sellLimitTemplate != null) {
            int purchased = PlayerPurchases.getCopy(player).applyTemplate(itemShop.sellLimitTemplate).getTotalPurchases();
            return formatServerSell(
                    price,
                    DateTimeUtils.formatReadableDuration(itemShop.sellLimitTemplate.retentionTime(), player.locale()),
                    itemShop.sellLimit,
                    itemShop.sellLimit - purchased
            );
        } else {
            return formatServerSell(price);
        }
    }
}
