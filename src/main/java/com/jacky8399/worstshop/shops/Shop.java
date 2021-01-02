package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.*;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.InventoryCloseListener;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jacky8399.worstshop.I18n.translate;

@Adaptor(Shop.Adaptor.class)
@Editable("shop")
public class Shop implements InventoryProvider, ParseContext.NamedContext {
    public static final String SHOP_ID_PREFIX = "worstshop:shop/";

    public List<ShopElement> staticElements = Lists.newArrayList();
    public List<ShopElement> dynamicElements = Lists.newArrayList();

    public Shop() { }

    // basic properties
    @Property
    public int rows;
    @Property
    public InventoryType type;
    // not property - specially handled
    public String id;
    @Property
    public String title;
    @Property
    public int updateInterval;

    public Condition condition = ConditionConstant.TRUE;

    // parents
    @Property
    public String parentShop = null;
    public boolean autoSetParentShop = false;

    // aliases
    public static final Pattern ALIAS_PATTERN = Pattern.compile("^\\w+$", Pattern.UNICODE_CHARACTER_CLASS);
    public List<String> aliases;
    @Property
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

            Config config = new Config(yaml);

            inst.rows = config.find("rows", Integer.class).orElse(6);
            inst.type = config.find("type", InventoryType.class).orElse(InventoryType.CHEST);

            inst.title = ChatColor.translateAlternateColorCodes('&', config.get("title", String.class));
            inst.updateInterval = config.find("update-interval", Integer.class).orElse(0);

            inst.condition = config.find("condition", Config.class).map(Condition::fromMap).orElse(null);

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
                inst.aliases = Arrays.stream(aliasString.split(","))
                        .filter(alias -> {
                            if (ALIAS_PATTERN.matcher(alias).matches())
                                return true;
                            logger.warning("Invalid shop alias '" + alias + "'");
                            logger.warning("Stack: " + ParseContext.getHierarchy());
                            return false;
                        })
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

    public void toYaml(YamlConfiguration yaml) {
        if (rows != 6)
            yaml.set("rows", rows);
        if (type != InventoryType.CHEST)
            yaml.set("type", type.name());
        yaml.set("title", title.replace(ChatColor.COLOR_CHAR, '&'));
        if (updateInterval != 0)
            yaml.set("update-interval", updateInterval);
        if (parentShop != null)
            yaml.set("parent", parentShop);
        if (aliases != null && aliases.size() != 0)
            yaml.set("alias", String.join(",", aliases));
        if (condition != null)
            yaml.set("condition", condition.toMap(new HashMap<>()));
        // TODO write elements
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
                ShopElement fakeElement = StaticShopElement.fromStack(ItemUtils.getErrorItem(ex));
                // copy renderer
                fakeElement.fill = element.fill;
                fakeElement.itemPositions = new ArrayList<>(element.itemPositions);
                fakeElement.populateItems(player, contents, helper);
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

    public class Adaptor implements EditableAdaptor<Shop>, InventoryProvider {
        private static final String I18N_KEY = "worstshop.messages.editor.property.shop.";
        @Override
        public CompletableFuture<Shop> onInteract(Player player, Shop val, @Nullable String fieldName) {
            SmartInventory inventory = WorstShop.buildGui("worstshop:editor_shop_adaptor")
                    .parent(WorstShop.get().inventories.getInventory(player).orElse(null))
                    .provider(this).title(translate(I18N_KEY + "title", id)).size(6, 9)
                    .build();
            InventoryCloseListener.openSafely(player, inventory);
            return CompletableFuture.completedFuture(Shop.this);
        }

        @Override
        public ItemStack getRepresentation(Shop val, @Nullable String fieldName) {
            return ItemBuilder.of(Material.EMERALD).name(id).build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            // header
            contents.fillRow(0, ClickableItem.empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).build()));
            // id
            contents.set(0, 4, ClickableItem.of(
                    ItemBuilder.of(Material.NAME_TAG).name(ChatColor.YELLOW + "id: " + ChatColor.GREEN + id)
                            .lores(translate(I18N_KEY + "id")).build(),
                    e -> new DefaultAdaptors.StringAdaptor().onInteract(player, id, "id")
                            .thenAccept(newId -> {
                                ShopManager.renameShop(Shop.this, newId);
                                // reopen GUI
                                InventoryCloseListener.closeWithoutParent(player);
                                onInteract(player, Shop.this, null);
                            })
            ));
        }

        @Override
        public void update(Player player, InventoryContents contents) {

        }
    }
}
