package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CommodityExp extends Commodity {
    public CommodityExp(Config config) {
        this(config.find("levels", Integer.class).orElse(0),
                config.find("points", Integer.class).orElse(0));
    }

    int levels, points;
    transient double multiplier;
    public CommodityExp(int levels, int points) {
        this(levels, points, 1);
    }

    public CommodityExp(int levels, int points, double multiplier) {
        this.levels = levels;
        this.points = points;
        this.multiplier = multiplier;
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityExp(levels, points, multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return player.getLevel() >= (int) (levels * multiplier) && player.getTotalExperience() >= (int) (points * multiplier);
    }

    private static Component formatComponent(int levels, int points) {
        var builder = Component.text().color(NamedTextColor.BLUE);
        if (levels != 0) {
            builder.append(I18n.translateComponent("worstshop.messages.shops.wants.levels", levels));
            if (points != 0)
                builder.append(Component.space());
        }
        if (points != 0)
            builder.append(I18n.translateComponent("worstshop.messages.shops.wants.points", points));
        return builder.build();
    }

    @Override
    public List<? extends Component> playerTrait(Player player) {
        int playerPoints = (int) (player.getExp() * player.getExpToLevel());
        return List.of(formatComponent(player.getLevel(), playerPoints));
    }

    @Override
    public List<? extends Component> playerResult(@Nullable Player player, TransactionType position) {
        return List.of(formatComponent((int) (levels * multiplier), (int) (points * multiplier)));
    }

    @Override
    public void deduct(Player player) {
        super.deduct(player);
        player.giveExpLevels((int) (-levels * multiplier));
        player.giveExp((int) (-points * multiplier));
    }

    @Override
    public double grantOrRefund(Player player) {
        player.giveExpLevels((int) (levels * multiplier));
        player.giveExp((int) (points * multiplier));
        return 0;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(
                ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                        .name(formatComponent((int) (levels * multiplier), (int) (points * multiplier)))
                        .build()
        );
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "exp");
        map.put("levels", levels);
        map.put("points", points);
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(levels, points, multiplier);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommodityExp))
            return false;
        CommodityExp other = (CommodityExp) obj;
        return other.multiplier == multiplier && other.levels == levels && other.points == points;
    }

    @Override
    public String toString() {
        return "[give/take " + levels + " levels and " + points + " xp points]";
    }
}
