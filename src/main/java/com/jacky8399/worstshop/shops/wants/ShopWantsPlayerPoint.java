package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import net.md_5.bungee.api.ChatColor;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ShopWantsPlayerPoint extends ShopWants {

    public static final PlayerPointsAPI POINTS;

    static {
        if (WorstShop.get().playerPoints == null)
            throw new IllegalStateException("PlayerPoints not found!");

        POINTS = WorstShop.get().playerPoints.getAPI();
    }

    int points;
    double multiplier;
    transient int realPoints;

    public ShopWantsPlayerPoint(Map<String, Object> yaml) {
        this(((Number)yaml.get("points")).intValue());
    }

    public ShopWantsPlayerPoint(int points) {
        this(points, 1);
    }

    public ShopWantsPlayerPoint(int points, double multiplier) {
        this.points = Math.abs(points);
        this.multiplier = multiplier;
        this.realPoints = (int) (points * multiplier);
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsPlayerPoint(points, this.multiplier * multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return POINTS.look(player.getUniqueId()) > realPoints;
    }

    @Override
    public String getPlayerTrait(Player player) {
        return formatPoints(POINTS.look(player.getUniqueId()));
    }

    @Override
    public String getPlayerResult(@Nullable Player player, TransactionType position) {
        return formatPoints(realPoints);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "points");
        map.put("points", points);
        return map;
    }

    @Override
    public void deduct(Player player) {
        POINTS.take(player.getUniqueId(), realPoints);
    }

    @Override
    public double grantOrRefund(Player player) {
        boolean success = POINTS.give(player.getUniqueId(), realPoints);
        return success ? 0 : multiplier;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(ItemBuilder.of(Material.DIAMOND).name(formatPoints(points)).build());
    }

    public static String formatPoints(int points) {
        return ChatColor.AQUA + I18n.translate("worstshop.shops.wants.player-points", points);
    }
}
