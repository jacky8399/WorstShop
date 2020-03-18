package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Optional;

public class InventoryCloseListener extends InventoryListener<InventoryCloseEvent> {

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

            Boolean skipOnce = contents.<Boolean>property("skipOnce");
            if (skipOnce != null && skipOnce) {
                contents.setProperty("skipOnce", false);
                return;
            }
            if (contents.property("hasClosed", false) || contents.property("noParent", false))
                return;
            contents.setProperty("hasClosed", true);

            Optional<SmartInventory> parent = of.get().getParent();

            // return to parent
            Bukkit.getScheduler().runTask(WorstShop.get(), ()->
                parent.ifPresent(smartInventory -> smartInventory.open(p))
            );
        });
    }
}