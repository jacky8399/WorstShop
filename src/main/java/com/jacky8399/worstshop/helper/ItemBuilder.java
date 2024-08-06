package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    public ItemBuilder maxAmount(int maxAmount) {
        meta.setMaxStackSize(maxAmount);
        return this;
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.min(amount, meta.hasMaxStackSize() ? meta.getMaxStackSize() : stack.getType().getMaxStackSize()));
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

    @Deprecated
    public ItemBuilder name(String str) {
        meta.setDisplayName(str);
        return this;
    }

    public ItemBuilder name(Component component) {
        meta.displayName(component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        return this;
    }

    public ItemBuilder itemName(Component component) {
        meta.itemName(component);
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
    public ItemBuilder lore(Collection<String> lore) {
        meta.setLore(List.copyOf(lore));
        return this;
    }

    public ItemBuilder lores(Component... components) {
        return lore(Arrays.asList(components));
    }

    public ItemBuilder lore(List<? extends Component> lore) {
        List<Component> list = new ArrayList<>(lore.size());
        for (Component line : lore) {
            Component component = line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
            list.add(component);
        }
        meta.lore(list);
        return this;
    }

    @SuppressWarnings("ConstantConditions")
    @Deprecated
    public ItemBuilder addLores(String... lore) {
        return addLore(Arrays.asList(lore));
    }

    @SuppressWarnings("ConstantConditions")
    @Deprecated
    public ItemBuilder addLore(Collection<String> lore) {
        if (lore != null) {
            List<Component> oldLore = meta.hasLore() ? meta.lore() : List.of();
            List<Component> newLore = new ArrayList<>(oldLore.size() + lore.size());
            newLore.addAll(oldLore);
            for (String line : lore) {
                newLore.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(newLore);
        }
        return this;
    }

    public ItemBuilder addLores(Component... lore) {
        return addLore(Arrays.asList(lore));
    }

    public ItemBuilder addLore(List<? extends Component> lore) {
        if (lore != null) {
            List<Component> oldLore = meta.hasLore() ? meta.lore() : List.of();
            List<Component> newLore = new ArrayList<>(oldLore.size() + lore.size());
            newLore.addAll(oldLore);
            for (Component line : lore) {
                newLore.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            meta.lore(newLore);
        }
        return this;
    }

    public ItemBuilder hideTooltip() {
        return hideTooltip(true);
    }

    public ItemBuilder hideTooltip(boolean hideTooltip) {
        meta.setHideTooltip(hideTooltip);
        return this;
    }

    public ItemBuilder enchantmentGlint(boolean enchantmentGlint) {
        meta.setEnchantmentGlintOverride(enchantmentGlint);
        return this;
    }

}
