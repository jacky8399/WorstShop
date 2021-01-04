package com.jacky8399.worstshop.shops.elements;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@SuppressWarnings("deprecation")
public class StaticSerializationTest {
    private static final Logger logger = Logger.getLogger("Minecraft");
    @BeforeEach
    @SuppressWarnings("ConstantConditions")
    public void setupServer() {
        if (Bukkit.getServer() == null) {
            Server server = mock(Server.class);
            doReturn(logger).when(server).getLogger();
            doReturn("Fake server").when(server).getName();
            doReturn("1.0").when(server).getVersion();
            doReturn("1.0").when(server).getMinecraftVersion();
            doReturn("1.0").when(server).getBukkitVersion();

            doReturn(CraftItemFactory.instance()).when(server).getItemFactory();
            doReturn(CraftMagicNumbers.INSTANCE).when(server).getUnsafe();

            Bukkit.setServer(server);
        }
    }

    @SuppressWarnings("ConstantConditions")
    File getResource(String file) {
        return new File(getClass().getClassLoader().getResource(file).getFile());
    }

    String serializeItemStack(ItemStack stack) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", stack);
        return yaml.saveToString();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDeserialization() {
        File file = getResource("shop/elements/itemstack_parsing.yml");
        Map<String, Object> yaml = YamlConfiguration.loadConfiguration(file).getValues(false);
        for (Map<String, Object> toParse : (List<Map<String, Object>>) yaml.get("tests")) {
            System.out.println(getClass().getSimpleName() + ": Testing " + toParse.get("desc"));

            Map<String, Object> toParseObject = new HashMap<>((Map<String, Object>) toParse.get("to-parse"));
            ItemStack stack = StaticShopElement.parseItemStack(toParseObject);

            Map<String, Object> bukkitObject = new HashMap<>((Map<String, ?>) toParse.get("bukkit"));
            // add version
            bukkitObject.put("v", Bukkit.getUnsafe().getDataVersion());
            ItemStack actualStack = (ItemStack) ConfigurationSerialization.deserializeObject(bukkitObject, ItemStack.class);
            if (!actualStack.equals(stack)) {
                System.out.println("Parsed: ");
                System.out.println(serializeItemStack(stack));
                System.out.println("Bukkit: ");
                System.out.println(serializeItemStack(actualStack));
                Assertions.assertEquals(actualStack, stack);
            }
            // also test serialization
            Map<String, Object> idealSerialization = (Map<String, Object>) toParse.getOrDefault("serialized", toParseObject);
            Map<String, Object> serialized = StaticShopElement.serializeItemStack(stack, new HashMap<>());
            Assertions.assertEquals(idealSerialization, serialized);
        }
    }
}