package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.WorstShop;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

    public ShopWantsMoney(double money) {
        this(money, 1);
    }

    public ShopWantsMoney(double money, double multiplier) {
        this.multiplier = multiplier;
        this.money = Math.abs(money); // ensure not negative
        this.realMoney = money * multiplier;
    }

    @Override
    public ItemStack createStack() {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(formatMoney(realMoney));
        stack.setItemMeta(meta);
        return stack;
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
    public String getPlayerResult(Player player, ElementPosition position) {
        return formatMoney(realMoney);
    }

    public static String formatMoney(double money) {
        return ChatColor.GOLD + ECONOMY.format(money);
    }
}
