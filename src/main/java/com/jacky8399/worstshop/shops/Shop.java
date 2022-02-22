package com.jacky8399.worstshop.shops;

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
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jacky8399.worstshop.I18n.translate;

@Adaptor(Shop.Adaptor.class)
@Editable("shop")
public class Shop implements ParseContext.NamedContext {
    public static final String SHOP_ID_PREFIX = "worstshop:shop/";

    @Property
    public List<ShopElement> elements = new ArrayList<>();

    public Shop() { }

    // basic properties
    @Property
    public int rows;
    @Property
    @Representation(Material.CHEST)
    public InventoryType type;
    // not property - handled separately
    public transient String id;
    @Property
    public String title;
    @Property
    public int updateInterval;
    @Property
    public Condition condition = ConditionConstant.TRUE;

    // parents
    @Property
    public ShopReference parentShop = ShopReference.empty();
    @Property
    public boolean autoSetParentShop = false;

    @Property
    public ShopReference extendsFrom = ShopReference.empty();

    // aliases
    public static final Pattern ALIAS_PATTERN = Pattern.compile("^\\w+$", Pattern.UNICODE_CHARACTER_CLASS);
    @Property
    public List<String> aliases;
    @Property
    public boolean aliasesIgnorePermission;

    // variables
    @Property
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

    @Nullable
    public Object getVariable(String key) {
        Object ret = variables.get(key);
        if (ret != null)
            return ret;
        if (!circularReferenceChecked)
            checkCircularReference();
        return extendsFrom.find().map(shop -> shop.getVariable(key)).orElse(null);
    }

    public SmartInventory getInventory(Player player) {
        return getInventory(player, false);
    }

    public SmartInventory getInventory(Player player, boolean skipAutoParent) {
        SmartInventory.Builder builder = getDefaultBuilder();
        builder.id(SHOP_ID_PREFIX + id)
//                .provider(this)
                .provider(new ShopRenderer(this, player))
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
        String actualTitle;
        if (title == null && extendsFrom != ShopReference.empty()) {
            actualTitle = extendsFrom.get().title;
        } else {
            actualTitle = title;
        }
        builder.title(Placeholders.setPlaceholders(actualTitle, new PlaceholderContext(player, this, null, null, null)));
        return builder.build();
    }

    public static Shop fromYaml(String shopName, File file) {
        Shop inst = new Shop();
        ShopManager.currentShop = inst;
        inst.id = shopName;

        Logger logger = WorstShop.get().logger;

        try {
            ParseContext.pushContext(inst);

            // of course you have to specify the charset
            Config config = new Config(new Yaml().load(new FileReader(file, StandardCharsets.UTF_8)),
                    null, "[" + shopName + ".yml]");

            config.find("extends", String.class).ifPresent(templateId -> {
                // self-reference check
                if (templateId.equals(shopName))
                    throw new ConfigException("Self-reference not allowed", config, "extends");
                inst.extendsFrom = ShopReference.of(templateId);
            });

            inst.rows = config.find("rows", Integer.class).orElse(6);
            inst.type = config.find("type", InventoryType.class).orElse(InventoryType.CHEST);

            if (!config.has("title") && inst.extendsFrom != ShopReference.empty()) {
                inst.title = null; // allow templates to do the hard work
            } else {
                inst.title = ConfigHelper.translateString(config.get("title", String.class));
            }
            inst.updateInterval = config.find("update-interval", Integer.class).orElse(0);

            inst.condition = config.find("condition", Condition.class).orElse(ConditionConstant.TRUE);

            inst.parentShop = config.find("parent", String.class).map(ShopReference::of).orElse(ShopReference.empty());
            if ("auto".equals(inst.parentShop.id)) {
                inst.autoSetParentShop = true;
            }

            config.getList("items", Config.class).forEach(itemConfig -> {
                ShopElement element = ShopElement.fromConfig(itemConfig);
                if (element != null)
                    inst.elements.add(element);
            });

            // commands
            config.find("alias", String.class).ifPresent(aliasString -> {
                inst.aliases = Arrays.stream(aliasString.split(","))
                        .filter(alias -> {
                            if (ALIAS_PATTERN.matcher(alias).matches())
                                return true;
                            logger.warning("Invalid shop alias '" + alias + "'");
                            logger.warning("At " + config.getPath("alias"));
                            return false;
                        })
                        .collect(Collectors.toList());

                if (inst.aliases.size() < 1)
                    inst.aliases = null;
                inst.aliasesIgnorePermission = config.find("alias-ignore-permission", Boolean.class).orElse(false);
            });

            // variables
            config.find("variables", Config.class)
                    .map(ConfigHelper::parseVariables)
                    .ifPresent(inst.variables::putAll);

            // should be self
            if (ParseContext.popContext() != inst) {
                throw new IllegalStateException("Stack is broken?? " + ParseContext.getHierarchy());
            }
        } catch (IOException e) {
            logger.severe("Failed to load shop " + shopName);
            e.printStackTrace();
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
        if (parentShop != ShopReference.empty())
            yaml.set("parent", parentShop.id);
        if (aliases != null && aliases.size() != 0)
            yaml.set("alias", String.join(",", aliases));
        if (condition != ConditionConstant.TRUE)
            yaml.set("condition", condition.toMapObject());
        yaml.set("items", elements.stream()
                .map(element -> element.toMap(new HashMap<>()))
                .collect(Collectors.toList())
        );
    }

    public boolean circularReferenceChecked = false;
    public void checkCircularReference() {
        ShopReference template = extendsFrom;
        Set<String> traversed = new LinkedHashSet<>();
        int depth = 0;
        while (template != ShopReference.empty()) {
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
                extendsFrom = ShopReference.empty();
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

    public void close(InventoryCloseEvent e) {
        if (e == null) {
            return;
        }
        Player p = (Player) e.getPlayer();
        SmartInventory of = InventoryUtils.getInventory(p);
        if (of == null)
            return;

        InventoryContents contents = InventoryUtils.getContents(p);
        if (contents == null)
            return;

        boolean skipOnce = InventoryUtils.getSkipOnce(contents);
        if (skipOnce) {
            contents.setProperty(InventoryUtils.PROPERTY_SKIP_ONCE, false);
            return;
        }
        if (InventoryUtils.getHasClosed(contents) || InventoryUtils.getNoParent(contents))
            return;
        contents.setProperty(InventoryUtils.PROPERTY_HAS_CLOSED, true);

        // find parent
        SmartInventory openNextTick = of.getParent()
                .orElseGet(()->
                        parentShop.find()
                                .map(shop -> shop.getInventory(p, true))
                                .orElse(null)
                );

        if (openNextTick != null) {
            Bukkit.getScheduler().runTask(WorstShop.get(), () -> openNextTick.open(p));
        }
    }

    @Override
    public String toString() {
        return "Shop{id=" + id + "}";
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
