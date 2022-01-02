package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jacky8399.worstshop.editor.Property;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionAnd;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.conditions.ConditionPermission;
import com.jacky8399.worstshop.shops.rendering.*;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ShopElement implements Cloneable, ParseContext.NamedContext {
    // for debugging
    @Nullable
    public String id;

    @Override
    public String getHierarchyName() {
        return getClass().getSimpleName() + "[" + (id != null ? id : "?") + "]";
    }

    // populateItems properties
    @Nullable
    @Property(nullable = true)
    public List<SlotPos> itemPositions = null;
    @NotNull
    @Property
    public SlotFiller filler = DefaultSlotFiller.NONE;

    // common properties
    public transient ShopReference owner = null;
    @NotNull
    @Property
    public Condition condition = ConditionConstant.TRUE;
    @NotNull
    @Property
    public List<Action> actions = new ArrayList<>();

    @Property
    public final HashMap<String, Object> variables = new HashMap<>();

    @Nullable
    public Object getVariable(String key, PlaceholderContext context) {
        return variables.get(key);
    }

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
        } else if (config.has("use")) {
            element = TemplateShopElement.fromYaml(config);
        } else {
            element = StaticShopElement.fromYaml(config);
        }

        if (element == null) {
            return null;
        }
        // check ParseContext
        if (!(ParseContext.STACK.peek() instanceof ShopElement)) {
            throw new IllegalStateException(element.getClass().getSimpleName() + " did not add itself to ParseContext!");
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
        config.find("condition", Config.class, String.class)
                .map(Condition::fromObject).ifPresent(instCondition::mergeCondition);
        element.condition = instCondition;

        // Action parsing
        element.actions = config.findList("actions", Config.class, String.class).orElseGet(ArrayList::new).stream()
                .map(obj -> obj instanceof Config ?
                        Action.fromConfig((Config) obj) :
                        Action.fromCommand(obj.toString()))
                .collect(Collectors.toList());

        // variables
        config.find("variables", Config.class)
                .map(ConfigHelper::parseVariables)
                .ifPresent(element.variables::putAll);

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

    public ItemStack createStack(ShopRenderer renderer) {
        return createStack(renderer.player);
    }

    public Consumer<InventoryClickEvent> getClickHandler(ShopRenderer renderer) {
        return this::onClick;
    }

    public List<RenderElement> getRenderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
        SlotFiller filler = getFiller(renderer);
        Collection<SlotPos> positions = filler.fill(this, renderer);
        return Collections.singletonList(new RenderElement(this, positions,
                createStack(renderer), getClickHandler(renderer), getRenderingFlags(renderer)));
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
        if (!(condition instanceof ConditionAnd and && and.isEmpty()))
            map.put("condition", condition.toMapObject());
        if (!variables.isEmpty())
            map.put("variables", Maps.transformValues(variables, ConfigHelper::stringifyVariable));
        return map;
    }

    public static List<SlotPos> parsePos(String input) {
        String[] posStrings = input.split(";");
        List<SlotPos> list = Lists.newArrayList();
        for (String posString : posStrings) {
                if (posString.contains(",")) {
                    // comma delimited x,y format
                    String[] posCoords = posString.split(",");
                    list.add(new SlotPos(Integer.parseInt(posCoords[1].trim()), Integer.parseInt(posCoords[0].trim())));
                } else {
                    // assume normal integer format (0 - 54)
                    int posNum = Integer.parseInt(posString.trim());
                    int row = posNum / 9, column = posNum % 9;
                    list.add(new SlotPos(row, column));
                }
        }
        return list;
    }

    public static final EnumSet<ShopRenderer.RenderingFlag> STATIC_FLAGS = EnumSet.noneOf(ShopRenderer.RenderingFlag.class),
            DYNAMIC_FLAGS = EnumSet.of(ShopRenderer.RenderingFlag.UPDATE_NEXT_TICK);
    public EnumSet<ShopRenderer.RenderingFlag> getRenderingFlags(ShopRenderer renderer) {
        return isDynamic() ? DYNAMIC_FLAGS : STATIC_FLAGS;
    }
    
    public SlotFiller getFiller(ShopRenderer renderer) {
        return filler;
    }

    @Override
    public String toString() {
        return id + "@" + owner.id;
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
