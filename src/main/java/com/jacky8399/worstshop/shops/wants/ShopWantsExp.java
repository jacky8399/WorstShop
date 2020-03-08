package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class ShopWantsExp extends ShopWants {

    public ShopWantsExp(Map<String, Object> map) {
        this(((Number) map.getOrDefault("levels", 0)).intValue(),
                ((Number) map.getOrDefault("points", 0)).intValue());
    }

    int levels, points;
    public ShopWantsExp(int levels, int points) {
        this.levels = levels;
        this.points = points;
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsExp((int) (levels * multiplier), (int) (points * multiplier));
    }

    @Override
    public boolean canAfford(Player player) {
        return player.getLevel() >= levels && player.getTotalExperience() >= points;
    }

    @Override
    public String getPlayerTrait(Player player) {
        return ChatColor.BLUE +
                (levels != 0 ? I18n.translate("worstshop.messages.shops.wants.level", levels) : "") + " " +
                (points != 0 ? I18n.translate("worstshop.messages.shops.wants.points", points) : "");
    }

    @Override
    public void deduct(Player player) {
        super.deduct(player);
        player.giveExpLevels(-levels);
        player.giveExp(-points);
    }

    @Override
    public void grant(Player player) {
        player.giveExpLevels(levels);
        player.giveExp(points);
    }

    @Override
    public ItemStack createStack() {
        return ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(ChatColor.BLUE +
                        (levels != 0 ? I18n.translate("worstshop.messages.shops.wants.level", levels) : "") + " " +
                        (points != 0 ? I18n.translate("worstshop.messages.shops.wants.points", points) : ""))
                .build();
    }
}
