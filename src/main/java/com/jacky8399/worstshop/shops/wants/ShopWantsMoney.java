package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public class ShopWantsMoney extends ShopWants {

    public static final Economy ECONOMY;

    static {
        if (WorstShop.get().economy == null)
            throw new IllegalStateException("Vault Economy not found!");
        ECONOMY = WorstShop.get().economy.getProvider();
    }

    public double money, realMoney;
    public double multiplier;
    public ShopWantsMoney(Map<String, Object> yaml) {
        this(((Number) yaml.getOrDefault("money", 0.0D)).doubleValue());
    }

    // to maintain serialization accuracy
    public final boolean fromShorthand;
    public ShopWantsMoney(double money) {
        this(money, 1, false);
    }

    public ShopWantsMoney(double money, double multiplier) {
        this(money, multiplier, false);
    }

    public ShopWantsMoney(double money, double multiplier, boolean shorthand) {
        this.multiplier = multiplier;
        this.money = Math.abs(money); // ensure not negative
        this.realMoney = money * multiplier;
        this.fromShorthand = shorthand;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(ItemBuilder.of(Material.GOLD_INGOT).name(formatMoney(realMoney)).build());
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsMoney(money, this.multiplier * multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return ECONOMY.has(player, realMoney);
    }

    @Override
    public int getMaximumMultiplier(Player player) {
        return (int) Math.floor(ECONOMY.getBalance(player) / realMoney);
    }

    @Override
    public String getPlayerTrait(Player player) {
        return formatMoney(ECONOMY.getBalance(player));
    }

    @Override
    public void deduct(Player player) {
        ECONOMY.withdrawPlayer(player, realMoney);
    }

    @Override
    public double grantOrRefund(Player player) {
        EconomyResponse response = ECONOMY.depositPlayer(player, realMoney);
        return response.transactionSuccess() ? 0 : response.amount / money;
    }

    @Override
    public String getPlayerResult(Player player, TransactionType position) {
        return formatMoney(realMoney);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "money");
        map.put("money", money);
        return map;
    }

    public static String formatMoney(double money) {
        return ChatColor.GOLD + ECONOMY.format(money);
    }
}
