package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.Editable;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import net.md_5.bungee.api.ChatColor;
import org.apache.logging.log4j.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.permissions.Permissible;

import java.util.*;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Editable("shop")
public class Shop implements InventoryProvider, ParseContext.NamedContext {

    public static String SHOP_ID_PREFIX = "worstshop:shop/";

    public List<ShopElement> staticElements = Lists.newArrayList();
    public List<ShopElement> dynamicElements = Lists.newArrayList();

    public Shop() { }

    // basic properties
    public int rows;
    public InventoryType type;
    public String id;
    public String title;
    public int updateInterval;

    public Condition condition = ConditionConstant.TRUE;

    // parents
    public String parentShop = null;
    public boolean autoSetParentShop = false;

    // aliases
    public List<String> aliases;
    public boolean aliasesIgnorePermission;

    @Override
    public String getHierarchyName() {
        return "Shop[" + id + "]";
    }

    public static SmartInventory.Builder getDefaultBuilder() {
        return SmartInventory.builder().manager(WorstShop.get().inventories);
    }

    public boolean checkPlayerPerms(Permissible player) {
        return ShopManager.checkPermsOnly(player, id);
    }

    public boolean canPlayerView(Player player) {
        return canPlayerView(player, false);
    }

    public boolean canPlayerView(Player player, boolean isUsingAlias) {
        return (!isUsingAlias || aliasesIgnorePermission || checkPlayerPerms(player))
                && condition.test(player);
    }

    public SmartInventory getInventory(Player player) {
        return getInventory(player, false);
    }

    public SmartInventory getInventory(Player player, boolean skipAutoParent) {
        SmartInventory.Builder builder = getDefaultBuilder();
        builder.id(SHOP_ID_PREFIX + id).provider(this)
                .listener(new InventoryListener<>(InventoryCloseEvent.class, this::close))
                .type(type);
        if (type == InventoryType.CHEST) {
            builder.size(rows, 9);
        }
        if (autoSetParentShop && !skipAutoParent) {
            Optional<SmartInventory> inventory = WorstShop.get().inventories.getInventory(player);
            inventory.ifPresent(inv->{
                if (inv.getId().startsWith(SHOP_ID_PREFIX)) { // check is shop
                    builder.parent(inv); // oops
                }
            });
        }
        return builder.title(title).build();
    }

    @SuppressWarnings({"unchecked", "ConstantConditions", "null"})
    public static Shop fromYaml(String shopName, YamlConfiguration yaml) {
        Shop inst = new Shop();
        ShopManager.currentShop = inst;
        inst.id = shopName;

        Logger logger = WorstShop.get().logger;

        try {
            ParseContext.pushContext(inst);

            inst.rows = yaml.getInt("rows", 6);
            inst.type = InventoryType.valueOf(yaml.getString("type", "chest").toUpperCase());
            if (!yaml.isString("title"))
                throw new RuntimeException("No title set!");
            inst.title = ChatColor.translateAlternateColorCodes('&', yaml.getString("title"));
            inst.updateInterval = yaml.getInt("update-interval", 0);

            if (yaml.isSet("condition")) {
                Object obj = yaml.get("condition");
                if (obj instanceof Map<?, ?>) {
                    inst.condition = Condition.fromMap((Map<String, Object>) obj);
                }
            }

            inst.parentShop = yaml.getString("parent");
            if ("auto".equals(inst.parentShop)) {
                inst.autoSetParentShop = true;
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) yaml.getList("items", Collections.emptyList());
            for (Map<String, Object> itemSection : items) {
                ShopElement elem = ShopElement.fromYaml(itemSection);
                if (elem != null) {
                    if (elem instanceof DynamicShopElement)
                        inst.dynamicElements.add(elem);
                    else
                        inst.staticElements.add(elem);
                }
            }
            // commands
            if (yaml.isString("alias")) {
                String aliasString = yaml.getString("alias");
                inst.aliases = Arrays.stream(aliasString.split(",")).filter(Strings::isNotBlank)
                        .collect(Collectors.toList());

                if (inst.aliases.size() < 1)
                    inst.aliases = null;
                inst.aliasesIgnorePermission = yaml.getBoolean("alias-ignore-permission", false);
            }

            // should be self
            if (ParseContext.popContext() != inst) {
                throw new IllegalStateException("Stack is broken??");
            }
        } catch (Exception ex) {
            logger.severe("Error while parsing shop " + shopName + ", skipping.");
            logger.severe("Stack: " + ParseContext.getHierarchy());
            ex.printStackTrace();
        } finally {
            ParseContext.clear();
        }
        return inst;
    }

