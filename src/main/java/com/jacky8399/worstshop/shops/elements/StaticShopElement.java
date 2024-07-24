package com.jacky8399.worstshop.shops.elements;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.common.collect.ImmutableSet;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.Editable;
import com.jacky8399.worstshop.editor.Property;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Editable
public class StaticShopElement extends ShopElement {
    public static NamespacedKey SAFETY_KEY = new NamespacedKey(WorstShop.get(), "shop_item");

    @NotNull
    @Property
    public ItemStack rawStack = new ItemStack(Material.AIR);

    @Property
    public boolean async = false;
    @Nullable
    @Property
    public ItemStack asyncLoadingItem = null;
    public transient boolean hasRemindedAsync = false;

    // for faster client load times
    @Nullable
    private transient PaperHelper.GameProfile skullCache;

    public static StaticShopElement fromStack(ItemStack stack) {
        StaticShopElement inst = new StaticShopElement();
        if (stack != null)
            inst.rawStack = stack;
        inst.actions = Collections.emptyList();
        return inst;
    }


    public static ShopElement fromYaml(Config config) {
        // static parsing
        StaticShopElement inst = new StaticShopElement();

        inst.id = config.find("id", Object.class).map(Object::toString).orElseGet(() -> {
            // try to assign random id
            Shop shop = ParseContext.findLatest(Shop.class);
            if (shop != null) {
                return "index=" + shop.elements.size();
            }
            return "???";
        });

        inst.owner = ShopReference.of(ParseContext.findLatest(Shop.class));

        // push context earlier for error-handling
        ParseContext.pushContext(inst);

        ItemStack rawStack = parseItemStack(config);

        // die if null
        if (ItemUtils.isEmpty(rawStack))
            return null;

        inst.rawStack = rawStack;

        inst.async = config.find("async", Boolean.class).orElse(false);
        if (inst.async)
            inst.asyncLoadingItem = config.find("async-loading-item", Config.class)
                    .map(StaticShopElement::parseItemStack).orElse(null);

        // cache if possible
        if (inst.rawStack.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) inst.rawStack.getItemMeta();
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(meta);
            if (profile != null && profile.getName() != null && !profile.hasSkin()) {
                profile.completeProfile().thenAccept(ignored -> inst.skullCache = profile);
            }
        }

