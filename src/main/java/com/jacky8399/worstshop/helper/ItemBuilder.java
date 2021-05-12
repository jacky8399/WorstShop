package com.jacky8399.worstshop.helper;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class ItemBuilder {
    private final ItemStack stack;
    private ItemBuilder(Material mat) {
        stack = new ItemStack(mat);
    }
    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
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

    public ItemBuilder amount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    private ItemMeta meta;

    public void loadMeta() {
        meta = stack.getItemMeta();
    }

    public ItemBuilder meta(ItemMeta meta) {
        this.meta = meta;
        return this;
    }

    public ItemBuilder meta(Consumer<ItemMeta> metaConsumer) {
        if (meta == null)
            loadMeta();
        metaConsumer.accept(meta);
        return this;
    }

    public ItemBuilder name(String str) {
        return meta(meta -> meta.setDisplayName(str));
    }

    public ItemBuilder skullOwner(OfflinePlayer p) {
        return meta(meta->{
            if (meta instanceof SkullMeta)
                ((SkullMeta) meta).setOwningPlayer(p);
            else
                throw new IllegalArgumentException("Material not a skull");
        });
    }

    public ItemBuilder lores(String... lore) {
        return lore(lore.length == 1 ? Collections.singletonList(lore[0]) : Arrays.asList(lore));
    }

    public ItemBuilder lore(List<String> lore) {
        return meta(meta -> meta.setLore(lore));
    }

    public ItemBuilder addLores(String... lore) {
        if (lore != null)
            addLore(lore.length == 1 ? Collections.singletonList(lore[0]) : Arrays.asList(lore));
        return this;
    }

    public ItemBuilder addLore(List<String> lore) {
        if (lore != null)
            meta(meta -> {
               List<String> oldLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
               oldLore.addAll(lore);
               meta.setLore(oldLore);
            });
        return this;
    }

}