    public void populateElements(List<ShopElement> elementList,
                                 Player player, InventoryContents contents, PaginationHelper helper) {
        Logger logger = WorstShop.get().logger;
        ListIterator<ShopElement> iterator = elementList.listIterator();
        while (iterator.hasNext()) {
            int index = iterator.nextIndex();
            ShopElement element = iterator.next();
            if (element == null)
                continue;
            try {
                element.populateItems(player, contents, helper);
            } catch (Exception ex) {
                player.sendMessage(ChatColor.RED + "Error while populating item " + index + ": " + ex.toString());
                logger.severe("Error while populating item " + index + " in shop " + id);
                ex.printStackTrace();
            }
        }
    }

    public void refreshItems(Player player, InventoryContents contents, boolean updateDynamic) {
        // clear old items
        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0,0).allowOverride(true);
        while (!it.ended()) {
            it.next().set(null);
        }
        PaginationHelper helper = new PaginationHelper(contents);
        populateElements(staticElements, player, contents, helper);
        if (updateDynamic)
            populateElements(dynamicElements, player, contents, helper);
        // ensure pagination
        helper.doPaginationNow();
    }

    public static class PaginationHelper {
        SlotIterator slotIterator;
        Pagination pagination;
        InventoryContents contents;
        boolean hasDonePagination = false;
        ArrayList<ClickableItem> paginationItems;
        PaginationHelper(InventoryContents contents) {
            this.contents = contents;
            slotIterator = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0, 0).allowOverride(false);
            pagination = contents.pagination();
            paginationItems = Lists.newArrayList();
        }

        public void add(ClickableItem item) {
            paginationItems.add(item);
        }

        public void forEachRemaining(BiFunction<Integer, Integer, ClickableItem> supplier) {
            doPaginationNow();
            while (!slotIterator.ended()) {
                slotIterator.next();
                ClickableItem next = supplier.apply(slotIterator.row(), slotIterator.column());
                slotIterator.set(next);
            }
        }

        public InventoryContents getPrimitive() {
            return contents;
        }

        void doPaginationNow() {
            if (hasDonePagination)
                return;
            hasDonePagination = true;
            pagination.setItems(paginationItems.toArray(new ClickableItem[0]));
            int emptySlots = 0;
            for (ClickableItem[] arr : contents.all()) {
                for (ClickableItem item : arr) {
                    if (item == null)
                        emptySlots++;
                }
            }
            pagination.setItemsPerPage(emptySlots);
            pagination.addToIterator(slotIterator);
        }
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        refreshItems(player, contents, true);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        populateElements(dynamicElements, player, contents, null);
        if (updateInterval != 0) {
            Integer ticksSinceUpdate = contents.property("ticksSinceUpdate", 0);
            if (++ticksSinceUpdate == updateInterval) {
                refreshItems(player, contents, false);
                ticksSinceUpdate = 0;
            }
            contents.setProperty("ticksSinceUpdate", ticksSinceUpdate);
        }
    }

    public void close(InventoryCloseEvent e) {
        if (e == null) {
            return;
        }
        Player p = (Player) e.getPlayer();
        Optional<SmartInventory> of = WorstShop.get().inventories.getInventory(p);
        if (!of.isPresent())
            return;

        Optional<InventoryContents> contents = WorstShop.get().inventories.getContents(p);

        if (!contents.isPresent() || contents.get().property("hasClosed", false)
                || contents.get().property("noParent", false))
            return;
        contents.get().setProperty("hasClosed", true); // don't react when invoked by inventory opening

        if (contents.get().property("skipOnce", false)) {
            // skip once
            contents.get().setProperty("skipOnce", false);
            return;
        }

        // find parent
        SmartInventory openNextTick = of.get().getParent()
                .orElseGet(()->
                        Optional.ofNullable(ShopManager.SHOPS.get(parentShop))
                                .map(shop -> shop.getInventory(p, true))
                                .orElse(null)
                );

        if (openNextTick != null) {
            Bukkit.getScheduler().runTask(WorstShop.get(), () -> openNextTick.open(p));
        }
    }
}
