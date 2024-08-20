package com.jacky8399.worstshop.shops.actions;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.event.ShopCreateEvent;
import com.ghostchu.quickshop.api.event.ShopDeleteEvent;
import com.ghostchu.quickshop.api.event.ShopItemChangeEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.shop.SimpleInfo;
import com.ghostchu.quickshop.shop.SimpleShopManager;
import com.ghostchu.quickshop.shop.inventory.BukkitInventoryWrapper;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.i18n.ComponentTranslatable;
import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.shops.commodity.*;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.SlotPos;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Item shop but items are from QuickShop shops
 */
public class ActionPlayerShop extends ActionPlayerShopFallback {
    public static final String I18N_KEY = "worstshop.messages.shops.player-shop.";
    static {
        try {
            QuickShopAPI api = QuickShopAPI.getInstance();
            cache = new ShopCache(api);
        } catch (Throwable e) {
            throw new IllegalStateException("QuickShop is not loaded!", e);
        }
    }

    public ActionPlayerShop(Config yaml) {
        super(yaml);
    }

    public ActionPlayerShop(String shorthand) {
        super(shorthand);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        boolean isBuying = e.getClick().isLeftClick();
        // check for offers
        if (findAllShops(player, isBuying).findAny().isPresent()) {
            SmartInventory gui = getInventory(player, InventoryUtils.getInventory(player), isBuying);
            InventoryUtils.openSafely(player, gui);
        } else if (fallback != null && (isBuying ? fallback.buyPrice : fallback.sellPrice) != 0) {
            fallback.onClick(e);
        }
    }

    @Override
    public void influenceItem(Player player, ItemStack readonlyStack, ItemBuilder builder) {
        DoubleSummaryStatistics buyShops = findAllShops(player, true).collect(Collectors.summarizingDouble(Shop::getPrice)),
                sellShops = findAllShops(player, false).collect(Collectors.summarizingDouble(Shop::getPrice));
        List<Component> lines = new ArrayList<>();
        if (buyShops.getCount() == 0 && fallback != null && fallback.buyPrice != 0) {
            lines.addAll(ItemShopFormatter.formatServerBuy(fallback, player));
        } else {
            lines.addAll(ItemShopFormatter.formatPlayerBuy((int) buyShops.getCount(), buyShops.getMin()));
        }
        if (sellShops.getCount() == 0 && fallback != null && fallback.sellPrice != 0) {
            lines.addAll(ItemShopFormatter.formatServerSell(fallback, player));
        } else {
            lines.addAll(ItemShopFormatter.formatPlayerSell((int) sellShops.getCount(), sellShops.getMax()));
        }
        builder.addLore(lines);
    }

    public ItemStack getTargetItemStack(Player player) {
        ItemStack stack = parentElement instanceof StaticShopElement sse ?
                sse.createPlaceholderStack(player) : parentElement.createStack(player);
        return ItemUtils.removeSafetyKey(stack);
    }

    public Commodity createDynamicCommodity(boolean isBuying) {
        // dynamic element
        // changed at ShopGui.updateCommodities
        return new CommodityCustomizable(CommodityFree.INSTANCE,
                new DynamicShopElement() {
                    @Override
                    public ItemStack createStack(Player player) {
                        return ItemBuilder.of(Material.GOLD_INGOT)
                                .name(I18n.translateComponent(I18N_KEY + (isBuying ? "buy" : "sell") + "-amount-prompt"))
                                .lores(I18n.translateComponent(I18N_KEY + "confirmation-info"))
                                .build();
                    }
                });
    }

