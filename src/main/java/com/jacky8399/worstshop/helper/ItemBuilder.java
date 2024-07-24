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

import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class ItemBuilder {
    private final ItemStack stack;
    private ItemMeta meta;

    private ItemBuilder(Material mat) {
        stack = new ItemStack(mat);
        meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(StaticShopElement.SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
        meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(StaticShopElement.SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
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

    public ClickableItem emptyClickable() {
        return ClickableItem.empty(build());
    }

    public ClickableItem ofClickable(Consumer<InventoryClickEvent> consumer) {
        return ClickableItem.of(build(), consumer);
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

    public ItemBuilder type(Material material) {
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
        metaConsumer.accept(meta);
        return this;
    }

    public ItemMeta meta() {
        return meta;
    }

    public ItemBuilder removeSafetyKey() {
        return meta(meta -> meta.getPersistentDataContainer().remove(StaticShopElement.SAFETY_KEY));
    }

    public ItemBuilder name(String str) {
        meta.setDisplayName(str);
        return this;
    }

    public ItemBuilder name(Component component) {
        meta.displayName(component);
        return this;
    }

    public ItemBuilder skullOwner(OfflinePlayer p) {
        if (meta instanceof SkullMeta skullMeta)
            skullMeta.setOwningPlayer(p);
        else
            throw new IllegalArgumentException("Material not a skull");
        return this;
    }

    @Deprecated
    public ItemBuilder lores(String... lore) {
        return lore(Arrays.asList(lore));
    }

    @Deprecated
    public ItemBuilder lore(List<String> lore) {
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder lores(Component... components) {
        return lore(Arrays.asList(components));
    }

    public ItemBuilder lore(Collection<? extends Component> lore) {
        meta.lore(lore instanceof List<? extends Component> list ? list : List.copyOf(lore));
        return this;
    }

    @SuppressWarnings("ConstantConditions")
    public ItemBuilder addLores(String... lore) {
        return addLore(Arrays.asList(lore));
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

    public ItemBuilder addLores(Component... lore) {
        return addLore(Arrays.asList(lore));
    }

    public ItemBuilder addLore(Collection<? extends Component> lore) {
        if (lore != null) {
            List<Component> oldLore = meta.hasLore() ? meta.lore() : List.of();
            List<Component> newLore = new ArrayList<>(oldLore.size() + lore.size());
            newLore.addAll(oldLore);
            newLore.addAll(lore);
            meta.lore(newLore);
        }
        return this;
    }

}
