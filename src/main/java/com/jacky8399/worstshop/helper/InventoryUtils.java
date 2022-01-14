package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class InventoryUtils extends InventoryListener<InventoryCloseEvent> {
    /**
     * Skips opening parent inventory once.
     */
    public static final String PROPERTY_SKIP_ONCE = "skipOnce";
    /**
     * Skips opening parent inventory while this property is true.
     */
    public static final String PROPERTY_NO_PARENT = "noParent";
    public static final String PROPERTY_HAS_CLOSED = "hasClosed";

    public InventoryUtils() {
        super(InventoryCloseEvent.class, e -> {
            if (e == null) {
                return;
            }
            Player p = (Player) e.getPlayer();
            SmartInventory of = getInventory(p);
            if (of == null)
                return;

            InventoryContents contents = getContents(p);
            if (contents == null)
                return;

            boolean skipOnce = getSkipOnce(contents);
            if (skipOnce) {
                contents.setProperty(PROPERTY_SKIP_ONCE, false);
                return;
            }
            if (getHasClosed(contents) || getNoParent(contents))
                return;
            contents.setProperty(PROPERTY_HAS_CLOSED, true);

            Optional<SmartInventory> parent = of.getParent();

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
        inv.getContents(player).ifPresent(contents -> contents.setProperty(PROPERTY_SKIP_ONCE, true));
        toOpen.open(player, page);
    }

    @Nullable
    public static InventoryContents getContents(Player player) {
        return inv.getContents(player).orElse(null);
    }

    @Nullable
    public static SmartInventory getInventory(Player player) {
        return inv.getInventory(player).orElse(null);
    }

    public static boolean getHasClosed(InventoryContents contents) {
        Boolean property = contents.property(PROPERTY_HAS_CLOSED);
        return property != null ? property : false;
    }

    public static boolean getSkipOnce(InventoryContents contents) {
        Boolean property = contents.property(PROPERTY_SKIP_ONCE);
        return property != null ? property : false;
    }

    public static boolean getNoParent(InventoryContents contents) {
        Boolean property = contents.property(PROPERTY_NO_PARENT);
        return property != null ? property : false;
    }
}