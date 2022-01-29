package com.jacky8399.worstshop.shops.commodity;

import org.bukkit.entity.Player;

/**
 * Represents a {@link Commodity} that may be customized further for players.
 */
public interface IFlexibleCommodity {
    default Commodity adjustForPlayer(Player player) {
        return (Commodity) this;
    }
}
