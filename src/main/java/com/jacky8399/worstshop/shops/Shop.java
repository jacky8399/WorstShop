package com.jacky8399.worstshop.shops;

import com.google.common.collect.Streams;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.Adaptor;
import com.jacky8399.worstshop.editor.Editable;
import com.jacky8399.worstshop.editor.Property;
import com.jacky8399.worstshop.editor.Representation;
import com.jacky8399.worstshop.editor.adaptors.EditableObjectAdaptor;
import com.jacky8399.worstshop.editor.adaptors.StringAdaptor;
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
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
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

    @Property
    public ShopReference extendsFrom = ShopReference.EMPTY;

    // aliases
    public static final Pattern ALIAS_PATTERN = Pattern.compile("^\\w+$", Pattern.UNICODE_CHARACTER_CLASS);
    @Property
    public List<String> aliases;
    @Property
    public boolean aliasesIgnorePermission;

    // variables
    public final HashMap<String, Object> variables = new HashMap<>();

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

    public Object getVariable(String key) {
        Object ret = variables.get(key);
        if (ret == null && extendsFrom.find().isPresent() && !extendsFrom.refersTo(this)) {
            ret = extendsFrom.get().getVariable(key);
        }
        return ret;
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
        if (title == null) {
            if (extendsFrom != ShopReference.EMPTY)
                builder.title(I18n.doPlaceholders(player, extendsFrom.get().title, this, null));
            else
                builder.title("null");
        } else {
            builder.title(I18n.doPlaceholders(player, title, this, null));
        }
        return builder.build();
    }

    public static Shop fromYaml(String shopName, File file) {
        Shop inst = new Shop();
        ShopManager.currentShop = inst;
        inst.id = shopName;

        Logger logger = WorstShop.get().logger;

        try {
            ParseContext.pushContext(inst);

            Config config = new Config(new Yaml().load(new FileReader(file)), null, "ROOT");

            config.find("extends", String.class).ifPresent(templateId -> {
                // self-reference check
                if (templateId.equals(shopName))
                    throw new ConfigException("Self-reference not allowed", config, "extends");
                inst.extendsFrom = ShopReference.of(templateId);
            });

            inst.rows = config.find("rows", Integer.class).orElse(6);
            inst.type = config.find("type", InventoryType.class).orElse(InventoryType.CHEST);

            if (!config.has("title") && inst.extendsFrom != ShopReference.EMPTY) {
                inst.title = null; // allow templates to do the hard work
            } else {
                inst.title = ConfigHelper.translateString(config.get("title", String.class));
            }
            inst.updateInterval = config.find("update-interval", Integer.class).orElse(0);

            inst.condition = config.find("condition", Config.class).map(Condition::fromMap).orElse(ConditionConstant.TRUE);

            inst.parentShop = config.find("parent", String.class).map(ShopReference::of).orElse(ShopReference.EMPTY);
            if ("auto".equals(inst.parentShop.id)) {
                inst.autoSetParentShop = true;
            }
            WorstShop.get().logger.info(shopName + " extends from " + inst.extendsFrom.id);

            config.getList("items", Config.class).forEach(itemConfig -> {
                ShopElement element = ShopElement.fromConfig(itemConfig);
                if (element != null)
                    (element.isDynamic() ? inst.dynamicElements : inst.staticElements).add(element);
            });

            // commands
            config.find("alias", String.class).ifPresent(aliasString -> {
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
                inst.aliasesIgnorePermission = config.find("alias-ignore-permission", Boolean.class).orElse(false);
            });

            // variables
            config.find("variables", Config.class).ifPresent(variables -> {
                variables.getKeys().forEach(key -> {
                    Optional<Config> complexConfig = variables.tryFind(key, Config.class);
                    if (complexConfig.isPresent()) {
                        WorstShop.get().logger.warning("Unsupported variable type: " + complexConfig.get().get("type", String.class));
                    } else {
                        inst.variables.put(key, variables.get(key, Object.class));
                    }
                });
            });

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

    boolean circularReferenceChecked = false;
    public void checkCircularReference() {
        ShopReference template = extendsFrom;
        Set<String> traversed = new LinkedHashSet<>();
        int depth = 0;
        while (template != ShopReference.EMPTY) {
            boolean shouldExit = false;
            if (!traversed.add(template.id)) {
                WorstShop.get().logger.severe("Circular reference is not allowed!!! (at shop " + id + "). The circular reference has been removed.");
                shouldExit = true;
            }
            if (template.id.equals(id)) {
                WorstShop.get().logger.severe("Self-reference is not allowed!!! (at shop " + id + "). The self-reference has been removed.");
                shouldExit = true;
            }
            if (++depth >= 100) {
                WorstShop.get().logger.severe("Hierarchy too deep!!! (at shop " + id + "). The reference has been removed.");
                shouldExit = true;
            }

            if (shouldExit) {
                WorstShop.get().logger.severe("Hierarchy: " + String.join(" <- ", traversed));
                extendsFrom = ShopReference.EMPTY;
                break;
            }

            Optional<Shop> shop = template.find();
            if (shop.isPresent()) {
                template = shop.get().extendsFrom;
            } else {
                break;
            }
        }
        circularReferenceChecked = true;
    }

    public void populateElements(boolean dynamic,
                                 Player player, InventoryContents contents, ElementContext helper) {
        if (!circularReferenceChecked)
            checkCircularReference();

        if (extendsFrom.find().isPresent()) {
            extendsFrom.get().populateElements(dynamic, player, contents, helper);
        }

        List<ShopElement> elementList = dynamic ? dynamicElements : staticElements;
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
                fakeElement.filler = element.filler;
                fakeElement.itemPositions = element.itemPositions != null ? new ArrayList<>(element.itemPositions) : null;
                fakeElement.populateItems(player, contents, helper);
            }
        }
    }

    public void refreshItems(Player player, InventoryContents contents, boolean updateDynamic, boolean isOutline) {
        // clear old items
        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0,0).allowOverride(true);
        while (!it.ended()) {
            it.next().set(null);
        }
        ElementContext helper = new ElementContext(contents,
                isOutline ? ElementContext.Stage.SKELETON : ElementContext.Stage.STATIC);
        populateElements(false, player, contents, helper);
        helper.doPaginationNow();

        if (updateDynamic)
            populateElements(true, player, contents, new ElementContext(contents,
                    isOutline ? ElementContext.Stage.SKELETON : ElementContext.Stage.DYNAMIC));
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // TODO find a better solution to fix page turning
        refreshItems(player, contents, false, true);
        refreshItems(player, contents, true, false);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
//        if (extendsFrom.find().isPresent() && !extendsFrom.refersTo(this)) {
//            extendsFrom.get().update(player, contents);
//        }
        populateElements(true, player, contents, null);
        if (updateInterval != 0) {
            Integer ticksSinceUpdate = contents.property("ticksSinceUpdate", 0);
            if (++ticksSinceUpdate == updateInterval) {
                refreshItems(player, contents, true, false);
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

    public class Adaptor extends EditableObjectAdaptor<Shop> {
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
                    e -> new StringAdaptor().onInteract(player, id, "id")
                            .thenAccept(newId -> {
                                ShopManager.renameShop(Shop.this, newId);
                                // reopen GUI
                                InventoryUtils.closeWithoutParent(player);
                                onInteract(player, Shop.this, null);
                            })
            ));
        }

        @Override
        public void update(Player player, InventoryContents contents, boolean isRefreshingProperties) {}
    }
}
