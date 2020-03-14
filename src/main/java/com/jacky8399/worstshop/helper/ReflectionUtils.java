package com.jacky8399.worstshop.helper;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;

public class ReflectionUtils {
    public static Object getNMSInstance(Object object) {
        Class<?> clazz = object.getClass();
        try {
            return clazz.getMethod("getHandle").invoke(object);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    private static Class<?> craftContainerClazz;
    private static Constructor<?> craftContainerConstructor;
    private static Method craftContainerGetBukkitView;
    public static InventoryView createViewFor(Inventory inventory, Player player) {
        Object entityPlayer = getNMSInstance(player);

        if (craftContainerClazz == null) {
            try {
                Class<? extends Inventory> clazz = inventory.getClass();
                String packageName = clazz.getPackage().getName();
                craftContainerClazz = Class.forName(packageName + ".CraftContainer");
                // public CraftContainer(Inventory inv, EntityHuman player, int windowId)
                craftContainerConstructor = craftContainerClazz.getConstructor(Inventory.class, entityPlayer.getClass().getSuperclass(), Integer.TYPE);
                // public InventoryView getBukkitView()
                craftContainerGetBukkitView = craftContainerClazz.getMethod("getBukkitView");
            } catch (Exception e) {
                throw new Error(e);
            }
        }

        try {
            Object container = craftContainerConstructor.newInstance(inventory, entityPlayer, (new Random()).nextInt());
            return (InventoryView) craftContainerGetBukkitView.invoke(container);
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
