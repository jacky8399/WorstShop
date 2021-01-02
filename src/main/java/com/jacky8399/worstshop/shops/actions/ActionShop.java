package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.wants.IFlexibleShopWants;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import com.jacky8399.worstshop.shops.wants.ShopWantsItem;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

public class ActionShop extends Action implements IParentElementReader {
    public ShopWants cost, reward;
    public PurchaseRecords.RecordTemplate purchaseLimitTemplate;
    public int purchaseLimit;
    public ActionShop(Config yaml) {
        super(yaml);
        yaml.find("cost", String.class, Config.class, List.class)
                .map(ShopWants::fromYamlNode)
                .ifPresent(cost -> this.cost = cost);

        yaml.find("reward", String.class, Config.class, List.class)
                .map(ShopWants::fromYamlNode)
                .ifPresent(reward -> this.reward = reward);

        yaml.find("purchase-limit", Config.class).ifPresent(purchaseLimitYaml -> {
            purchaseLimitTemplate = PurchaseRecords.RecordTemplate.fromConfig(purchaseLimitYaml);
            purchaseLimit = purchaseLimitYaml.get("limit", Integer.class);
        });
    }

    public ActionShop(ShopWants cost, ShopWants reward, PurchaseRecords.RecordTemplate purchaseLimitTemplate, int purchaseLimit) {
        super(null);
        this.cost = cost;
        this.reward = reward;
        this.purchaseLimitTemplate = purchaseLimitTemplate;
        this.purchaseLimit = purchaseLimit;
    }

    public void doTransaction(Player player, double count) {
        if (count == 0) {
            player.sendMessage(formatPurchaseMessage(player, 0));
            return;
        }
        ActionShop adjusted = adjustForPlayer(player);
        ShopWants multipliedCost = adjusted.cost.multiply(count);
        int playerMaxPurchases = getPlayerMaxPurchase(player);
        if (count > playerMaxPurchases) {
            player.sendMessage(formatPurchaseLimitMessage(player));
            return;
        }
        if (multipliedCost.canAfford(player)) {
            multipliedCost.deduct(player);
            double refund = adjusted.reward.multiply(count).grantOrRefund(player);
            if (refund > 0) {
                // refunds DO NOT allow refunding
                adjusted.cost.multiply(refund).grantOrRefund(player);
                player.sendMessage(formatRefundMessage(player, refund));
            }
            // record the sale
            if (purchaseLimitTemplate != null) {
                PurchaseRecords records = PurchaseRecords.getCopy(player);
                records.applyTemplate(purchaseLimitTemplate).addRecord(LocalDateTime.now(), (int) (count - refund));
                records.updateFor(player);
            }
            player.sendMessage(formatPurchaseMessage(player, count - refund));
        } else {
            player.sendMessage(formatPurchaseMessage(player, 0));
        }
    }

    public void sellAll(Player player) {
        ActionShop adjusted = adjustForPlayer(player);
        int transactionCount = Math.min(adjusted.cost.getMaximumMultiplier(player), getPlayerMaxPurchase(player));
        adjusted.doTransaction(player, transactionCount);
    }

    public ActionShop adjustForPlayer(Player player) {
        return new ActionShop(
                cost instanceof IFlexibleShopWants ?
                        ((IFlexibleShopWants) cost).adjustForPlayer(player) : cost,
                reward instanceof IFlexibleShopWants ?
                        ((IFlexibleShopWants) reward).adjustForPlayer(player) : reward,
                purchaseLimitTemplate, purchaseLimit);
    }

