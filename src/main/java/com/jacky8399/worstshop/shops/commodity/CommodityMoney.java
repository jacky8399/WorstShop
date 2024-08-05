package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

    public record Denomination(int minAmount, Material material) {
        public int getAmount(double money) {
            return (int) Math.max(Math.round(money / minAmount), 1);
        }
    }
    public static final List<Denomination> DENOMINATIONS = List.of(
            new Denomination(1, Material.GOLD_NUGGET),
            new Denomination(100, Material.GOLD_INGOT),
            new Denomination(10_000, Material.GOLD_BLOCK),
            new Denomination(1_000_000, Material.RAW_GOLD),
            new Denomination(100_000_000, Material.RAW_GOLD_BLOCK)
    );
    public static Denomination getDenomination(double money) {
        var iterator = DENOMINATIONS.iterator();
        Denomination denomination = iterator.next();
        while (iterator.hasNext()) {
            Denomination next = iterator.next();
            if (money < next.minAmount) {
                break;
            }
            denomination = next;
        }
        return denomination;
    }
    @Override
    public ShopElement createElement(TransactionType position) {
        Denomination denomination = getDenomination(realMoney);
        return position.createElement(
                ItemBuilder.of(denomination.material)
                        .maxAmount(99)
                        .amount(Math.min(denomination.getAmount(realMoney), 99))
                        .name(formatMoneyComponent(realMoney))
                        .build()
        );
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
    public List<? extends Component> playerTrait(Player player) {
        return List.of(formatMoneyComponent(ECONOMY.getBalance(player)));
    }

    @Override
    public List<? extends Component> playerResult(@Nullable Player player, TransactionType position) {
        return List.of(formatMoneyComponent(realMoney));
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
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "money");
        map.put("money", money);
        return map;
    }

    public static String formatMoney(double money) {
        return ChatColor.GOLD + ECONOMY.format(money);
    }

    public static Component formatMoneyComponent(double money) {
        return Component.text(ECONOMY.format(money), NamedTextColor.GOLD);
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
