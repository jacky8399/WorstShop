package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public class CommodityMoney extends Commodity {

    public static final Economy ECONOMY;

    static {
        if (WorstShop.get().economy == null)
            throw new IllegalStateException("Vault Economy not found!");
        ECONOMY = WorstShop.get().economy.getProvider();
    }

    public double money;
    public transient double realMoney;
    public transient double multiplier;
    public CommodityMoney(Config config) {
        this(config.get("money", Double.class));
    }

    // to maintain serialization accuracy
    public final boolean isFromShorthand;
    public CommodityMoney(double money) {
        this(money, 1, false);
    }

    public CommodityMoney(double money, double multiplier) {
        this(money, multiplier, false);
    }

    public CommodityMoney(double money, double multiplier, boolean shorthand) {
        this.multiplier = multiplier;
        this.money = Math.abs(money); // ensure not negative
        this.realMoney = money * multiplier;
        this.isFromShorthand = shorthand;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(ItemBuilder.of(Material.GOLD_INGOT).name(formatMoney(realMoney)).build());
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityMoney(money, this.multiplier * multiplier);
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
        return response.transactionSuccess() ? 0 : (realMoney - response.amount) / money;
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

    @Override
    public int hashCode() {
        return Double.hashCode(realMoney);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CommodityMoney && ((CommodityMoney) obj).realMoney == realMoney;
    }

    @Override
    public String toString() {
        return "[give/take " + money + "]";
    }
}
