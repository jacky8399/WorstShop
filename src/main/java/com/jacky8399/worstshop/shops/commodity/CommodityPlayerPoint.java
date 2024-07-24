package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class CommodityPlayerPoint extends Commodity {

    public static final PlayerPointsAPI POINTS;

    static {
        if (WorstShop.get().playerPoints == null)
            throw new IllegalStateException("PlayerPoints not found!");

        POINTS = WorstShop.get().playerPoints.getAPI();
    }

    int points;
    double multiplier;
    transient int realPoints;

    public CommodityPlayerPoint(Config config) {
        this(config.get("points", Integer.class));
    }

    public CommodityPlayerPoint(int points) {
        this(points, 1);
    }

    public CommodityPlayerPoint(int points, double multiplier) {
        this.points = Math.abs(points);
        this.multiplier = multiplier;
        this.realPoints = (int) (points * multiplier);
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityPlayerPoint(points, this.multiplier * multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return POINTS.look(player.getUniqueId()) > realPoints;
    }

    @Override
    public List<? extends Component> playerTrait(Player player) {
        return List.of(formatPointsComponent(POINTS.look(player.getUniqueId())));
    }

    @Override
    public List<? extends Component> playerResult(@Nullable Player player, TransactionType position) {
        return List.of(formatPointsComponent(realPoints));
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
        return position.createElement(ItemBuilder.of(Material.DIAMOND).name(formatPointsComponent(points)).build());
    }

    public static String formatPoints(int points) {
        return ChatColor.AQUA + I18n.translate("worstshop.shops.wants.player-points", points);
    }

    public static Component formatPointsComponent(int points) {
        return I18n.translateComponent("worstshop.shops.wants.player-points", points).colorIfAbsent(NamedTextColor.AQUA);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(realPoints);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CommodityPlayerPoint && ((CommodityPlayerPoint) obj).realPoints == realPoints;
    }

    @Override
    public String toString() {
        return "[give/take " + points + " points]";
    }
}
