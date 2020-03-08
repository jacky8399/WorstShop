package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class ShopWantsPlayerPoint extends ShopWants {

    public static final PlayerPointsAPI POINTS;

    static {
        if (WorstShop.get().playerPoints == null)
            throw new IllegalStateException("PlayerPoints not found!");

        POINTS = WorstShop.get().playerPoints.getAPI();
    }

    int points;

    public ShopWantsPlayerPoint(Map<String, Object> yaml) {
        this(((Number)yaml.get("points")).intValue());
    }

    public ShopWantsPlayerPoint(int points) {

        this.points = Math.abs(points);
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsPlayerPoint((int) (points * multiplier));
    }

    @Override
    public boolean canAfford(Player player) {
        return POINTS.look(player.getUniqueId()) > points;
    }

    @Override
    public String getPlayerTrait(Player player) {
        return formatPoints(POINTS.look(player.getUniqueId()));
    }

    @Override
    public void deduct(Player player) {
        POINTS.take(player.getUniqueId(), points);
    }

    @Override
    public void grant(Player player) {
        POINTS.give(player.getUniqueId(), points);
    }

    @Override
    public ItemStack createStack() {
        return ItemBuilder.of(Material.DIAMOND)
                .name(formatPoints(points)).build();
    }

    public static String formatPoints(int points) {
        return ChatColor.AQUA + I18n.translate("worstshop.shops.wants.player-points", points);
    }
}