        // don't pop context just yet
        return inst;
    }

    public static ItemMeta deserializeBase64ItemMeta(String base64) {
        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        // Bukkit API what the fuck
        // Do not use Bukkit YamlConfiguration as it converts embedded Maps into MemorySections,
        // which SerializableItemMeta (also from Bukkit API) does NOT like
        Map<String, Object> map = (new Yaml()).load(decoded);
        //noinspection ConstantConditions
        return (ItemMeta) ConfigurationSerialization.deserializeObject(map, ConfigurationSerialization.getClassByAlias("ItemMeta"));
    }

    public static String serializeBase64ItemMeta(ItemMeta meta) {
        Map<String, Object> map = meta.serialize();
        YamlConfiguration temp = new YamlConfiguration();
        temp.addDefaults(map);
        temp.options().copyDefaults(true);
        return new String(Base64.getEncoder().encode(temp.saveToString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    // values are from CraftMetaItem.SerializableMeta.classMap
    public static final Set<String> SUPPORTED_ITEM_META_TYPES = ImmutableSet.<String>builder().add("UNSPECIFIC", "SKULL", "ENCHANTED").build();

    public static boolean isItemMetaSupported(ItemMeta meta) {
        //noinspection SuspiciousMethodCalls
        return SUPPORTED_ITEM_META_TYPES.contains(meta.serialize().get("meta-type"));
    }


    // minimessage instance that applies the default lore style (white and no italics)
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .postProcessor(component -> component
                    .style(component.style().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                    .compact()
            )
            .build();
    public static ItemStack parseItemStack(Config yaml) {
        try {
            Material material = Material.matchMaterial(yaml.get("item", String.class).replace(' ', '_'));
            if (material == null) {
                throw new IllegalStateException("Illegal material " + yaml.get("item", String.class));
            } else if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                return null; // skip air
            }
            ItemBuilder is = ItemBuilder.of(material);
            Object amount = yaml.find("amount", Integer.class, String.class).or(()->yaml.find("count", Integer.class, String.class)).orElse(1);
            if (amount instanceof Integer integer) {
                is.amount(Math.min(Math.max(integer, 1), material.getMaxStackSize()));
            } else {
                is.amount(1);
                is.meta().getPersistentDataContainer().set(Placeholders.ITEM_AMOUNT_KEY, PersistentDataType.STRING, ((String) amount));
            }

            Optional<String> itemMetaString = yaml.find("item-meta", String.class);
            if (itemMetaString.isPresent()) {
                ItemMeta decoded = deserializeBase64ItemMeta(itemMetaString.get());
                is.meta(decoded);
                // allow basic properties to further influence this ItemStack
            }

            yaml.find("max-damage", Integer.class).ifPresent(maxDamage -> {
                if (maxDamage != 0 && is.meta() instanceof Damageable damageable) {
                    damageable.setMaxDamage(maxDamage);
                }
            });

            yaml.find("damage", Integer.class).ifPresent(damage -> {
                if (damage != 0 && is.meta() instanceof Damageable damageable) {
                    damageable.setDamage(damage);
                }
            });

            yaml.find("enchantment-glint", Boolean.class).ifPresent(is::enchantmentGlint);
            yaml.find("hide-tooltip", Boolean.class).ifPresent(is::hideTooltip);

            yaml.find("custom-model-data", Integer.class).ifPresent(customModelData ->
                    is.meta(meta -> meta.setCustomModelData(customModelData))
            );

            yaml.find("enchants", Config.class).ifPresent(enchants ->
                    is.meta(meta -> {
                        // support enchanted books
                        BiConsumer<Enchantment, Integer> consumer = meta instanceof EnchantmentStorageMeta ?
                                (e, i) -> ((EnchantmentStorageMeta) meta).addStoredEnchant(e, i, true) :
                                (e, i) -> meta.addEnchant(e, i, true);

                        enchants.getPrimitiveMap().forEach((ench, level) -> {
                            NamespacedKey key = NamespacedKey.fromString(ench);
                            if (key == null) throw new ConfigException(ench + " is not a valid key!", enchants);
                            Enchantment enchType = Registry.ENCHANTMENT.get(key);
                            if (enchType == null)
                                throw new ConfigException(ench + " is not a valid enchantment!", enchants);
                            if (!(level instanceof Number))
                                throw new ConfigException("Expected level to be a number", enchants, ench);
                            consumer.accept(enchType, ((Number) level).intValue());
                        });
                    })
            );

            Optional<String> optionalMessage = yaml.find("message", String.class).or(() -> yaml.find("msg", String.class));
            if (optionalMessage.isPresent()) {
                String message = optionalMessage.get();
                List<Component> lines = message.lines().map(MINI_MESSAGE::deserialize).toList();
                is.name(lines.getFirst());
                if (lines.size() > 1) {
                    is.lore(lines.subList(1, lines.size()));
                }
            } else {
                Optional<String> optionalText = yaml.find("text", String.class);
                if (optionalText.isPresent()) {
                    String[] lines = optionalText.get().split("\n");
                    is.name(lines[0]);
                    if (lines.length > 1) {
                        is.lores(Arrays.copyOfRange(lines, 1, lines.length));
                    }
                }
            }

            yaml.find("name", String.class).map(MINI_MESSAGE::deserialize).ifPresent(is::name);

            yaml.find("loc-name", String.class).ifPresent(locName -> is.name(Component.translatable(locName)));

            yaml.find("lore", List.class, String.class).ifPresent(loreObj -> {
                if (loreObj instanceof List<?>) {
                    is.lore(yaml.getList("lore", String.class).stream().map(MINI_MESSAGE::deserialize).toList());
                } else {
                    is.lore(loreObj.toString().lines().map(MINI_MESSAGE::deserialize).toList());
                }
            });

            yaml.find("item-name", String.class).map(MINI_MESSAGE::deserialize).ifPresent(is::itemName);

            yaml.find("unbreakable", Boolean.class).ifPresent(bool -> is.meta(meta -> meta.setUnbreakable(bool)));

            yaml.findList("hide", String.class)
                    .or(() -> yaml.findList("hide-flags", String.class)) // old name
                    .ifPresent(flags -> {
                        ItemFlag[] itemFlags = flags.stream()
                                .map(flag -> !flag.startsWith("HIDE") ? "HIDE_" + flag : flag)
                                .map(flag -> ConfigHelper.parseEnum(flag, ItemFlag.class))
                                .toArray(ItemFlag[]::new);
                        is.meta(meta -> meta.addItemFlags(itemFlags));
                    });

            // skull
            // maybe I should've used PersistentDataContainers instead of attaching data to profiles lol
            yaml.find("skull", String.class).ifPresent(uuidOrName -> {
                UUID uuid = null;
                try {
                    uuid = UUID.fromString(uuidOrName);
                    uuidOrName = null; // make name null
                } catch (IllegalArgumentException ignored) {
                }
                if (is.meta() instanceof SkullMeta skullMeta) {
                    // names longer than 16 characters will cause errors
                    skullMeta.setPlayerProfile(ItemUtils.makeProfileExact(uuid, uuidOrName));
                } else {
                    throw new ConfigException("skull can only be used on player heads! Got " + is.type() + " (" + is.meta() + ")", yaml, "skull");
                }
            });
            yaml.find("skin", String.class).ifPresent(skin -> {
                PaperHelper.GameProfile profile = PaperHelper.createProfile(UUID.randomUUID(), null);
                profile.setSkin(skin);
                if (is.meta() instanceof SkullMeta skullMeta) {
                    PaperHelper.setSkullMetaProfile(skullMeta, profile);
                } else {
                    throw new ConfigException("skin can only be used on player heads! Got" + is.type() + " (" + is.meta() + ")", yaml, "skin");
                }
            });
            return is.build();
        } catch (Exception ex) {
            if (ex instanceof ConfigException) {
                throw ex;
            } else {
                throw new ConfigException("Parsing item stack", yaml, ex);
            }
        }
    }

    /**
     * Serializes an ItemStack.
     * <p>
     * This method always attempts to use the simplest representation.
     */
    @SuppressWarnings("ConstantConditions")
    public static Map<String, Object> serializeItemStack(ItemStack stack, Map<String, Object> map) {
        map.put("item", stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (stack.getAmount() != 1)
            map.put("amount", stack.getAmount());
        // remove safety key first
        ItemMeta meta = ItemUtils.removeSafetyKey(stack.getItemMeta());

        if (meta instanceof Damageable damageable) {
            if (damageable.hasDamage()) {
                map.put("damage", damageable.getDamage());
            }
        }
        if (meta.hasCustomModelData()) {
            map.put("custom-model-data", meta.getCustomModelData());
        }
        if (meta.hasEnchants() || (meta instanceof EnchantmentStorageMeta && ((EnchantmentStorageMeta) meta).hasStoredEnchants())) {
            HashMap<String, Object> enchants = new HashMap<>();
            var realEnchants = meta instanceof EnchantmentStorageMeta storageMeta ? storageMeta.getStoredEnchants() : meta.getEnchants();
            realEnchants.forEach((ench, level) -> {
                // strip namespace if minecraft:
                String key = ench.getKey().getNamespace().equals(NamespacedKey.MINECRAFT) ? ench.getKey().getKey() : ench.getKey().toString();
                enchants.put(key, level);
            });
            map.put("enchants", enchants);
        }
        if (meta.hasDisplayName()) {
            map.put("name", ConfigHelper.untranslateString(meta.getDisplayName()));
        }
        if (meta.displayName() instanceof TranslatableComponent translatableComponent) {
            map.put("loc-name", translatableComponent.key());
        }
        if (meta.hasLore()) {
            map.put("lore", meta.getLore().stream().map(ConfigHelper::untranslateString).collect(Collectors.toList()));
        }
        if (meta.getItemFlags().size() != 0) {
            map.put("hide", meta.getItemFlags().stream().map(ItemFlag::name)
                    .map(str -> str.substring("HIDE_".length())) // strip hide
                    .map(str -> str.toLowerCase().replace('_', ' ')) // to lowercase
                    .collect(Collectors.toList()));
        }
        if (meta instanceof SkullMeta skullMeta) {
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(skullMeta);
            if (profile != null) {
                if (profile.hasSkin()) {
                    map.put("skin", profile.getSkin());
                }
                String nameOrUUID = profile.getName();
                if (nameOrUUID == null)
                    nameOrUUID = profile.getUUID().toString();
                map.put("skull", nameOrUUID);
            }
        }

        // magic string
        // will be included when meta is complex
        // (contains custom data / is of an unsupported ItemMeta class / has attribute modifiers (WIP))
        if (!isItemMetaSupported(meta) || !meta.getPersistentDataContainer().isEmpty() || meta.getAttributeModifiers() != null)
            map.put("item-meta", serializeBase64ItemMeta(meta));
        return map;
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        super.toMap(map);
        if (ItemUtils.isEmpty(rawStack))
            map.put("preserve-space", true);
        else
            serializeItemStack(rawStack, map);
        if (async)
            map.put("async", true);
        if (asyncLoadingItem != null)
            map.put("async-loading-item", serializeItemStack(asyncLoadingItem, new HashMap<>()));
        return map;
    }

    /**
     * Applies placeholders to the {@link StaticShopElement#rawStack}
     * @param player The target player
     * @return The resultant item stack
     */
    public ItemStack createPlaceholderStack(Player player) {
        if (!condition.test(player)) {
            return null;
        }

        // parse placeholders
        long start = System.currentTimeMillis();
        ItemStack stack = Placeholders.setPlaceholders(this.rawStack, player);
        long end = System.currentTimeMillis();
//        if (!async && !hasRemindedAsync && end - start > 500) {
//            WorstShop.get().logger.warning("Placeholders took " + (end - start) + "ms. Consider making this element async. (" + id + "@" + owner.id + ")\n" +
//                    "Note that some placeholders may stop working when used async.");
//            hasRemindedAsync = true;
//        }
        // try to apply cache
        if (skullCache != null && stack.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) stack.getItemMeta();
            PlayerProfile currentProfile = meta.getPlayerProfile();
            // ensure that the cache is still up-to-date
            if (Objects.equals(skullCache.getName(), currentProfile != null ? currentProfile.getName() : null)) {
                PaperHelper.setSkullMetaProfile(meta, skullCache);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    public static final ItemStack ASYNC_PLACEHOLDER = ItemBuilder.of(Material.BEDROCK)
            .name("" + ChatColor.RED + ChatColor.BOLD + "...").build();
    record AsyncTask(List<RenderElement> placeholder, CompletableFuture<ItemStack> future) {}
    // use a map to prevent conflicts
    private final Map<Player, AsyncTask> asyncItemCache = Collections.synchronizedMap(new WeakHashMap<>());

    private List<RenderElement> getAsyncPlaceholderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
        ItemStack toReturn = asyncLoadingItem != null ?
                Placeholders.setPlaceholders(asyncLoadingItem, placeholder) :
                ASYNC_PLACEHOLDER.clone();
        ItemMeta meta = toReturn.getItemMeta();
        meta.getPersistentDataContainer().set(SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
        toReturn.setItemMeta(meta);
        return List.of(new RenderElement(this, getFiller(renderer).fill(this, renderer),
                toReturn, getClickHandler(renderer), DYNAMIC_FLAGS));
    }

    private List<RenderElement> getStaticRenderElement(ShopRenderer renderer, ItemStack stack) {
        return List.of(new RenderElement(this, getFiller(renderer).fill(this, renderer), stack,
                PlaceholderContext.NO_CONTEXT, getClickHandler(renderer), STATIC_FLAGS));
    }

    @Override
    public List<RenderElement> getRenderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
        Player player = renderer.player;
        if (async) {
            synchronized (asyncItemCache) {
                var awaiting = asyncItemCache.get(player);
                if (awaiting != null) {
                    if (awaiting.future.isDone()) {
                        asyncItemCache.remove(player);
                        return getStaticRenderElement(renderer, awaiting.future.resultNow());
                    } else {
                        return awaiting.placeholder;
                    }
                } else {
                    var future = new CompletableFuture<ItemStack>();
                    awaiting = new AsyncTask(getAsyncPlaceholderElement(renderer, placeholder), future);
                    asyncItemCache.put(player, awaiting);
                    // schedule task
                    Bukkit.getScheduler().runTaskAsynchronously(WorstShop.get(), () -> {
                        ItemStack stack = createStack(renderer);
                        completePlayerSkin(stack);
                        future.complete(stack);
                    });
                    return awaiting.placeholder;
                }
            }
        } else if (
                rawStack.getType() == Material.PLAYER_HEAD &&
                ((SkullMeta) rawStack.getItemMeta()).getPlayerProfile() instanceof PlayerProfile profile &&
                profile.hasProperty(ItemUtils.SKULL_PROPERTY)
        ) {
            // the player head looks like it will need updating
            synchronized (asyncItemCache) {
                var awaiting = asyncItemCache.get(player);
                if (awaiting != null) {
                    if (awaiting.future.isDone()) {
                        asyncItemCache.remove(player);
                        return getStaticRenderElement(renderer, awaiting.future.resultNow());
                    } else {
                        return awaiting.placeholder;
                    }
                } else {
                    // check if scheduling is needed
                    ItemStack stack = Placeholders.setPlaceholders(createStack(renderer), new PlaceholderContext(renderer));
                    if (isPendingPlayerSkin(stack)) {
                        // mark the element as dynamic
                        var placeholderElement = List.of(getStaticRenderElement(renderer, stack).getFirst().withFlags(DYNAMIC_FLAGS));

                        SkullMeta meta = (SkullMeta) stack.getItemMeta();
                        var actualProfile = Objects.requireNonNull(meta.getPlayerProfile());
                        awaiting = new AsyncTask(placeholderElement, actualProfile.update().thenApply(updated -> {
                            meta.setPlayerProfile(updated);
                            stack.setItemMeta(meta);
                            return stack;
                        }));
                        asyncItemCache.put(player, awaiting);
                        return awaiting.placeholder;
                    } else {
                        return getStaticRenderElement(renderer, stack);
                    }
                }
            }
        }

        return super.getRenderElement(renderer, placeholder);
    }

    @Override
    public ItemStack createStack(ShopRenderer renderer) {
        Player player = renderer.player;

        final ItemStack readonlyStack = rawStack.clone();
        final ItemStack actualStack = readonlyStack.clone();

        // let actions influence item
        actions.forEach(action -> action.influenceItem(player, readonlyStack.clone(), actualStack));

        // put unique identifier
        ItemMeta meta = actualStack.getItemMeta();
        meta.getPersistentDataContainer().set(SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
        actualStack.setItemMeta(meta);

        return actualStack;
    }

    public static boolean isPendingPlayerSkin(ItemStack stack) {
        return stack.getType() == Material.PLAYER_HEAD &&
                ((SkullMeta) stack.getItemMeta()).getPlayerProfile() instanceof PlayerProfile profile &&
                !profile.isComplete();
    }

    @Blocking
    public static void completePlayerSkin(ItemStack stack) {
        if (stack.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) stack.getItemMeta();
            var profile = meta.getPlayerProfile();
            if (profile != null && !profile.hasTextures()) {
                profile.complete();
                meta.setPlayerProfile(profile);
                stack.setItemMeta(meta);
            }
        }
    }

    public static boolean isShopItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        return container.has(SAFETY_KEY, PersistentDataType.BYTE);
    }

    @Override
    public String toString() {
        return "static " + super.toString() + "(stack=" + rawStack.getType() + ")";
    }

    @Override
    public int hashCode() {
        return rawStack.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StaticShopElement other))
            return false;
        return other.rawStack.equals(rawStack) && Objects.equals(other.skullCache, skullCache) && other.async == async;
    }

    @Override
    public StaticShopElement clone() {
        StaticShopElement element = (StaticShopElement) super.clone();
        element.rawStack = rawStack.clone();
        if (asyncLoadingItem != null)
            element.asyncLoadingItem = asyncLoadingItem.clone();
        return element;
    }
}
