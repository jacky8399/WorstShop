package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.ItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

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
        float playerPoints = player.getExp() * player.getExpToLevel();
        return ChatColor.BLUE +
                (player.getLevel() != 0 ? I18n.translate("worstshop.messages.shops.wants.level", player.getLevel()) : "") + " " +
                (playerPoints != 0 ? I18n.translate("worstshop.messages.shops.wants.points", playerPoints) : "");
    }

    @Override
    public String getPlayerResult(@Nullable Player player, TransactionType position) {
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
    public double grantOrRefund(Player player) {
        player.giveExpLevels(levels);
        player.giveExp(points);
        return 0;
    }

    @Override
    public ItemStack createStack() {
        return ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(ChatColor.BLUE +
                        (levels != 0 ? I18n.translate("worstshop.messages.shops.wants.level", levels) : "") + " " +
                        (points != 0 ? I18n.translate("worstshop.messages.shops.wants.points", points) : ""))
                .build();
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "exp");
        map.put("levels", levels);
        map.put("points", points);
        return map;
    }
}
