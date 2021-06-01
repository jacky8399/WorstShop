package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.InventoryCloseListener;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.commodity.*;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.SlotPos;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.api.QuickShopAPI;
import org.maxgamer.quickshop.api.ShopAPI;
import org.maxgamer.quickshop.event.ShopCreateEvent;
import org.maxgamer.quickshop.event.ShopDeleteEvent;
import org.maxgamer.quickshop.event.ShopItemChangeEvent;
import org.maxgamer.quickshop.shop.Info;
import org.maxgamer.quickshop.shop.Shop;
import org.maxgamer.quickshop.shop.ShopAction;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActionPlayerShop extends Action {
    public static final String I18N_KEY = "worstshop.messages.shops.player-shop.";
    static {
        try {
            ShopAPI shopAPI = QuickShopAPI.getShopAPI();
            cache = new ShopCache();
        } catch (Throwable e) {
            throw new IllegalStateException("QuickShop is not loaded!", e);
        }
    }

    ShopElement parentElement;
    public ActionPlayerShop(Config yaml) {
        super(yaml);
        ShopElement element = ParseContext.findLatest(ShopElement.class);
        if (element == null)
            throw new IllegalStateException("Couldn't find parent element! Not in parse context?");
        parentElement = element.clone();
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        boolean isBuying = e.getClick().isLeftClick();
        SmartInventory gui = getInventory(player, WorstShop.get().inventories.getInventory(player).orElse(null), isBuying);
        InventoryCloseListener.openSafely(player, gui);
    }

    @Override
    public void influenceItem(Player player, ItemStack readonlyStack, ItemStack stack) {
        ItemBuilder builder = ItemBuilder.from(stack);
        List<Double> buyShops = findAllShops(player, true).map(Shop::getPrice).collect(Collectors.toList()),
                sellShops = findAllShops(player, false).map(Shop::getPrice).collect(Collectors.toList());
        builder.addLores(
                I18n.translate(I18N_KEY + "buy", buyShops.size(), buyShops.size() != 0 ?
                        CommodityMoney.formatMoney(Collections.min(buyShops)) :
                        I18n.translate(I18N_KEY + "no-available-price")
                ),
                I18n.translate(I18N_KEY + "sell", sellShops.size(), sellShops.size() != 0 ?
                        CommodityMoney.formatMoney(Collections.max(sellShops)) :
                        I18n.translate(I18N_KEY + "no-available-price")
                )
        );
        builder.build();
    }

    public ItemStack getTargetItemStack(Player player) {
        return parentElement instanceof StaticShopElement ? ((StaticShopElement) parentElement).createPlaceholderStack(player) : parentElement.createStack(player);
    }

    public Commodity createFakeCommodity(Commodity.TransactionType type) {
        return new CommodityCustomizable(CommodityFree.INSTANCE,
                StaticShopElement.fromStack(
                        ItemBuilder.of(Material.GOLD_INGOT)
                                .name(I18n.translate(I18N_KEY +
                                        (type == Commodity.TransactionType.COST ? "buy" : "sell") + "-amount-prompt"))
                                .lores(I18n.translate(I18N_KEY + "confirmation-info"))
                                .build()));
    }

    public ActionShop createFakeShop(Player player, boolean isBuyingItem) {
        Commodity fakeCost = createFakeCommodity(isBuyingItem ? Commodity.TransactionType.COST : Commodity.TransactionType.REWARD),
                item = new CommodityItem(getTargetItemStack(player));
        return isBuyingItem ?
                new ActionShop(fakeCost, item, null, 0) :
                new ActionShop(item, fakeCost, null, 0);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "player_shop");
        return map;
    }

    SmartInventory getInventory(Player player, SmartInventory parent, boolean isBuying) {
        return WorstShop.buildGui("worstshop:player_shop_gui")
                .title(I18n.translate("worstshop.messages.shops.shop", player))
                .type(InventoryType.CHEST).size(6, 9)
                .provider(new ShopGui(player, isBuying)).parent(parent)
                .build();
    }

    public Stream<Shop> findAllShops(Player player, boolean isBuying) {
        ItemStack stack = getTargetItemStack(player);
        List<org.maxgamer.quickshop.shop.Shop> shops = cache.shops.get(stack.getType());
        if (shops == null)
            return Stream.empty();

        Comparator<org.maxgamer.quickshop.shop.Shop> comparator = Comparator.comparingDouble(org.maxgamer.quickshop.shop.Shop::getPrice);
        if (!isBuying) {
            comparator = comparator.reversed();
        }
        return shops.stream()
                .filter(shop -> (isBuying ? shop.isSelling() : shop.isBuying()) && shop.matches(stack))
                .sorted(comparator);
    }

    private static final SlotPos[] checkMarkPos = {SlotPos.of(2, 3), SlotPos.of(3, 4), SlotPos.of(2, 5), SlotPos.of(1, 6)};
    class ShopGui extends ActionShop.ShopGui {
        boolean isBuying;
        int maxBuyCount;
        protected ShopGui(Player player, boolean isBuying) {
            super(createFakeShop(player, isBuying));
            this.isBuying = isBuying;
            this.buyCount = 0;
            this.maxBuyCount = getTargetItemStack(player).getMaxStackSize() * 36;
        }

        class PurchaseStrategy {
            public final org.maxgamer.quickshop.shop.Shop shop;
            public final int buyCount;
            public final Info info;

            PurchaseStrategy(Shop shop, int buyCount) {
                this.shop = shop;
                this.buyCount = buyCount;
                info = new Info(shop.getLocation(), ShopAction.BUY, shop.getItem(), shop.getLocation().getBlock(), shop);
            }
        }

        List<PurchaseStrategy> purchases = null;
        int unfulfilledAmount;

        int animationSequence = 0;
        int tick = 3;

        @Override
        public void update(Player player, InventoryContents contents) {
            if (purchases == null) {
                super.update(player, contents);
                if (buyCount > maxBuyCount) {
                    buyCount = maxBuyCount;
                }
            } else if (animationSequence != 0) {
                // horrible code
                if (++tick == 4) {
                    tick = 0;
                    if (animationSequence > 0) {
                        for (int i = 0; i < 3; i++) {
                            contents.set(2, 3 + i, i == animationSequence ? RED : FILLER);
                        }
                    } else if (animationSequence > -5) { // check mark

                        SlotPos pos = checkMarkPos[Math.abs(animationSequence--) - 1];
                        contents.set(pos, GREEN);
                    } else if (animationSequence-- == -10) {
                        contents.inventory().close(player);
                    }
                }
            }
        }

        @Override
        protected void doTransaction(InventoryClickEvent e) {
            Player player = (Player) e.getWhoClicked();
            ItemStack stack = getTargetItemStack(player);
            List<org.maxgamer.quickshop.shop.Shop> shops = cache.shops.get(stack.getType());
            int[] targetAmount = {buyCount};

            if (shops == null) { // no shop providing this item
                purchases = Collections.emptyList();
                unfulfilledAmount = buyCount;
                showConfirmation(player);
                return;
            }
            purchases = findAllShops(player, isBuying)
                    .sequential()
                    .map(s -> {
                        int stock = isBuying ? s.getRemainingStock() : s.getRemainingSpace();
                        if (stock < targetAmount[0]) {
                            targetAmount[0] -= stock;
                            return new PurchaseStrategy(s, stock);
                        } else {
                            int purchaseCount = targetAmount[0];
                            targetAmount[0] = 0;
                            return new PurchaseStrategy(s, purchaseCount);
                        }
                    })
                    .filter(strategy -> strategy.buyCount != 0)
                    .collect(Collectors.toList());

            unfulfilledAmount = targetAmount[0];
            showConfirmation(player);
        }

        protected void showConfirmation(Player player) {
            InventoryContents contents = WorstShop.get().inventories.getContents(player).orElseThrow(()->new IllegalStateException("No inventory?"));
            contents.fill(FILLER);
            // confirmation
            I18n.Translatable translatable = new I18n.Translatable(I18N_KEY + (isBuying ? "buying-from" : "selling-to"));
            contents.set(2, 4, ItemBuilder.of(Material.PAPER)
                    .name(I18n.translate(I18N_KEY + "confirmation"))
                    .lore(purchases.stream()
                            .map(strategy -> translatable.apply(
                                    Integer.toString(strategy.buyCount),
                                    strategy.shop.ownerName(),
                                    CommodityMoney.formatMoney(strategy.shop.getPrice()),
                                    CommodityMoney.formatMoney(strategy.shop.getPrice() * strategy.buyCount)
                            ))
                            .collect(Collectors.toList())
                    )
                    .addLore(unfulfilledAmount == 0 ?
                            Collections.emptyList() :
                            Collections.singletonList(I18n.translate(I18N_KEY + "failed-to-fulfill", unfulfilledAmount)))
                    .toEmptyClickable());

            // ok button
            contents.set(5, 3, ClickableItem.of(
                    ItemBuilder.of(Material.GREEN_TERRACOTTA)
                            .name(I18n.translate("worstshop.messages.shops.buttons.confirm"))
                            .build(), this::confirmPurchase
            ));
            // cancel button
            contents.set(5, 5, ClickableItem.of(
                    ItemBuilder.of(Material.RED_TERRACOTTA)
                            .name(I18n.translate("worstshop.messages.shops.buttons.cancel"))
                            .build(), e -> contents.inventory().close(player)
            ));
        }

        protected void confirmPurchase(InventoryClickEvent e) {
            InventoryContents contents = WorstShop.get().inventories.getContents((Player) e.getWhoClicked())
                    .orElseThrow(()->new IllegalStateException("No inventory?"));
            contents.fill(FILLER);
            animationSequence = 1;
            Player player = (Player) e.getWhoClicked();
            QuickShop qs = QuickShop.getInstance();
            CompletableFuture<?>[] futures = purchases.stream().map(purchase -> {
                Shop qShop = purchase.shop;
                CompletableFuture<Chunk> future = qShop.isLoaded() ?
                        CompletableFuture.completedFuture(null) :
                        PaperLib.getChunkAtAsync(qShop.getLocation(), false);
                return future.thenCompose(ignored -> {
                    CompletableFuture<Void> future2 = new CompletableFuture<>();
                    Bukkit.getScheduler().runTask(WorstShop.get(), () -> {
                        if (isBuying)
                            qs.getShopManager().actionSell(player.getUniqueId(), player.getInventory(), qs.getEconomy(), purchase.info, qShop, purchase.buyCount);
                        else
                            qs.getShopManager().actionBuy(player.getUniqueId(), player.getInventory(), qs.getEconomy(), purchase.info, qShop, purchase.buyCount);
                        future2.complete(null);
                    });
                    return future2;
                });
            }).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).thenRun(() -> {
                contents.fill(FILLER);
                animationSequence = -1;
            });
        }
    }

    private static ShopCache cache;
    private static class ShopCache implements Listener {
        ShopCache() {
            Bukkit.getScheduler().runTask(WorstShop.get(), this::refreshCache);
            Bukkit.getPluginManager().registerEvents(this, WorstShop.get());
        }

        HashMap<Material, List<org.maxgamer.quickshop.shop.Shop>> shops = new HashMap<>();

        public void refreshCache() {
            List<org.maxgamer.quickshop.shop.Shop> shops = QuickShopAPI.getShopAPI().getAllShops();
            for (org.maxgamer.quickshop.shop.Shop shop : shops) {
                ItemStack stack = shop.getItem();
                Material mat = stack.getType();
                List<org.maxgamer.quickshop.shop.Shop> shopz = this.shops.computeIfAbsent(mat, key->new ArrayList<>());
                shopz.add(shop);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopItemChange(ShopItemChangeEvent e) {
            org.maxgamer.quickshop.shop.Shop shop = e.getShop();
            ItemStack oldStack = e.getOldItem(), newStack = e.getNewItem();
            List<org.maxgamer.quickshop.shop.Shop> shopz = shops.get(oldStack.getType()),
                    newShopz = shops.computeIfAbsent(newStack.getType(), key->new ArrayList<>());
            if (shopz != null) {
                shopz.remove(shop);
            }
            newShopz.add(shop);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopCreate(ShopCreateEvent e) {
            org.maxgamer.quickshop.shop.Shop shop = e.getShop();
            List<org.maxgamer.quickshop.shop.Shop> shopz = shops.computeIfAbsent(shop.getItem().getType(), key->new ArrayList<>());
            shopz.add(shop);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopDelete(ShopDeleteEvent e) {
            org.maxgamer.quickshop.shop.Shop shop = e.getShop();
            List<org.maxgamer.quickshop.shop.Shop> shopz = shops.get(shop.getItem().getType());
            shopz.remove(shop);
        }
    }
}
