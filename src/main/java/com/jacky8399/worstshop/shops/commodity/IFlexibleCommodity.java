package com.jacky8399.worstshop.shops.commodity;

import org.bukkit.entity.Player;

public interface IFlexibleCommodity {
    default Commodity adjustForPlayer(Player player) {
        return (Commodity) this;
    }
}
