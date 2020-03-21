package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ShopWantsItem extends ShopWants implements IFlexibleShopWants {

    ItemStack stack;
    // never modify the stack directly
    double multiplier;
    public ShopWantsItem(Map<String, Object> yaml) {
        // parse itemstack
        this(StaticShopElement.parseItemStack(yaml), 1);
    }

    public ShopWantsItem(ItemStack stack) {
        this(stack, 1);
    }

    public ShopWantsItem(ItemStack stack, double multiplier) {
        this.stack = stack;
        this.multiplier = multiplier;
    }

    public int getAmount() {
        return (int) (stack.getAmount() * multiplier);
    }

    @Override
    public ItemStack createStack() {
        return stack.clone();
    }

    @Override
    public ShopWants adjustForPlayer(Player player) {
        // parse placeholders
        return new ShopWantsItem(StaticShopElement.getPlaceholderStack(player, stack));
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsItem(stack.clone(), multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return canAfford(player.getInventory());
    }

    public boolean canAfford(Inventory inventory) {
        return inventory.containsAtLeast(stack, getAmount());
    }

    public boolean canAfford(ItemStack stack) {
        return stack.isSimilar(this.stack) && stack.getAmount() >= getAmount();
    }

    // Override parent method for better performance
    @Override
    public int getMaximumMultiplier(Player player) {
        return getMaximumMultiplier(player.getInventory());
    }

    public int getMaximumMultiplier(Inventory inventory) {
        return Arrays.stream(inventory.getStorageContents()).filter(Objects::nonNull)
                .mapToInt(this::getMaximumMultiplier).sum();
    }

    public int getMaximumMultiplier(ItemStack stack) {
        return stack.isSimilar(this.stack) ? Math.floorDiv(stack.getAmount(), getAmount()) : 0;
    }

    @Override
    public String getPlayerTrait(Player player) {
        return getInventoryMatchingFormatted(player.getInventory());
    }

    public String getInventoryMatchingFormatted(Inventory inventory) {
        return I18n.translate("worstshop.messages.shops.wants.items", getInventoryMatching(inventory),
                PaperHelper.getItemName(stack)
        );
    }

    public int getInventoryMatching(Inventory inventory) {
        int amount = 0;
        for (ItemStack playerStack : inventory) {
            if (stack.isSimilar(playerStack)) {
                amount += playerStack.getAmount();
            }
        }
        return amount;
    }

    @Override
    public void deduct(Player player) {
        deduct(player.getInventory());
        player.updateInventory();
    }

    public void deduct(Inventory inventory) {
        int amount = getAmount();
        for (ItemStack playerStack : inventory.getStorageContents()) {
            if (stack.isSimilar(playerStack)) {
                if (playerStack.getAmount() >= amount) {
                    // has more than enough
                    playerStack.subtract(amount);
                    break; // exit loop
                } else {
                    // does not have enough
                    amount -= playerStack.getAmount();
                    playerStack.setAmount(0);
                }
            }
        }
    }

    public void deduct(ItemStack stack) {
        stack.setAmount(stack.getAmount() - getAmount());
    }

    @Override
    public double grantOrRefund(Player player) {
        double refund = 0;
        ItemStack remaining = grant(player.getInventory());
        if (remaining != null) {
            refund = (double) remaining.getAmount() / (double) stack.getAmount(); // get original amount for correct refund
            player.sendMessage(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.transaction-inv-full"));
        }
        player.updateInventory();
        return refund;
    }

    public ItemStack grant(Inventory inventory) {
        HashMap<Integer, ItemStack> unfit = inventory.addItem(
                ItemBuilder.from(stack.clone()).amount(getAmount()).build()
        );
        return unfit.size() > 0 ? unfit.get(0) : null;
    }

    @Override
    public String getPlayerResult(Player player, ElementPosition position) {
        return I18n.translate("worstshop.messages.shops.wants.items", getAmount(),
                PaperHelper.getItemName(stack)
        );
    }

}
