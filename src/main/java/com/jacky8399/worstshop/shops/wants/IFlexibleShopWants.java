package com.jacky8399.worstshop.shops.wants;

import org.bukkit.entity.Player;

public interface IFlexibleShopWants {
    default ShopWants adjustForPlayer(Player player) {
        return (ShopWants) this;
    }
}
