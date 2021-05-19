package com.jacky8399.worstshop.shops;

import com.google.common.collect.Streams;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.*;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
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
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jacky8399.worstshop.I18n.translate;

@Adaptor(Shop.Adaptor.class)
@Editable("shop")
public class Shop implements InventoryProvider, ParseContext.NamedContext {
    public static final String SHOP_ID_PREFIX = "worstshop:shop/";

    @Property
    public List<ShopElement> staticElements = new ArrayList<>();
    @Property
    public List<ShopElement> dynamicElements = new ArrayList<>();

    public Shop() { }

    // basic properties
    @Property
    public int rows;
    @Property
    @Representation(Material.CHEST)
    public InventoryType type;
    // not property - specially handled
    public String id;
    @Property
    public String title;
    @Property
    public int updateInterval;
    @Property
    public Condition condition = ConditionConstant.TRUE;

    // parents
    @Property
    public ShopReference parentShop = ShopReference.EMPTY;
    @Property
    public boolean autoSetParentShop = false;

    // aliases
    public static final Pattern ALIAS_PATTERN = Pattern.compile("^\\w+$", Pattern.UNICODE_CHARACTER_CLASS);
    @Property
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

    @SuppressWarnings({"ConstantConditions", "null"})
    public static Shop fromYaml(String shopName, YamlConfiguration yaml) {
        Shop inst = new Shop();
        ShopManager.currentShop = inst;
        inst.id = shopName;

        Logger logger = WorstShop.get().logger;

        try {
            ParseContext.pushContext(inst);

            Config config = new Config(yaml.getValues(false), null, "ROOT");

            inst.rows = config.find("rows", Integer.class).orElse(6);
            inst.type = config.find("type", InventoryType.class).orElse(InventoryType.CHEST);

            inst.title = ConfigHelper.translateString(config.get("title", String.class));
            inst.updateInterval = config.find("update-interval", Integer.class).orElse(0);

            inst.condition = config.find("condition", Config.class).map(Condition::fromMap).orElse(ConditionConstant.TRUE);

            inst.parentShop = config.find("parent", String.class).map(ShopReference::of).orElse(ShopReference.EMPTY);
            if ("auto".equals(inst.parentShop.id)) {
                inst.autoSetParentShop = true;
            }

            config.getList("items", Config.class).forEach(itemConfig -> {
                ShopElement element = ShopElement.fromConfig(itemConfig);
                if (element != null)
                    (element.isDynamic() ? inst.dynamicElements : inst.staticElements).add(element);
            });

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
        if (parentShop != ShopReference.EMPTY)
            yaml.set("parent", parentShop.id);
        if (aliases != null && aliases.size() != 0)
            yaml.set("alias", String.join(",", aliases));
        if (condition != ConditionConstant.TRUE)
            yaml.set("condition", condition.toMap(new HashMap<>()));
        //noinspection UnstableApiUsage
        yaml.set("items", Streams.concat(staticElements.stream(), dynamicElements.stream())
                .map(element -> element.toMap(new HashMap<>()))
                .collect(Collectors.toList())
        );
    }

    public void populateElements(List<ShopElement> elementList,
                                 Player player, InventoryContents contents, ElementPopulationContext helper) {
        ListIterator<ShopElement> iterator = elementList.listIterator();
        while (iterator.hasNext()) {
            int index = iterator.nextIndex();
            ShopElement element = iterator.next();
            if (element == null)
                continue;
            try {
                element.populateItems(player, contents, helper);
            } catch (Exception ex) {
                RuntimeException wrapped = new RuntimeException("Error while populating element [" + index + "] in " + id, ex);
                ShopElement fakeElement = StaticShopElement.fromStack(ItemUtils.getErrorItem(wrapped));
                // copy renderer
                fakeElement.fill = element.fill;
                fakeElement.itemPositions = element.itemPositions != null ? new ArrayList<>(element.itemPositions) : null;
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
        ElementPopulationContext helper = new ElementPopulationContext(contents);
        populateElements(staticElements, player, contents, helper);
        if (updateDynamic)
            populateElements(dynamicElements, player, contents, helper);
        // ensure pagination
        helper.doPaginationNow();
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
                        parentShop.find()
                                .map(shop -> shop.getInventory(p, true))
                                .orElse(null)
                );

        if (openNextTick != null) {
            Bukkit.getScheduler().runTask(WorstShop.get(), () -> openNextTick.open(p));
        }
    }

    public class Adaptor extends DefaultAdaptors.EditableObjectAdaptor<Shop> {
        private static final String I18N_KEY = "worstshop.messages.editor.property.shop.";
        public Adaptor() {
            super(Shop.class);
        }

        @Override
        protected String getTitle() {
            return translate(I18N_KEY + "title", id) + " " + ChatColor.DARK_RED + ChatColor.BOLD + "BETA";
        }

        @Override
        public ItemStack getRepresentation(Shop val, @Nullable String parentName, @Nullable String fieldName) {
            return ItemBuilder.of(Material.EMERALD).name(id).build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
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
        public void update(Player player, InventoryContents contents, boolean isRefreshingProperties) {}
    }
}
