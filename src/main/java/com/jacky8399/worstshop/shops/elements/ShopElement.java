package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.shops.ElementPopulationContext;
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
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public abstract class ShopElement implements Cloneable, ParseContext.NamedContext {
    public interface SlotFiller {
        void fill(ShopElement element, ClickableItem item, InventoryContents contents, ElementPopulationContext pagination);
    }
    public enum FillType {
        ALL((element, item, contents, pagination) -> contents.fill(item)),
        BORDER_1((element, item, contents, pagination) -> contents.fillBorders(item)),
        NONE((element, item, contents, pagination) -> {
            if (element.itemPositions != null) {
                for (SlotPos pos : element.itemPositions) {
                    contents.set(pos, item);
                }
            } else {
                pagination.add(item);
            }
        }),
        REMAINING((element, item, contents, pagination) -> {
            pagination.forEachRemaining((integer, integer2) -> item);
        });

        SlotFiller filler;
        FillType(SlotFiller filler) {
            this.filler = filler;
        }

        public SlotFiller getFiller() {
            return filler;
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
    public FillType fill = FillType.NONE;

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
        if (config.find("dynamic", Boolean.class).orElse(false)) {
            element = DynamicShopElement.fromYaml(config);
        } else if (config.has("if")) {
            element = ConditionalShopElement.fromYaml(config);
        } else {
            element = StaticShopElement.fromYaml(config);
        }

        if (element == null) {
            return null;
        }

        Optional<Object> fillOptional = config.find("fill", Boolean.class, FillType.class);
        if (fillOptional.isPresent()) {
            Object fill = fillOptional.get();
            if (fill instanceof Boolean) {
                element.fill = (Boolean) fill ? FillType.ALL : FillType.NONE;
            } else {
                element.fill = (FillType) fill;
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

    public Map<String, Object> toMap(Map<String, Object> map) {
        if (id != null && !id.startsWith("index="))
            map.put("id", id);
        if (fill != FillType.NONE)
            map.put("fill", fill.name());
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

    public void populateItems(Player player, InventoryContents contents, ElementPopulationContext pagination) {
        ItemStack stack;
        try {
            stack = createStack(player);
            if (ItemUtils.isEmpty(stack))
                return;
        } catch (Exception ex) {
            // something has gone horribly wrong
            Shop owningShop = (Shop) contents.inventory().getProvider();
            RuntimeException wrapped = new RuntimeException("An error occurred while populating item for " + player.getName() + " (" + id + "@" + owningShop.id + ")", ex);
            stack = ItemUtils.getErrorItem(wrapped);
        }
        ClickableItem item = ClickableItem.of(stack, e -> {
            try {
                onClick(e);
            } catch (Exception ex) {
                Shop owningShop = (Shop) contents.inventory().getProvider();
                RuntimeException wrapped = new RuntimeException("An error occurred while processing item click for " + e.getWhoClicked().getName() + " (" + id + "@" + owningShop.id + ")", ex);
                ItemStack err = ItemUtils.getErrorItem(wrapped);
                e.setCurrentItem(err);
            }
        });
        fill.getFiller().fill(this, item, contents, pagination);
//        switch (fill) {
//            case ALL:
//                contents.fill(item);
//                break;
//            case BORDER_1:
//                contents.fillBorders(item);
//                break;
//            case REMAINING:
//                pagination.forEachRemaining((row, col)->item);
//                break;
//            case NONE:
//                if (itemPositions != null) {
//                    for (SlotPos pos : itemPositions) {
//                        contents.set(pos, item);
//                    }
//                } else {
//                    // pagination
//                    pagination.add(item);
//                }
//                break;
//        }
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
