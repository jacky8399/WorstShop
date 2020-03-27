package com.jacky8399.worstshop.helper;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.persistence.PersistentDataContainer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Fucking Bukkit does not provide the key set on PersistentDataContainers even though they are literally
     * backed by a map. This fucking detour solves the fucking problem.
     * @param container the container
     * @return the key set Bukkit won't fucking provide
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    public static Set<NamespacedKey> getPersistentDataContainerKeys(PersistentDataContainer container) {
        Class<? extends PersistentDataContainer> clazz = container.getClass();
        try {
            Field field = clazz.getDeclaredField("customDataTags");
            field.setAccessible(true);
            return ((Map<String, ?>) field.get(container)).keySet().stream().map(key -> {
                String[] split = key.split(":");
                // why the fuck is the custom namespace ctor deprecated fuck off
                return new NamespacedKey(split[0], split[1]);
            }).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
