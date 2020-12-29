package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Optional;

public class InventoryCloseListener extends InventoryListener<InventoryCloseEvent> {
    /**
     * Skips opening parent inventory once.
     */
    public static final String PROPERTY_SKIP_ONCE = "skipOnce";
    /**
     * Skips opening parent inventory while this property is true.
     */
    public static final String PROPERTY_NO_PARENT = "noParent";
    private static final String PROPERTY_HAS_CLOSED = "hasClosed";

    public InventoryCloseListener() {
        super(InventoryCloseEvent.class, e -> {
            if (e == null) {
                return;
            }
            Player p = (Player) e.getPlayer();
            Optional<SmartInventory> of = WorstShop.get().inventories.getInventory(p);
            if (!of.isPresent())
                return;

            InventoryContents contents = WorstShop.get().inventories.getContents(p).orElseThrow(()->new IllegalStateException(p.getName() + " is not in shop inventory?"));

            Boolean skipOnce = contents.<Boolean>property(PROPERTY_SKIP_ONCE);
            if (skipOnce != null && skipOnce) {
                contents.setProperty(PROPERTY_SKIP_ONCE, false);
                return;
            }
            if (contents.property(PROPERTY_HAS_CLOSED, false) || contents.property(PROPERTY_NO_PARENT, false))
                return;
            contents.setProperty(PROPERTY_HAS_CLOSED, true);

            Optional<SmartInventory> parent = of.get().getParent();

            // return to parent
            Bukkit.getScheduler().runTask(WorstShop.get(), ()->
                parent.ifPresent(smartInventory -> smartInventory.open(p))
            );
        });
    }

    private static final InventoryManager inv = WorstShop.get().inventories;
    public static void closeWithoutParent(Player player) {
        inv.getContents(player).ifPresent(contents -> {
            contents.setProperty(PROPERTY_SKIP_ONCE, true);
            contents.inventory().close(player);
        });
    }

    public static Runnable closeTemporarilyWithoutParent(Player player) {
        Optional<InventoryContents> optional = inv.getContents(player);
        if (optional.isPresent()) {
            InventoryContents contents = optional.get();
            contents.setProperty(PROPERTY_SKIP_ONCE, true);
            SmartInventory inv = contents.inventory();
            int page = contents.pagination().getPage();
            inv.close(player);
            return () -> inv.open(player, page);
        }
        return () -> {};
    }

    public static void openSafely(Player player, SmartInventory toOpen) {
        openSafely(player, toOpen, 0);
    }

    public static void openSafely(Player player, SmartInventory toOpen, int page) {
        inv.getContents(player).ifPresent(contents -> {
            contents.setProperty(PROPERTY_SKIP_ONCE, true);
            toOpen.open(player, page);
        });
    }
}