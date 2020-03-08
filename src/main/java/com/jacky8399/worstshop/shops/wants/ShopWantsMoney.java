package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.WorstShop;
import net.milkbowl.vault.economy.Economy;
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

    public double money;
    public ShopWantsMoney(Map<String, Object> yaml) {
        this(((Number) yaml.getOrDefault("money", 0.0D)).doubleValue());
    }

    public ShopWantsMoney(double money) {
        this.money = Math.abs(money); // ensure not negative
    }

    @Override
    public ItemStack createStack() {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(formatMoney(money));
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsMoney(money * multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return ECONOMY.has(player, money);
    }

    @Override
    public int getMaximumMultiplier(Player player) {
        return (int) Math.floor(ECONOMY.getBalance(player) / money);
    }

    @Override
    public String getPlayerTrait(Player player) {
        return formatMoney(ECONOMY.getBalance(player));
    }

    @Override
    public void deduct(Player player) {
        ECONOMY.withdrawPlayer(player, money);
    }

    @Override
    public void grant(Player player) {
        ECONOMY.depositPlayer(player, money);
    }

    @Override
    public String getPlayerResult(Player player, ElementPosition position) {
        return formatMoney(money);
    }

    public static String formatMoney(double money) {
        return ChatColor.GOLD + ECONOMY.format(money);
    }
}
