package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ShopWantsFree extends ShopWants {
    public ShopWantsFree() {
        super();
    }

    @Override
    public boolean canAfford(Player player) {
        return true;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(
                ItemBuilder.of(Material.BARRIER).name(I18n.translate("worstshop.messages.shops.wants.free")).build()
        );
    }
}
