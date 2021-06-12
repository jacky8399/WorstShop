package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.shops.ElementContext;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionAnd;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.conditions.ConditionPermission;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class ShopElement implements Cloneable, ParseContext.NamedContext {
    public interface SlotFiller {
        void fill(ShopElement element, ClickableItem item, InventoryContents contents, ElementContext pagination);
    }

    public static class DefaultSlotFiller {
        private static final HashMap<String, DefaultSlotFiller> FILLERS = new HashMap<>();
        // very special case
        public static final SlotFiller NONE;
        public static final SlotFiller ALL;
        public final String name;
        public final String usage;
        public final Function<String[], SlotFiller> filler;
        private DefaultSlotFiller(String name, SlotFiller staticFiller) {
            this(name, name, input -> staticFiller);
        }
        private DefaultSlotFiller(String name, String usage, Function<String[], SlotFiller> filler) {
            this.name = name;
            this.usage = usage;
            this.filler = input -> new SlotFiller() {
                SlotFiller actual = filler.apply(input);

                @Override
                public void fill(ShopElement element, ClickableItem item, InventoryContents contents, ElementContext pagination) {
                    actual.fill(element, item, contents, pagination);
                }

                @Override
                public String toString() {
                    return input.length == 0 ? name : name + " " + String.join(" ", input);
                }
            };
            FILLERS.put(name, this);
        }
        static {
            ALL = new DefaultSlotFiller("all", (element, item, contents, pagination) -> contents.fill(item))
                    .filler.apply(null);
            NONE = new DefaultSlotFiller("none", (element, item, contents, pagination) -> {
                if (element.itemPositions != null) {
                    for (SlotPos pos : element.itemPositions) {
                        contents.set(pos, item);
                    }
                } else {
                    pagination.add(item);
                }
            }).filler.apply(null);
            new DefaultSlotFiller("border", "border <radius>", input -> {
                int radius = Integer.parseInt(input[0]);
                return (element, item, contents, pagination) -> {
                    for (int i = 0, rows = contents.inventory().getRows(); i < rows; i++) {
                        for (int j = 0, columns = contents.inventory().getColumns(); j < columns; j++) {
                            if (i < radius || i >= rows - radius || j < radius || j >= columns - radius)
                                contents.set(i, j, item);
                        }
                    }
                };
            });
            new DefaultSlotFiller("remaining", (element, item, contents, pagination) ->
                    pagination.forEachRemaining((integer, integer2) -> item));
            new DefaultSlotFiller("row", "row <row>", input -> {
                int row = Integer.parseInt(input[0]);
                return ((element, item, contents, pagination) -> contents.fillRow(row, item));
            });
            new DefaultSlotFiller("column", "column <row>", input -> {
                int row = Integer.parseInt(input[0]);
                return (element, item, contents, pagination) -> contents.fillColumn(row, item);
            });
            new DefaultSlotFiller("rectangle", "rectangle <row,col> <row,col> [solid]", input -> {
                SlotPos pos1 = parsePos(input[0]).get(0), pos2 = parsePos(input[1]).get(0);
                boolean solid = input.length == 3 && ("solid".equals(input[2]) || "true".equals(input[2]));
                return (element, item, contents, pagination) -> {
                    for (int i = 0, rows = contents.inventory().getRows(); i < rows; i++) {
                        for (int j = 0, columns = contents.inventory().getColumns(); j < columns; j++) {
                            if (i == pos1.getRow() || i == pos2.getRow() ||
                                    j == pos1.getColumn() || j == pos2.getColumn() ||
                                    (solid && i > pos1.getRow() && i < pos2.getRow() && j > pos1.getColumn() && j < pos2.getColumn()))
                                contents.set(i, j, item);
                        }
                    }
                };
            });
        }
        public static SlotFiller fromInput(String input) {
            input = input.toLowerCase(Locale.ROOT);
            String[] split = input.split(" ");
            DefaultSlotFiller filler = FILLERS.get(split[0]);
            if (filler == null)
                throw new IllegalArgumentException(input);
            try {
                return filler.filler.apply(Arrays.copyOfRange(split, 1, split.length));
            } catch (Exception e) {
                throw new IllegalArgumentException(filler.usage, e);
            }
        }
    }

    // for debugging
    @Nullable
    public String id;

    @Override
    public String getHierarchyName() {
        return getClass().getSimpleName() + "[" + (id != null ? id : "?") + "]";
    }

    // populateItems properties
    @Nullable
    public List<SlotPos> itemPositions = null;
    @NotNull
    public SlotFiller filler = DefaultSlotFiller.NONE;

    // common properties
    public transient ShopReference owner = null;
    @NotNull
    public Condition condition = ConditionConstant.TRUE;
    @NotNull
    public List<Action> actions = new ArrayList<>();

    public boolean isDynamic() {
        return this instanceof DynamicShopElement;
    }

    public static ShopElement fromConfig(Config config) {
        boolean dynamic = config.find("dynamic", Boolean.class).orElse(false);

        ShopElement element;
        if (dynamic) {
            element = DynamicShopElement.fromYaml(config);
        } else if (config.has("if")) {
            element = ConditionalShopElement.fromYaml(config);
        } else {
            element = StaticShopElement.fromYaml(config);
        }

        if (element == null) {
            return null;
        }

        Optional<Object> fillOptional = config.find("fill", Boolean.class, String.class);
        if (fillOptional.isPresent()) {
            Object fill = fillOptional.get();
            if (fill instanceof Boolean) {
                element.filler = (Boolean) fill ? DefaultSlotFiller.ALL : DefaultSlotFiller.NONE;
            } else {
                element.filler = DefaultSlotFiller.fromInput(fill.toString());
            }
        }

        Optional<String> posOptional = config.find("pos", String.class);
        posOptional.ifPresent(s -> element.itemPositions = parsePos(s));

        ConditionAnd instCondition = new ConditionAnd();
        config.find("view-perm", String.class).map(ConditionPermission::fromPermString).ifPresent(instCondition::mergeCondition);
        config.find("condition", Config.class).map(Condition::fromMap).ifPresent(instCondition::mergeCondition);
        element.condition = instCondition;

        // Action parsing
        element.actions = config.findList("actions", Config.class, String.class).orElseGet(ArrayList::new).stream()
                .map(obj -> obj instanceof Config ?
                        Action.fromConfig((Config) obj) :
                        Action.fromCommand(obj.toString()))
                .filter(Objects::nonNull).collect(Collectors.toList());

        // pop context here
        ParseContext.popContext();
        return element;
    }

    public void onClick(InventoryClickEvent e) {
        for (Action action : actions) {
            if (action.shouldTrigger(e)) {
                action.onClick(e);
            }
        }
    }

    public ItemStack createStack(Player player) {
        return null;
    }

    public ItemStack createStack(Player player, ElementContext context) {
        return createStack(player);
    }

    public Map<String, Object> toMap(Map<String, Object> map) {
        if (id != null && !id.startsWith("index="))
            map.put("id", id);
        if (filler != DefaultSlotFiller.NONE)
            map.put("fill", filler.toString());
        if (itemPositions != null)
            map.put("pos", itemPositions.stream()
                    .map(pos -> pos.getRow() + "," + pos.getColumn())
                    .collect(Collectors.joining(";")));

        if (this instanceof DynamicShopElement)
            map.put("dynamic", true);
        if (actions.size() != 0)
            map.put("actions", actions.stream().map(action -> action.toMap(new HashMap<>())).collect(Collectors.toList()));
        if (!(condition instanceof ConditionAnd && ((ConditionAnd) condition).isEmpty()))
            map.put("condition", condition.toMap(new HashMap<>()));
        return map;
    }

    protected static List<SlotPos> parsePos(String input) {
        String[] posStrings = input.split(";");
        List<SlotPos> list = Lists.newArrayList();
        for (String posString : posStrings) {
                if (posString.contains(",")) {
                    // comma delimited x,y format
                    String[] posCoords = posString.split(",");
                    list.add(new SlotPos(Integer.parseInt(posCoords[0].trim()), Integer.parseInt(posCoords[1].trim())));
                } else {
                    // assume normal integer format (0 - 54)
                    int posNum = Integer.parseInt(posString.trim());
                    int row = posNum / 9, column = posNum % 9;
                    list.add(new SlotPos(row, column));
                }
        }
        return list;
    }

    public void populateItems(Player player, InventoryContents contents, ElementContext pagination) {
        InventoryProvider provider = contents.inventory().getProvider();
        String owner = provider instanceof Shop ? ((Shop) provider).id : provider.toString();
        ItemStack stack;
        try {
            stack = createStack(player, pagination);
            if (ItemUtils.isEmpty(stack))
                return;

            // debug
            if (contents.property("debug", false)) {
                // modify lore
                ItemMeta meta = stack.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                if (lore.size() != 0)
                    lore.add("");
                lore.add(ChatColor.YELLOW + id + "@" + owner);
            }
        } catch (Exception ex) {
            // something has gone horribly wrong
            RuntimeException wrapped = new RuntimeException("Populating item for " + player.getName() + " (" + id + "@" + owner + ")", ex);
            filler.fill(this, ItemUtils.getClickableErrorItem(wrapped), contents, pagination);
            return;
        }
        ClickableItem item = ClickableItem.of(stack, e -> {
            try {
                onClick(e);
            } catch (Exception ex) {
                RuntimeException wrapped = new RuntimeException("Processing item click for " + e.getWhoClicked().getName() + " (" + id + "@" + owner + ")", ex);
                ItemStack err = ItemUtils.getErrorItem(wrapped);
                e.setCurrentItem(err);
            }
        });
        filler.fill(this, item, contents, pagination);
    }

    public ShopElement clone() {
        try {
            ShopElement clone = (ShopElement) super.clone();
            clone.itemPositions = itemPositions != null ? new ArrayList<>(itemPositions) : null;
            clone.actions = actions.stream().map(Action::clone).collect(Collectors.toList());
            return clone;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
