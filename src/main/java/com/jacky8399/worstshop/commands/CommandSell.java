package com.jacky8399.worstshop.commands;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import com.jacky8399.worstshop.helper.InventoryUtils;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.shops.ItemShop;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.actions.ActionShop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.ListIterator;

@CommandAlias("sell")
@CommandPermission("worstshop.command.sell")
public class CommandSell extends BaseCommand {

    public static boolean sellStack(Player player, ItemStack stack, boolean promptIfFailed) {
        if (ItemUtils.isEmpty(stack))
            return false;
        List<ItemShop> itemShops = ShopManager.ITEM_SHOPS.get(stack.getType());
        if (itemShops != null) {
            // there exists shop for the equipped type
            for (ItemShop shop : itemShops) {
                if (shop.isAvailableTo(player) && shop.isSellable(stack, player)) {
                    shop.sell(stack, player);
                    return true;
                }
            }
        }
        if (promptIfFailed) {
            player.sendMessage(ActionShop.formatNothingMessage());
        }
        return false;
    }

    // lol bukkit api

    public static boolean sellInventory(Player player, Inventory inventory) {
        boolean everSucceeded = false;
        boolean skipEventCalls = inventory instanceof PlayerInventory; // skip events call if player is selling their inv
        InventoryView inventoryView = InventoryUtils.makeInventoryView(player, inventory);
        if (!skipEventCalls) { // more events
            InventoryOpenEvent e = new InventoryOpenEvent(inventoryView);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) {
                InventoryCloseEvent eClose = new InventoryCloseEvent(inventoryView, InventoryCloseEvent.Reason.PLUGIN);
                Bukkit.getPluginManager().callEvent(eClose);
                return false;
            }
        }
        ListIterator<ItemStack> iterator = inventory.iterator();
        while (iterator.hasNext()) {
            int slotIdx = iterator.nextIndex();
            ItemStack stack = iterator.next();
            if (ItemUtils.isEmpty(stack))
                continue;
            // superfluous event calls to ensure the player can interact with the item.
            if (!skipEventCalls) {
                InventoryClickEvent eClick = new InventoryClickEvent(inventoryView, InventoryType.SlotType.CONTAINER, slotIdx, ClickType.SHIFT_LEFT, InventoryAction.PICKUP_ALL);
                Bukkit.getPluginManager().callEvent(eClick);
                if (eClick.isCancelled())
                    continue;

                InventoryMoveItemEvent eMove = new InventoryMoveItemEvent(inventory, stack, player.getInventory(), false);
                Bukkit.getPluginManager().callEvent(eMove);
                if (eMove.isCancelled()) // skip this stack if event is cancelled
                    continue;
            }

            List<ItemShop> itemShops = ShopManager.ITEM_SHOPS.get(stack.getType());
            if (itemShops != null) {
                // there exists shop for type
                for (ItemShop shop : itemShops) {
                    if (shop.isAvailableTo(player) && shop.isSellable(stack, player)) {
                        shop.sell(stack, player);
                        everSucceeded = true;
                    }
                }
            }
        }

        if (!skipEventCalls) {
            InventoryCloseEvent e = new InventoryCloseEvent(inventoryView, InventoryCloseEvent.Reason.PLUGIN);
            Bukkit.getPluginManager().callEvent(e);
        }
        return everSucceeded;
    }

    @Subcommand("hand")
    @CommandPermission("worstshop.command.sell.hand")
    public void sellHand(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        sellStack(player, stack, true);
    }

    @Subcommand("all")
    @CommandPermission("worstshop.command.sell.all")
    public void sellAll(Player player) {
        boolean everSucceeded = sellInventory(player, player.getInventory());
        if (!everSucceeded) {
            player.sendMessage(ActionShop.formatNothingMessage());
        }
        player.updateInventory();
    }

    @Subcommand("container")
    @CommandPermission("worstshop.command.sell.container")
    public void sellFacingContainer(Player player) {
        RayTraceResult rayTraceResult = player.rayTraceBlocks(5.0f);
        if (rayTraceResult != null && rayTraceResult.getHitBlock() != null) {
            Block hitBlock = rayTraceResult.getHitBlock();
            if (hitBlock.getState() instanceof Container) {
                // try to open the container
                PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                        player.getInventory().getItemInMainHand(),
                        hitBlock, rayTraceResult.getHitBlockFace());

                Bukkit.getPluginManager().callEvent(event);

                // wow turns out some plugins open containers and deny the event
                if (event.useInteractedBlock() != Event.Result.DENY) {
                    Inventory inv = ((Container) hitBlock.getState()).getInventory();
                    boolean everSucceeded = sellInventory(player, inv);
                    if (!everSucceeded) {
                        player.sendMessage(ActionShop.formatNothingMessage());
                    }
                } else if (player.getOpenInventory().getTopInventory().getType() == InventoryType.CHEST) {
                    Inventory inv = player.getOpenInventory().getTopInventory();
                    // kindly closes the inventory
                    player.closeInventory();

                    boolean everSucceeded = sellInventory(player, inv);
                    if (!everSucceeded) {
                        player.sendMessage(ActionShop.formatNothingMessage());
                    }
                }
            }
        }
    }

    @Subcommand("material")
    @CommandPermission("worstshop.command.sell.material")
    @CommandCompletion("*")
    public void sellMaterial(Player player, Material mat) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat)
                sellStack(player, stack, true);
        }
    }
}
