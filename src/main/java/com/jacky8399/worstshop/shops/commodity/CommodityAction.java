package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommodityAction extends Commodity implements IUnaffordableCommodity {

    public List<Action> actions;
    public CommodityAction(Config yaml) {
        actions = yaml.getList("actions", Action.class);
    }

    public CommodityAction(List<? extends Action> actions) {
        this.actions = new ArrayList<>(actions);
    }

    @Override
    public boolean canAfford(Player player) {
        return false;
    }

    @Override
    public boolean canMultiply() {
        return false;
    }

    @Override
    public List<? extends Component> playerResult(@Nullable Player player, TransactionType position) {
        return List.of(I18n.translateAsComponent("worstshop.messages.shops.wants.action", actions.size()));
    }

    private InventoryClickEvent doUnspeakableThings(Player player) {
        return new InventoryClickEvent(InventoryUtils.makeInventoryView(player, player.getInventory()),
                InventoryType.SlotType.CONTAINER, 0, ClickType.UNKNOWN, InventoryAction.UNKNOWN);
    }

    @Override
    public double grantOrRefund(Player player) {
        InventoryClickEvent fakeEvent = doUnspeakableThings(player);
        for (Action action : actions) {
            try {
                action.onClick(fakeEvent);
            } catch (NullPointerException e) {
                WorstShop.get().logger.severe("Action " + action.getClass().getSimpleName() + " is not compatible with CommodityAction!");
            } catch (Exception e) {
                RuntimeException wrapped = new RuntimeException("Processing CommodityAction for player " + player.getName(), e);
                Exceptions.logException(wrapped);
            }
        }
        return 0;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(ItemBuilder.of(Material.BIRCH_SIGN)
                .name(I18n.translate("worstshop.messages.shops.wants.action", actions.size()))
                .build());
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        super.toMap(map);
        map.put("preset", "action");
        map.put("actions", actions.stream()
                .map(action -> action.toMap(new LinkedHashMap<>()))
                .collect(Collectors.toList()));
        return map;
    }
}