    public int getPlayerMaxPurchase(Player player) {
        if (purchaseLimitTemplate == null) {
            return Integer.MAX_VALUE;
        }
        PurchaseRecords records = PurchaseRecords.getCopy(player);
        PurchaseRecords.RecordStorage storage = records.applyTemplate(purchaseLimitTemplate);
        int totalPurchases = storage.getTotalPurchases();
        return purchaseLimit - totalPurchases;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player)e.getWhoClicked();
        Optional<InventoryContents> parentContentsOptional = WorstShop.get().inventories.getContents(player);
        if (parentContentsOptional.isPresent()) {
            InventoryContents parentContents = parentContentsOptional.get();
            // skip close event once
            parentContents.setProperty("skipOnce", true);
            // open a shop 1 tick later
            final ActionShop self = this.adjustForPlayer(player);
            (new BukkitRunnable() {
                @Override
                public void run() {
                    ShopGui.getInventory(player, self, parentContents.inventory())
                            .open(player);

                    // undo skip close event in case there is none
                    parentContents.setProperty("skipOnce", false);
                }
            }).runTask(WorstShop.get());
        }
    }

    public static String formatNothingMessage() {
        return I18n.translate("worstshop.messages.shops.transaction-nothing");
    }

    public String formatPurchaseMessage(Player player, double buyCount) {
        if (buyCount <= 0) {
            return formatNothingMessage();
        }
        ActionShop adjusted = adjustForPlayer(player);
        return I18n.translate("worstshop.messages.shops.transaction-message",
                buyCount,
                adjusted.cost.multiply(buyCount).getPlayerResult(player, ShopWants.TransactionType.COST),
                adjusted.reward.multiply(buyCount).getPlayerResult(player, ShopWants.TransactionType.REWARD)
        );
    }

    public String formatPurchaseLimitMessage(Player player) {
        PurchaseRecords records = PurchaseRecords.getCopy(player);
        PurchaseRecords.RecordStorage storage = records.applyTemplate(purchaseLimitTemplate);
        List<Map.Entry<LocalDateTime, Integer>> entries = storage.getEntries();
        Duration wait;
        if (entries.size() == 0) {
            wait = Duration.ofSeconds(0);
        } else {
            LocalDateTime firstPurchase = entries.get(0).getKey();
            LocalDateTime nextPurchase = firstPurchase.plus(storage.retentionTime);
            wait = Duration.between(LocalDateTime.now(), nextPurchase);
        }
        return I18n.translate("worstshop.messages.shops.transaction-limit-reached",
                purchaseLimit,
                DateTimeUtils.formatTime(purchaseLimitTemplate.retentionTime),
                DateTimeUtils.formatTime(wait)
        );
    }

    public String formatRefundMessage(Player player, double refundAmount) {
        if (refundAmount > 0) {
            ActionShop adjusted = adjustForPlayer(player);
            return I18n.translate("worstshop.messages.shops.transaction-refund",
                    refundAmount,
                    adjusted.cost.multiply(refundAmount).getPlayerResult(player, ShopWants.TransactionType.REWARD)
            );
        }
        return "";
    }

    @Override
    public void readElement(ShopElement element) {
        boolean isStatic = element instanceof StaticShopElement;
        if (isStatic) {
            StaticShopElement e = (StaticShopElement) element;
            if (cost == null) {
                cost = new ShopWantsItem(e.rawStack); // copy from parent
            }
            if (reward == null) {
                reward = new ShopWantsItem(e.rawStack); // copy from parent
            }
        }
        if (cost != null && cost instanceof IParentElementReader) {
            ((IParentElementReader) cost).readElement(element);
        }
        if (reward != null && reward instanceof IParentElementReader) {
            ((IParentElementReader) reward).readElement(element);
        }
    }

    public static class ShopGui implements InventoryProvider {
        private boolean firstClick = true;
        private ActionShop shop;
        private ShopWants cost, reward;
        private ShopElement costElem, rewardElem;
        private ShopGui(ActionShop shop) {
            this.shop = shop;
            this.cost = shop.cost;
            this.reward = shop.reward;
        }

        public static SmartInventory getInventory(Player player, ActionShop shop, SmartInventory parent) {
            return SmartInventory.builder()
                    .title(
                            I18n.translate("worstshop.messages.shops.shop", player)
                    ).manager(WorstShop.get().inventories)
                    .type(InventoryType.CHEST).size(6, 9)
                    .provider(new ShopGui(shop)).parent(parent)
                    .listener(new InventoryCloseListener()).build();
        }

        private static ClickableItem FILLER = ClickableItem
                .empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());
        private static ClickableItem ARROW = ClickableItem
                .empty(ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());
        private static ClickableItem GREEN = ClickableItem
                .empty(ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());
        private static ClickableItem RED = ClickableItem
                .empty(ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(FILLER);

            costElem = cost.createElement(ShopWants.TransactionType.COST);
            costElem.populateItems(player, contents, null);
            rewardElem = reward.createElement(ShopWants.TransactionType.REWARD);
            rewardElem.populateItems(player, contents, null);

            updateItemCount(player, contents);
            if (cost.canMultiply() && reward.canMultiply())
                populateBuyCountChangeButtons(player, contents);
            updateCanAfford(player, contents);

            // player balance etc
            contents.set(5, 4, ClickableItem.empty(
               ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(ChatColor.WHITE + player.getDisplayName()).skullOwner(player)
                    .lore(Arrays.asList(cost.getPlayerTrait(player).split("\\n"))).build()
            ));

            // ok button
            contents.set(5, 3, ClickableItem.of(
                    ItemBuilder.of(Material.GREEN_TERRACOTTA)
                            .name(I18n.translate("worstshop.messages.shops.buttons.confirm"))
                            .build(), this::doTransaction
            ));
            // cancel button
            contents.set(5, 5, ClickableItem.of(
                    ItemBuilder.of(Material.RED_TERRACOTTA)
                            .name(I18n.translate("worstshop.messages.shops.buttons.cancel"))
                            .build(), e-> contents.inventory().close(player)
            ));
            // purchase limit
            if (shop.purchaseLimitTemplate != null) {
                List<String> lore = Lists.newArrayList(I18n.translate(
                        I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.limit",
                        shop.purchaseLimit, DateTimeUtils.formatTime(shop.purchaseLimitTemplate.retentionTime)
                ), I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.previous-purchases"));
                PurchaseRecords.RecordStorage records = PurchaseRecords.getCopy(player).applyTemplate(shop.purchaseLimitTemplate);

                LocalDateTime now = LocalDateTime.now();
                records.getEntries().stream().map(entry ->
                        I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.previous-purchase-entry",
                                entry.getValue(), DateTimeUtils.formatTime(Duration.between(entry.getKey(), now)))
                ).forEach(lore::add);
                contents.set(5, 0, ClickableItem.empty(
                        ItemBuilder.of(Material.CLOCK)
                                .name(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.name"))
                                .lore(lore).build()
                ));
            }
            // maximize purchase button
            if (cost.canMultiply() && reward.canMultiply())
                contents.set(5, 8, ClickableItem.of(
                        ItemBuilder.of(Material.HOPPER)
                            .name(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.maximize-purchase"))
                            .build(), e-> buyCount = Math.min(cost.getMaximumMultiplier(player), shop.getPlayerMaxPurchase(player))
                ));
        }

        // animation
        int tickCounter = 0;
        int animationSequence = 0;
        int buyCount = 1;
        int lastBuyCount = buyCount;

        int calculateItemColumn(boolean offset) {
            return (animationSequence == 4 ? 2 : animationSequence + 3);
        }

        private Consumer<InventoryClickEvent> createBuyCountChanger(int changeSize) {
            return e->{
                if (firstClick) {
                    buyCount = changeSize;
                    firstClick = false;
                } else {
                    buyCount += changeSize;
                }
                if (buyCount <= 0) {
                    buyCount = 1;
                }
            };
        }

        private void doTransaction(InventoryClickEvent e) {
            Player player = (Player) e.getWhoClicked();
            Bukkit.getScheduler().runTask(WorstShop.get(), ()->{
                player.closeInventory();
                shop.doTransaction(player, buyCount);
            });
        }

        private static List<Integer> BUTTON_SIZE = Lists.newArrayList(1, 4, 16, 64);
        private void populateBuyCountChangeButtons(Player player, InventoryContents contents) {
            ListIterator<Integer> iterator = BUTTON_SIZE.listIterator();
            int correctedBuyCount = buyCount - (firstClick ? 1 : 0);
            while (iterator.hasNext()) {
                int index = iterator.nextIndex() + 1;
                int number = iterator.next();

                contents.set(4, 4 + index, ClickableItem.of(
                        ItemBuilder.of(Material.LIME_STAINED_GLASS)
                                .name(I18n.translate("worstshop.messages.shops.buy-counts.increase-by", number))
                                .lores(I18n.translate(
                                        "worstshop.messages.shops.buy-counts.change-result", correctedBuyCount + number
                                ))
                                .amount(number)
                                .build(),
                        createBuyCountChanger(number)
                ));
                contents.set(4, 4 - index, ClickableItem.of(
                        ItemBuilder.of(Material.RED_STAINED_GLASS)
                                .name(I18n.translate("worstshop.messages.shops.buy-counts.decrease-by", number))
                                .lores(I18n.translate(
                                        "worstshop.messages.shops.buy-counts.change-result", Math.max(1, correctedBuyCount - number)
                                ))
                                .amount(number)
                                .build(),
                        createBuyCountChanger(-number)
                ));
            }
        }

        @Override
        public void update(Player player, InventoryContents contents) {
            if (lastBuyCount != buyCount) {
                updateItemCount(player, contents);
                updateCanAfford(player, contents);
                populateBuyCountChangeButtons(player, contents);
                lastBuyCount = buyCount;
            }
            updateAnimation(player, contents);
        }

        private void updateItemCount(Player player, InventoryContents contents) {
            contents.set(4, 4, ClickableItem.empty(
                    ItemBuilder.of(Material.END_CRYSTAL)
                    .name(I18n.translate("worstshop.messages.shops.buy-counts.total", buyCount)).build()
            ));
        }

        private void updateCanAfford(Player player, InventoryContents contents) {
            if (shop.cost.multiply(buyCount).canAfford(player) && buyCount <= shop.getPlayerMaxPurchase(player)) {
                // green
                contents.fillRow(3, GREEN);
            } else {
                // red
                contents.fillRow(3, RED);
            }
        }

        private void updateAnimation(Player player, InventoryContents contents) {
            if (++tickCounter > 5) {
                animationSequence = animationSequence == 4 ? 0 : animationSequence + 1;
                tickCounter = 0;
            } else {
                return;
            }
            // clear
            for(int row = 0; row <= 2; row++) {
                for(int column = 2; column <= 6; column++) {
                    contents.set(row, column, FILLER);
                }
            }

            contents.set(0, animationSequence + 2, ARROW);
            contents.set(1, calculateItemColumn(true), ARROW);
            contents.set(2, animationSequence + 2, ARROW);

            // also update item lol
            if (animationSequence == 4) {
                if (shop.cost.isElementDynamic()) {
                    costElem.populateItems(player, contents, null);
                }
                if (shop.reward.isElementDynamic()) {
                    rewardElem.populateItems(player, contents, null);
                }
            }
        }
    }
}
