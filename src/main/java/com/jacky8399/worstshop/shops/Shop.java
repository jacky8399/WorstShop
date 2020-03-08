package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.apache.logging.log4j.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Shop implements InventoryProvider {

    public static String SHOP_ID_PREFIX = "worstshop:shop/";

    public List<ShopElement> staticElements = Lists.newArrayList();
    public List<ShopElement> dynamicElements = Lists.newArrayList();

    public Shop() { }

    // basic properties
    int rows;
    InventoryType type;
    String id;
    String title;
    int updateInterval;

    // parents
    String parentShop = null;
    boolean autoSetParentShop = false;

    // aliases
    List<String> aliases;
    boolean aliasesIgnorePermission;

    public static SmartInventory.Builder getDefaultBuilder() {
        return SmartInventory.builder().manager(WorstShop.get().inventories);
    }

    public SmartInventory getInventory(Player player) {
        return getInventory(player, false);
    }

    public SmartInventory getInventory(Player player, boolean skipAutoParent) {
        SmartInventory.Builder builder = getDefaultBuilder();
        builder.id(SHOP_ID_PREFIX+id).provider(this)
                .listener(new InventoryListener<>(InventoryCloseEvent.class, this::close))
                .type(type);
        if (type == InventoryType.CHEST) {
            builder.size(rows, 9);
        }
        if (autoSetParentShop && !skipAutoParent) {
            Optional<SmartInventory> inventory = WorstShop.get().inventories.getInventory(player);
            inventory.ifPresent(inv->{
                if (inv.getId().startsWith(SHOP_ID_PREFIX)) { // check is shop
                    parentShop = ((Shop) inv.getProvider()).id;
                }
            });
        }
        return builder.title(title).build();
    }

    public static Shop fromYaml(String shopName, YamlConfiguration yaml) {
        Shop inst = new Shop();
        inst.id = shopName;

        try {

            inst.rows = yaml.getInt("rows", 6);
            inst.type = InventoryType.valueOf(yaml.getString("type", "chest").toUpperCase());
            if (!yaml.isString("title"))
                throw new RuntimeException("No title set!");
            inst.title = ChatColor.translateAlternateColorCodes('&', yaml.getString("title"));
            inst.updateInterval = yaml.getInt("update-interval", 0);

            inst.parentShop = yaml.getString("parent");
            if (inst.parentShop != null && inst.parentShop.equals("auto")) {
                inst.autoSetParentShop = true;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) yaml.getList("items", Collections.emptyList());
            for (Map<String, Object> itemSection : items) {
                ShopElement elem = ShopElement.fromYaml(itemSection);

                if (elem instanceof DynamicShopElement)
                    inst.dynamicElements.add(elem);
                else
                    inst.staticElements.add(elem);
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

        } catch (Exception ex) {
            WorstShop.get().logger.severe("Error while parsing shop " + shopName + ", skipping.");
            ex.printStackTrace();
        }
        return inst;
    }

    public void populateElements(List<ShopElement> elementList,
                                 Player player, InventoryContents contents, PaginationHelper helper) {
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
                ex.printStackTrace();
            }
        }
    }

    public void refreshItems(Player player, InventoryContents contents, boolean updateDynamic) {
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

        public void forEachRemaining(Supplier<ClickableItem> supplier) {
            doPaginationNow();
            ClickableItem next;
            while (!slotIterator.ended() && (next = supplier.get()) != null) {
                slotIterator.next().set(next);
            }
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

        Optional<SmartInventory> parent = of.get().getParent();

        SmartInventory openNextTick = null;
        // return to parent
        if (parent.isPresent()) {
            //e.getPlayer().closeInventory();
            openNextTick = parent.get();
        } else if (parentShop != null) {
            Shop shop = ShopManager.SHOPS.get(parentShop);
            openNextTick = shop.getInventory(p, true);
        }

        if (openNextTick != null) {
            final SmartInventory finalOpenNextTick = openNextTick; // closure;
            Bukkit.getScheduler().runTask(WorstShop.get(), () -> finalOpenNextTick.open(p));
        }
    }
}