    public ActionShop createFakeShop(Player player, boolean isBuyingItem) {
        Commodity fakeCost = createDynamicCommodity(isBuyingItem),
                item = new CommodityItem(getTargetItemStack(player));
        return isBuyingItem ? new ActionShop(fakeCost, item) : new ActionShop(item, fakeCost);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "player_shop");
        return map;
    }

    SmartInventory getInventory(Player player, SmartInventory parent, boolean isBuying) {
        return WorstShop.buildGui("worstshop:player_shop_gui")
                .title(I18n.translate("worstshop.messages.shops.shop"))
                .type(InventoryType.CHEST).size(6, 9)
                .provider(new ShopGui(player, isBuying)).parent(parent)
                .build();
    }

    /**
     * Find all QuickShop shops matching the transaction type and the item stack {@link ActionPlayerShop#getTargetItemStack(Player)}
     * @param player Player
     * @param isBuying Whether the player is buying items
     * @return A stream of applicable shops ordered by their price
     */
    public Stream<Shop> findAllShops(Player player, boolean isBuying) {
        ItemStack stack = getTargetItemStack(player);
        List<Shop> shops = cache.shops.get(stack.getType());
        if (shops == null)
            return Stream.empty();

        Comparator<Shop> comparator = Comparator.comparingDouble(Shop::getPrice);
        if (!isBuying) {
            comparator = comparator.reversed();
        }
        return shops.stream()
                .filter(shop -> {
                    if (isBuying == shop.isBuying()) // player buying = shop selling
                        return false;
                    if ((isBuying ? shop.getRemainingStock() : shop.getRemainingSpace()) <= 0) // has stock
                        return false;
                    return shop.matches(stack);
                })
                .sorted(comparator);
    }

    private record PurchaseStrategySummary(List<PurchaseStrategy> strategies, int unfulfilled) {
        private static final ComponentTranslatable GRAND_TOTAL = I18n.createComponentTranslatable(I18N_KEY + "confirmation-grand-total");
        private static final ComponentTranslatable BUYING_FROM = I18n.createComponentTranslatable(I18N_KEY + "buying-from");
        private static final ComponentTranslatable SELLING_TO = I18n.createComponentTranslatable(I18N_KEY + "selling-to");

        public double grandTotal() {
            return strategies.stream().mapToDouble(PurchaseStrategy::total).sum();
        }

        public ItemBuilder getDisplay(boolean isBuying) {
            double grandTotal = grandTotal();
            Component grandTotalDisplay = GRAND_TOTAL.apply(CommodityMoney.formatMoneyComponent(grandTotal));
            ComponentTranslatable translatable = isBuying ? BUYING_FROM : SELLING_TO;

            var denomination = CommodityMoney.getDenomination(grandTotal);
            return ItemBuilder.of(denomination.material())
                    .maxAmount(99)
                    .amount(Math.min(denomination.getAmount(grandTotal), 99))
                    .name(I18n.translateComponent(I18N_KEY + "confirmation"))
                    .lore(strategies.stream()
                            .map(strategy -> translatable.apply(
                                    Component.text(strategy.buyCount),
                                    strategy.shop.ownerName(),
                                    CommodityMoney.formatMoneyComponent(strategy.shop.getPrice()),
                                    CommodityMoney.formatMoneyComponent(strategy.shop.getPrice() * strategy.buyCount)
                            ))
                            .toList()
                    )
                    .addLore(unfulfilled == 0 ?
                            List.of(grandTotalDisplay) :
                            List.of(grandTotalDisplay, I18n.translateComponent(I18N_KEY + "failed-to-fulfill." + (isBuying ? "buy" : "sell"),
                                    Component.text(unfulfilled))));
        }
    }

    private record PurchaseStrategy(Shop shop, int buyCount, SimpleInfo info) {
        PurchaseStrategy(Shop shop, int buyCount) {
            this(shop, buyCount, new SimpleInfo(shop.getLocation(),
                    com.ghostchu.quickshop.api.shop.ShopAction.PURCHASE_BUY, shop.getItem(), shop.getLocation().getBlock(), shop, false));
        }

        public double total() {
            return shop.getPrice() * buyCount;
        }
    }

    /**
     * Devise an optimal strategy to trade items with QuickShop shops, earning the maximum or losing the minimum.
     * @param player The player
     * @param isBuying Whether the playing is trying to purchase or not
     * @param targetAmount The target amount
     * @return The strategy
     */
    private PurchaseStrategySummary deviseStrategy(Player player, boolean isBuying, int targetAmount) {
        // check for any shops
        ItemStack stack = getTargetItemStack(player);
        List<Shop> shops = cache.shops.get(stack.getType());
        if (shops == null) {
            return new PurchaseStrategySummary(Collections.emptyList(), targetAmount);
        }
        int[] amount = {targetAmount};
        List<PurchaseStrategy> strategies = findAllShops(player, isBuying)
                .map(s -> {
                    int stock;
                    if (s.isUnlimited()) {
                        stock = Integer.MAX_VALUE;
                    } else {
                        stock = isBuying ? s.getRemainingStock() : s.getRemainingSpace();
                    }
                    if (stock < amount[0]) {
                        amount[0] -= stock;
                        return new PurchaseStrategy(s, stock);
                    } else {
                        int purchaseCount = amount[0];
                        amount[0] = 0;
                        return new PurchaseStrategy(s, purchaseCount);
                    }
                })
                .filter(strategy -> strategy.buyCount > 0)
                .toList();
        return new PurchaseStrategySummary(strategies, amount[0]);
    }

    class ShopGui extends ActionShop.ShopGui {
        private static final SlotPos[] checkMarkPos = {SlotPos.of(2, 3), SlotPos.of(3, 4), SlotPos.of(2, 5), SlotPos.of(1, 6)};
        boolean isBuying;
        protected ShopGui(Player player, boolean isBuying) {
            super(createFakeShop(player, isBuying));
            this.isBuying = isBuying;
            this.buyCount = 0;
        }

        PurchaseStrategySummary purchaseSummary = null;

        int animationSequence = 0;
        int tick = 3;

        @Override
        public void update(Player player, InventoryContents contents) {
            // Let parent handle animation while not confirmed
            if (purchaseSummary == null) {
                super.update(player, contents);
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

        // flash between BARRIER and GOLD_INGOT if anything is out of stock
        boolean outOfStockBlink = false;
        @Override
        protected void updateCommodities(Player player, InventoryContents contents, boolean dynamicOnly) {
            super.updateCommodities(player, contents, dynamicOnly);
            // estimate cost and update accordingly
            if (dynamicOnly) {
                PurchaseStrategySummary strategySummary = deviseStrategy(player, isBuying, buyCount);
                ItemBuilder builder = strategySummary.getDisplay(isBuying);
                if (outOfStockBlink && strategySummary.unfulfilled != 0)
                    builder.type(Material.BARRIER);
                ItemStack displayStack = builder
                        // remove references to confirmation
                        .name(I18n.translateComponent(I18N_KEY + (isBuying ? "buy" : "sell") + "-amount-prompt"))
                        .addLores(I18n.translateComponent(I18N_KEY + "confirmation-info"))
                        .build();
                if (isBuying) {
                    costElem = Commodity.TransactionType.COST.createElement(displayStack);
                    populateItems(player, costElem, contents);
                } else {
                    rewardElem = Commodity.TransactionType.REWARD.createElement(displayStack);
                    populateItems(player, rewardElem, contents);
                }
                outOfStockBlink = !outOfStockBlink;
            }
        }

        @Override
        protected void doTransaction(InventoryClickEvent e) {
            Player player = (Player) e.getWhoClicked();
            if (buyCount == 0) {
                player.closeInventory();
            } else {
                purchaseSummary = deviseStrategy(player, isBuying, buyCount);
                showConfirmation(player);
            }
        }

        protected void showConfirmation(Player player) {
            InventoryContents contents = WorstShop.get().inventories.getContents(player).orElseThrow(IllegalStateException::new);
            contents.fill(FILLER);
            // confirmation
            contents.set(2, 4, purchaseSummary.getDisplay(isBuying).toEmptyClickable());
            // ok button
            contents.set(5, 3, ClickableItem.of(
                    ItemBuilder.of(Material.GREEN_TERRACOTTA)
                            .name(I18n.translateAsComponent("worstshop.messages.shops.buttons.confirm"))
                            .build(), this::confirmPurchase
            ));
            // cancel button
            contents.set(5, 5, ClickableItem.of(
                    ItemBuilder.of(Material.RED_TERRACOTTA)
                            .name(I18n.translateComponent("worstshop.messages.shops.buttons.cancel"))
                            .build(), e -> contents.inventory().close(player)
            ));
        }

        // loads the chunks and delegates the transaction to QuickShop
        // probably not a good place to put a critical feature
        protected void confirmPurchase(InventoryClickEvent e) {
            InventoryContents contents = WorstShop.get().inventories.getContents((Player) e.getWhoClicked())
                    .orElseThrow(() -> new IllegalStateException("No inventory?"));
            contents.fill(FILLER);
            animationSequence = 1;
            Player player = (Player) e.getWhoClicked();
            BukkitInventoryWrapper inventoryWrapper = new BukkitInventoryWrapper(player.getInventory());

            QuickShop qs = QuickShop.getInstance();
            CompletableFuture<?>[] futures = purchaseSummary.strategies.stream().map(purchase -> {
                Shop qShop = purchase.shop;
                CompletableFuture<Chunk> future = qShop.isLoaded() ?
                        CompletableFuture.completedFuture(null) :
                        PaperLib.getChunkAtAsync(qShop.getLocation(), false);
                return future.thenCompose(ignored -> {
                    CompletableFuture<Void> future2 = new CompletableFuture<>();
                    SimpleShopManager qsManager = (SimpleShopManager) qs.getShopManager();
                    Bukkit.getScheduler().runTask(WorstShop.get(), () -> {
                        if (isBuying)
                            qsManager.actionSelling(player, inventoryWrapper, qs.getEconomy(), purchase.info, qShop, purchase.buyCount);
                        else
                            qsManager.actionBuying(player, inventoryWrapper, qs.getEconomy(), purchase.info, qShop, purchase.buyCount);
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

    public static void probeCache(CommandSender sender, @Nullable Material targetMaterial) {
        int total = 0;
        Comparator<Shop> sorter = Comparator.comparing(Shop::isBuying)
                .thenComparingDouble(shop -> shop.isBuying() ? shop.getPrice() : -shop.getPrice());
        var buy = Component.text("Buying @ ", NamedTextColor.GREEN);
        var sell = Component.text("Selling @ ", NamedTextColor.RED);
        for (Map.Entry<Material, List<Shop>> entry : cache.shops.entrySet()) {
            Material mat = entry.getKey();
            if (targetMaterial != null && mat != targetMaterial) continue;
            List<Shop> shops = entry.getValue();
            sender.sendMessage(Component.textOfChildren(
                    Component.translatable(Objects.requireNonNull(mat.getItemTranslationKey())),
                    Component.text(":")
            ).color(NamedTextColor.GREEN));
            Component shopsComponent = shops.stream()
                    .sorted(sorter)
                    .map(shop -> {
                        Location location = shop.getLocation();
                        int x = location.getBlockX();
                        int y = location.getBlockY();
                        int z = location.getBlockZ();
                        return Component.textOfChildren(
                                Component.text(shop.getOwner().getDisplay() + " "),
                                shop.isBuying() ? buy : sell,
                                CommodityMoney.formatMoneyComponent(shop.getPrice()),
                                Component.text()
                                        .content("(" + location.getWorld().getName() + "," + x + ", " + y + ", " + z + ")")
                                        .color(NamedTextColor.AQUA)
                                        .clickEvent(ClickEvent.runCommand("/minecraft:tp " + x + " " + y + " " + z))
                        );
                    })
                    .collect(Component.toComponent(Component.newline()));
            sender.sendMessage(shopsComponent);
            total += shops.size();
        }

        sender.sendMessage(Component.text(total + " total shops", NamedTextColor.GREEN));
    }

    public static void reloadCache(CommandSender sender) {
        cache.refreshCache();
    }

    private static final ShopCache cache;
    private static class ShopCache implements Listener {
        private QuickShopAPI api;
        ShopCache(QuickShopAPI api) {
            this.api = api;
            Bukkit.getScheduler().runTask(WorstShop.get(), this::refreshCache);
            Bukkit.getPluginManager().registerEvents(this, WorstShop.get());
        }

        private final HashMap<Material, List<Shop>> shops = new HashMap<>();

        public void refreshCache() {
            List<Shop> allShops = api.getShopManager().getAllShops();
            for (Shop shop : allShops) {
                ItemStack stack = shop.getItem();
                Material mat = stack.getType();
                List<Shop> shops = this.shops.computeIfAbsent(mat, key->new ArrayList<>());
                shops.add(shop);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopItemChange(ShopItemChangeEvent e) {
            Shop shop = e.getShop();
            ItemStack oldStack = e.getOldItem(), newStack = e.getNewItem();
            List<Shop> shops = this.shops.get(oldStack.getType()),
                    newShops = this.shops.computeIfAbsent(newStack.getType(), key->new ArrayList<>());
            if (shops != null) {
                shops.remove(shop);
                if (shops.isEmpty())
                    this.shops.remove(oldStack.getType());
            }
            newShops.add(shop);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopCreate(ShopCreateEvent e) {
            Shop shop = e.getShop();
            List<Shop> shops = this.shops.computeIfAbsent(shop.getItem().getType(), key->new ArrayList<>());
            shops.add(shop);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopDelete(ShopDeleteEvent e) {
            Shop shop = e.getShop();
            ItemStack stack = shop.getItem();
            List<Shop> shops = this.shops.get(stack.getType());
            if (shops != null) {
                shops.remove(shop);
                if (shops.isEmpty())
                    this.shops.remove(stack.getType());
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        void onPluginDisable(PluginDisableEvent e) {
            if (e.getPlugin() instanceof QuickShopAPI) {
                shops.clear();
                api = null;
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        void onPluginEnable(PluginEnableEvent e) {
            if (e.getPlugin() instanceof QuickShopAPI qsAPI) {
                api = qsAPI;
                Bukkit.getScheduler().runTaskLater(WorstShop.get(), this::refreshCache, 1);
            }
        }
    }
}
