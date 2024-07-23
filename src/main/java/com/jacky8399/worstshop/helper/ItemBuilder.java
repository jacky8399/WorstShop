package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class ItemBuilder {
    private final ItemStack stack;
    private ItemMeta meta;

    private ItemBuilder(Material mat) {
        stack = new ItemStack(mat);
        meta = stack.getItemMeta();
    }

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
        meta = stack.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder from(ItemStack stack) {
        return new ItemBuilder(stack);
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }

    public ClickableItem toEmptyClickable() {
        return ClickableItem.empty(build());
    }

    public ClickableItem toClickable(Consumer<InventoryClickEvent> consumer) {
        return ClickableItem.of(build(), consumer);
    }

    public Material type() {
        return stack.getType();
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    @Deprecated
    public void loadMeta() {
        meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(StaticShopElement.SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public ItemBuilder type(Material material) {
        if (meta == null)
            loadMeta();
        meta = Bukkit.getItemFactory().asMetaFor(meta, material);
        stack.setType(material);
        stack.setItemMeta(meta);
        return meta(meta);
    }

    public ItemBuilder meta(ItemMeta meta) {
        this.meta = meta;
        this.meta.getPersistentDataContainer().set(StaticShopElement.SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
        return this;
    }

    public ItemBuilder meta(Consumer<ItemMeta> metaConsumer) {
        if (meta == null)
            loadMeta();
        metaConsumer.accept(meta);
        return this;
    }

    public ItemMeta meta() {
        if (meta == null)
            loadMeta();
        return meta;
    }

    public ItemBuilder removeSafetyKey() {
        return meta(meta -> meta.getPersistentDataContainer().remove(StaticShopElement.SAFETY_KEY));
    }

    public ItemBuilder name(String str) {
        meta.setDisplayName(str);
        return this;
    }

    public ItemBuilder displayName(Component component) {
        meta.displayName(component);
        return this;
    }

    public ItemBuilder skullOwner(OfflinePlayer p) {
        return meta(meta -> {
            if (meta instanceof SkullMeta skullMeta)
                skullMeta.setOwningPlayer(p);
            else
                throw new IllegalArgumentException("Material not a skull");
        });
    }

    public ItemBuilder lores(String... lore) {
        return lore(Arrays.asList(lore));
    }

    public ItemBuilder lore(List<String> lore) {
        meta.setLore(lore);
        return this;
    }

    @SuppressWarnings("ConstantConditions")
    public ItemBuilder addLores(String... lore) {
        if (lore != null) {
            List<String> oldLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            Collections.addAll(oldLore, lore);
            meta.setLore(oldLore);
        }
        return this;
    }

    @SuppressWarnings("ConstantConditions")
    public ItemBuilder addLore(List<String> lore) {
        if (lore != null) {
            List<String> oldLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            oldLore.addAll(lore);
            meta.setLore(oldLore);
        }
        return this;
    }

}
