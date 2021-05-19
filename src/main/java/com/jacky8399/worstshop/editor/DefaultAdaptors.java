package com.jacky8399.worstshop.editor;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.InventoryCloseListener;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.ItemUtils;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultAdaptors {
    private DefaultAdaptors() {
    }

    public static final String I18N_KEY = I18n.Keys.MESSAGES_KEY + "editor.property.";

    private static String translate(String key, Object... args) {
        return I18n.translate(I18N_KEY + key, args);
    }

    public static final I18n.Translatable NAME_FORMAT = I18n.createTranslatable(I18N_KEY + "name-format");
    public static final I18n.Translatable VALUE_FORMAT = I18n.createTranslatable(I18N_KEY + "value-format");

    /**
     * Opens a interactive GUI allowing users to pick from a list of values when clicked.
     *
     * @param <T> the adaptor type
     */
    public static abstract class GUIAdaptor<T> implements EditableAdaptor<T> {
        class Inventory implements InventoryProvider {
            private T value;

            SmartInventory getInventory(@Nullable SmartInventory parent, T val, @Nullable String fieldName) {
                value = val;
                // estimate container size
                int rows = (int) Math.min(6, Math.ceil(getValues().size() / 9f) + 1);
                return WorstShop.buildGui("worstshop:editor_gui_adaptor")
                        .title(I18n.translate(I18N_KEY + "gui.title", fieldName != null ? fieldName : "???"))
                        .size(rows, 9).provider(this).parent(parent).build();
            }

            @Override
            public void init(Player player, InventoryContents contents) {
                // header
                contents.fillRow(0, ClickableItem.empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).build()));

                // items
                ClickableItem[] items = getValues().stream()
                        .map(val -> {
                            ItemBuilder repr = ItemBuilder.from(getRepresentation(val, null, null));
                            if (val == value) {
                                repr.meta(meta -> {
                                    meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
                                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                                }).addLores(I18n.translate(I18N_KEY + "gui.current"));
                            } else {
                                repr.addLores(I18n.translate(I18N_KEY + "gui.set"));
                            }
                            return ClickableItem.of(repr.build(), e -> {
                                future.complete(val);
                                Bukkit.getScheduler().runTask(WorstShop.get(), (Runnable) e.getWhoClicked()::closeInventory);
                            });
                        })
                        .toArray(ClickableItem[]::new);

                contents.pagination().setItems(items).setItemsPerPage(45)
                        .addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0));
            }

            @Override
            public void update(Player player, InventoryContents contents) {
            }
        }

        private CompletableFuture<T> future;

        @Override
        public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
            future = new CompletableFuture<>();

            SmartInventory parent = WorstShop.get().inventories.getInventory(player).orElse(null);
            new Inventory().getInventory(parent, val, fieldName).open(player);
            return future;
        }

        public abstract Collection<? extends T> getValues();
    }

    public static class EnumAdaptor<T extends Enum<T>> extends GUIAdaptor<T> {
        private Class<T> clazz;

        public EnumAdaptor(Class<T> clazz) {
            values = EnumSet.allOf(clazz);
        }

        private final EnumSet<T> values;

        @Override
        public Collection<? extends T> getValues() {
            return values;
        }

        @Override
        public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
            return ItemBuilder.of(Material.EMERALD_BLOCK)
                    .name(NAME_FORMAT.apply(fieldName))
                    .lores(VALUE_FORMAT.apply(val.name()))
                    .build();
        }
    }

    public static class BooleanAdaptor implements EditableAdaptor<Boolean> {
        @Override
        public CompletableFuture<Boolean> onInteract(Player player, Boolean val, @Nullable String fieldName) {
            return CompletableFuture.completedFuture(!val);
        }

        @Override
        public ItemStack getRepresentation(Boolean val, @Nullable String parentName, @Nullable String fieldName) {
            ItemBuilder builder = val ?
                    ItemBuilder.of(Material.GREEN_CONCRETE)
                            .lores(VALUE_FORMAT.apply(I18n.translate(I18N_KEY + "boolean.true"))) :
                    ItemBuilder.of(Material.RED_CONCRETE)
                            .lores(VALUE_FORMAT.apply(I18n.translate(I18N_KEY + "boolean.false")));
            return builder
                    .name(NAME_FORMAT.apply(fieldName))
                    .addLores(I18n.translate(I18N_KEY + "boolean.toggle"))
                    .build();
        }
    }

    public static abstract class TextAdaptor<T> implements EditableAdaptor<T> {
        @Override
        public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
            Runnable opener = InventoryCloseListener.closeTemporarilyWithoutParent(player);
            CompletableFuture<T> future = new CompletableFuture<>();
            player.beginConversation(new Conversation(WorstShop.get(), player,
                    createPrompt(player, fieldName, val, opener, future)));
            return future;
        }

        public Prompt createPrompt(Player player, String fieldName, T val, Runnable opener, CompletableFuture<T> future) {
            String current = marshal(val);
            return new Prompt() {
                @NotNull
                @Override
                public String getPromptText(@NotNull ConversationContext conversationContext) {
                    return I18n.translate(I18N_KEY + "text.editing", fieldName, getType()) + "\n" +
                            I18n.translate(I18N_KEY + "text.current", current);
                }

                @Override
                public boolean blocksForInput(@NotNull ConversationContext conversationContext) {
                    return true;
                }

                @Nullable
                @Override
                public Prompt acceptInput(@NotNull ConversationContext conversationContext, @Nullable String s) {
                    if (current.equals(s)) {
                        // exit
                        future.complete(val);
                        // reopen GUI
                        opener.run();
                        return Prompt.END_OF_CONVERSATION;
                    } else if (s != null && validateInput(s)) {
                        future.complete(unmarshal(s));
                        opener.run();
                        return Prompt.END_OF_CONVERSATION;
                    }
                    player.sendMessage(I18n.translate(I18N_KEY + "text.invalid"));
                    return createPrompt(player, fieldName, val, opener, future);
                }
            };
        }

        public abstract T unmarshal(String input);

        public abstract String marshal(T val);

        public abstract String getType();

        public String format = null;

        public void setFormat(String format) {
            this.format = format;
        }

        public boolean validateInput(String input) {
            return format == null || input.matches(format);
        }
    }

    public static class StringAdaptor extends TextAdaptor<String> {
        @Override
        public String unmarshal(String input) {
            return input;
        }

        @Override
        public String marshal(String val) {
            return val;
        }

        @Override
        public String getType() {
            return "string";
        }

        @Override
        public ItemStack getRepresentation(String val, @Nullable String parentName, @Nullable String fieldName) {
            return ItemBuilder.of(Material.NAME_TAG).name(NAME_FORMAT.apply(fieldName))
                    .lores(VALUE_FORMAT.apply(val))
                    .addLores(EditorUtils.getDesc(parentName, fieldName))
                    .build();
        }
    }

    public static class IntegerAdaptor extends TextAdaptor<Integer> {
        @Override
        public Integer unmarshal(String input) {
            return Integer.valueOf(input);
        }

        @Override
        public String marshal(Integer val) {
            return val.toString();
        }

        @Override
        public boolean validateInput(String input) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Integer.parseInt(input);
                return super.validateInput(input);
            } catch (IllegalFormatException e) {
                return false;
            }
        }

        @Override
        public String getType() {
            return "integer";
        }

        @Override
        public ItemStack getRepresentation(Integer val, @Nullable String parentName, @Nullable String fieldName) {
            return ItemBuilder.of(Material.PAPER).name(NAME_FORMAT.apply(fieldName))
                    .lores(VALUE_FORMAT.apply(Integer.toString(val)))
                    .addLores(EditorUtils.getDesc(parentName, fieldName))
                    .build();
        }
    }

    public static class EditableObjectAdaptor<T> implements EditableAdaptor<T> {
        private final Class<T> clazz;
        private final String name;
        private final List<Field> properties;

        public EditableObjectAdaptor(Class<T> clazz) {
            this.clazz = clazz;
            name = clazz.getAnnotation(Editable.class).value();
            properties = Arrays.stream(clazz.getFields())
                    .filter(field -> field.isAnnotationPresent(Property.class))
                    .sorted(Comparator.comparing(Field::getName))
                    .collect(Collectors.toList());
        }

        @Override
        public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
            int rows = Math.min(6, properties.size() / 9 + 2);

            SmartInventory parent = WorstShop.get().inventories.getInventory(player).orElse(null);
            class Inventory implements InventoryProvider {
                boolean shouldRefresh = false;

                // helper
                @SuppressWarnings("unchecked")
                private <TValue> ClickableItem createItemForField(Field field) {
                    EditableAdaptor<TValue> adaptor = EditorUtils.findAdaptorForField(val, field);
                    if (adaptor == null) // no adaptor; probably can't edit
                        return ClickableItem.empty(
                                ItemBuilder.of(Material.BARRIER).name(NAME_FORMAT.apply(fieldName)).build()
                        );
                    TValue value;
                    String fieldName = field.getName();
                    try {
                        value = (TValue) field.get(val);
                    } catch (IllegalAccessException e) {
                        RuntimeException wrapped = new RuntimeException("An error occurred while obtaining value for field " + fieldName, e);
                        // error item
                        return ClickableItem.empty(ItemUtils.getErrorItem(wrapped));
                    }
                    return ClickableItem.of(
                            adaptor.getRepresentation(value, name, fieldName),
                            e -> adaptor.onInteract(player, value, fieldName)
                                    .thenAccept(newValue -> {
                                        try {
                                            field.set(val, newValue);
                                        } catch (IllegalAccessException ex) {
                                            RuntimeException wrapped = new RuntimeException("An error occurred while updating value for field " + fieldName, ex);
                                            e.setCurrentItem(ItemUtils.getErrorItem(wrapped));
                                        }
                                        shouldRefresh = true;
                                    })
                    );
                }

                public void refresh(InventoryContents contents) {
                    ClickableItem[] items = properties.stream()
                            .map(this::createItemForField)
                            .toArray(ClickableItem[]::new);
                    contents.pagination().setItems(items).setItemsPerPage(45).addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0).allowOverride(true));
                }

                @Override
                public void init(Player player, InventoryContents contents) {
                    // header
                    contents.fillRow(0, ClickableItem.empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).build()));
                    // properties
                    refresh(contents);
                    // custom
                    EditableObjectAdaptor.this.init(player, contents);
                }

                @Override
                public void update(Player player, InventoryContents contents) {
                    EditableObjectAdaptor.this.update(player, contents, shouldRefresh);
                    if (shouldRefresh) {
                        refresh(contents);
                        shouldRefresh = false;
                    }
                }
            }

            SmartInventory toOpen = WorstShop.buildGui("worstshop:editor_editable_adaptor")
                    .provider(new Inventory()).size(rows, 9)
                    .parent(parent).title(getTitle()).build();
            InventoryCloseListener.openSafely(player, toOpen);
            // doesn't change the object
            return CompletableFuture.completedFuture(val);
        }

        public void init(Player player, InventoryContents contents) {
        }

        public void update(Player player, InventoryContents contents, boolean isRefreshingProperties) {
        }

        @Override
        public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
            return ItemBuilder.of(Material.WRITABLE_BOOK).name(NAME_FORMAT.apply(fieldName))
                    .lores(I18n.translate(I18N_KEY + "gui.edit"))
                    .addLores(EditorUtils.getDesc(parentName, fieldName))
                    .build();
        }

        protected String getTitle() {
            return NAME_FORMAT.apply(clazz.getSimpleName());
        }
    }

    public static class CustomRepresentationAdaptor<T> implements EditableAdaptor<T> {
        public final EditableAdaptor<T> internal;
        public final Material material;

        public CustomRepresentationAdaptor(EditableAdaptor<T> original, Representation repr) {
            internal = original;
            material = repr.value();
        }

        @Override
        public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
            return internal.onInteract(player, val, fieldName);
        }

        @Override
        public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
            ItemStack stack = internal.getRepresentation(val, parentName, fieldName);
            ItemStack ret = new ItemStack(material);
            ret.setAmount(stack.getAmount());
            ret.setItemMeta(Bukkit.getItemFactory().asMetaFor(stack.getItemMeta(), material));
            return ret;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static class ListAdaptor<T extends Collection<?>> implements EditableAdaptor<T> {
        private Supplier<@NotNull ?> supplier;

        public ListAdaptor() {
        }

        public void setFactory(@Nullable Class<? extends Supplier<@NotNull ?>> clazz) {
            if (clazz == null) {
                supplier = null;
            } else {
                try {
                    supplier = clazz.getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    supplier = null;
                }
            }
        }

        @Override
        public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
            int rows = val.size() / 9 + 2;
            boolean hasPages = rows > 6;

            SmartInventory parent = WorstShop.get().inventories.getInventory(player).orElse(null);
            class Inventory implements InventoryProvider {
                boolean shouldRefresh = false;
                boolean removalMode = false;

                ClickableItem createItem(Object obj, int idx) {
                    EditableAdaptor adaptor = EditorUtils.findAdaptorForClass((Class) obj.getClass(), obj);
                    if (adaptor == null) // no adaptor; probably can't edit
                        return ItemBuilder.of(Material.BARRIER).name(NAME_FORMAT.apply(fieldName)).toEmptyClickable();
                    String name = idx != -1 ? fieldName + " item " + (idx + 1) : fieldName + " item";
                    return ClickableItem.of(
                            adaptor.getRepresentation(obj, fieldName, name),
                            e -> adaptor.onInteract(player, obj, name)
                                    .thenAccept(newValue -> {
                                        if (val.equals(newValue))
                                            return;
                                        try {
                                            if (idx != -1) {
                                                ((List) val).set(idx, newValue);
                                            } else {
                                                val.remove(obj);
                                                ((Collection) val).add(newValue);
                                            }
                                        } catch (Exception ex) {
                                            RuntimeException wrapped = new RuntimeException("An error occurred while updating value for field " + fieldName, ex);
                                            e.setCurrentItem(ItemUtils.getErrorItem(wrapped));
                                        }
                                        shouldRefresh = true;
                                    })
                    );
                }

                void refresh(InventoryContents contents) {
                    // clear all
                    contents.fill(null);
                    // header
                    contents.fillRow(0, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).toEmptyClickable());
                    Pagination pagination = contents.pagination();
                    if (hasPages && !pagination.isFirst())
                        contents.set(0, 0, ItemBuilder.of(Material.ARROW).name(translate("gui.prev-page"))
                                .toClickable(e -> {
                                    if (e.isRightClick())
                                        pagination.first();
                                    else
                                        pagination.page(pagination.getPage() - 1);
                                    shouldRefresh = true;
                                })
                        );
                    if (hasPages && !pagination.isLast())
                        contents.set(0, 8, ItemBuilder.of(Material.ARROW).name(translate("gui.next-page"))
                                .toClickable(e -> {
                                    if (e.isRightClick())
                                        pagination.last();
                                    else
                                        pagination.page(pagination.getPage() + 1);
                                    shouldRefresh = true;
                                })
                        );
                    // append item
                    boolean create = supplier != null;
                    contents.set(0, 4, ItemBuilder.of(create ? Material.WRITABLE_BOOK : Material.BOOK)
                            .name(translate(create ? "list.new" : "list.new-unsupported"))
                            .toClickable(e -> {
                                if (!create) return;
                                boolean success = false;
                                try {
                                    success = ((Collection) val).add(supplier.get());
                                } catch (Exception ignored) {
                                }
                                if (success) {
                                    shouldRefresh = true;
                                }
                            })
                    );
                    // mark for removal
                    contents.set(0, 5, ItemBuilder.of(removalMode ? Material.BARRIER : Material.STRUCTURE_VOID)
                            .name(translate(removalMode ? "list.delete" : "list.delete-end"))
                            .toClickable(e -> {
                                removalMode = !removalMode;
                                shouldRefresh = true;
                            })
                    );

                    ClickableItem[] items;
                    if (val instanceof List) {
                        items = new ClickableItem[val.size()];
                        for (ListIterator<?> iterator = ((List<?>) val).listIterator(); iterator.hasNext();) {
                            int idx = iterator.nextIndex();
                            Object next = iterator.next();
                            items[idx] = createItem(next, idx);
                        }
                    } else {
                        items = ((Collection<?>) val).stream().map(item -> createItem(item, -1)).toArray(ClickableItem[]::new);
                    }
                    SlotIterator iterator = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0);
                    pagination.setItems(items).setItemsPerPage(45).addToIterator(iterator);
                }

                @Override
                public void init(Player player, InventoryContents contents) {
                    refresh(contents);
                }

                @Override
                public void update(Player player, InventoryContents contents) {
                    if (shouldRefresh)
                        refresh(contents);
                }
            }

            SmartInventory toOpen = WorstShop.buildGui("worstshop:editor_editable_adaptor")
                    .provider(new Inventory()).size(6, 9)
                    .parent(parent).title(fieldName).build();
            InventoryCloseListener.openSafely(player, toOpen);
            return CompletableFuture.completedFuture(val);
        }

        @Override
        public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
            return null;
        }
    }
}
