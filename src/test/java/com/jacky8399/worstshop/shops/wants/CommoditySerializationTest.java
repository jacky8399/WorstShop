package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.StaticSerializationTest;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

public class CommoditySerializationTest {
    static MockedStatic<WorstShop> plugin;
    @BeforeAll
    static void beforeAll() {
        // setup item factory thing
        StaticSerializationTest.setupServer();

        WorstShop inst = mock(WorstShop.class);
        when(inst.getName()).thenReturn("WorstShop");
        inst.logger = Logger.getLogger("WorstShop");
        plugin = mockStatic(WorstShop.class);
        plugin.when(WorstShop::get).thenReturn(inst);

        // Vault and PlayerPoints
        // for Vault, just mock the service provider
        RegisteredServiceProvider<?> provider = mock(RegisteredServiceProvider.class);
        doReturn(null).when(provider).getProvider();
        inst.economy = (RegisteredServiceProvider<Economy>) provider;
        inst.playerPoints = mock(PlayerPoints.class);
    }

    @AfterAll
    public static void afterAll() {
        plugin.close();
    }

    @Test
    public void testDeserialization() {
        List<ShopWants> expected = Arrays.asList(
                new ShopWantsCommand("/icanhasbukkit", ShopWantsCommand.CommandInvocationMethod.CONSOLE, 1),
                new ShopWantsExp(123, 456),
                ShopWantsFree.INSTANCE,
                new ShopWantsItem(ItemBuilder.of(Material.TNT).name("boom").amount(10).build())
                        .setItemMatchers(Arrays.asList(ShopWantsItem.ItemMatcher.MATERIAL, ShopWantsItem.ItemMatcher.NAME)),
                new ShopWantsMoney(101.23),
                new ShopWantsMoney(101.24),
                // idk how to test for that
                // new ShopWantsPermission()
                new ShopWantsPlayerPoint(13),
                new ShopWantsCustomizable(ShopWantsFree.INSTANCE, StaticShopElement.fromStack(new ItemStack(Material.STRUCTURE_VOID)))
        );
        File file = new File(getClass().getClassLoader().getResource("shop/commodity_parsing.yml").getFile());
        List<?> yaml;
        try (FileInputStream stream = new FileInputStream(file)) {
            yaml = new Yaml().load(stream);
        } catch (IOException e) {
            Assertions.fail("Failed to open file", e);
            return;
        }
        int idx = 0;
        for (Object obj : yaml) {
            ShopWants shopWants = ShopWants.fromObject(obj instanceof Map<?, ?> ? new Config((Map<String, Object>) obj, null, "[" + idx + "]") : obj);
            Assertions.assertEquals(expected.get(idx++), shopWants);
        }
    }
}
