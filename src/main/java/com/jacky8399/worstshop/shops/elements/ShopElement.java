package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public abstract class ShopElement implements Cloneable, ParseContext.NamedContext {
    public interface SlotFiller {
        void fill(Player player, InventoryContents contents, Shop.PaginationHelper pagination);
    }
    public enum FillType {
        ALL, BORDER_1, NONE, REMAINING
    }

    // for debugging
    public String id;

    @Override
    public String getHierarchyName() {
        return getClass().getSimpleName() + "[" + (id != null ? id : "?") + "]";
    }

    // populateItems properties
    public List<SlotPos> itemPositions = null;
    public FillType fill = FillType.NONE;
    public Shop owner = null;

    public static ShopElement fromYaml(Map<String, Object> yaml) {
        boolean dynamic = (boolean) yaml.getOrDefault("dynamic", false);

        ShopElement element = dynamic ? DynamicShopElement.fromYaml(yaml) : StaticShopElement.fromYaml(yaml);

        if (element == null) {
            return null;
        }

        if (yaml.containsKey("fill") && yaml.get("fill") instanceof Boolean) {
            element.fill = (boolean)yaml.get("fill") ? FillType.ALL : FillType.NONE;
        } else if (yaml.containsKey("fill") && yaml.get("fill") instanceof String) {
            element.fill = ConfigHelper.parseEnum((String)yaml.get("fill"), FillType.class);
        }
        if (element.fill == FillType.NONE && yaml.containsKey("pos")) { // parse only if not fill
            element.itemPositions = parsePos((String) yaml.get("pos"));
        }

        return element;
    }

    public void onClick(InventoryClickEvent e) {

    }

    public ItemStack createStack(Player player) {
        return null;
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

    public void populateItems(Player player, InventoryContents contents, Shop.PaginationHelper pagination) {
        ItemStack stack = createStack(player);
        if (ItemUtils.isEmpty(stack))
            return;
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
        switch (fill) {
            case ALL:
                contents.fill(item);
                break;
            case BORDER_1:
                contents.fillBorders(item);
                break;
            case REMAINING:
                pagination.forEachRemaining((row, col)->item);
                break;
            case NONE:
                if (itemPositions != null) {
                    for (SlotPos pos : itemPositions) {
                        contents.set(pos, item);
                    }
                } else {
                    // pagination
                    pagination.add(item);
                }
                break;
        }
    }

    public ShopElement clone() {
        try {
            return (ShopElement) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
