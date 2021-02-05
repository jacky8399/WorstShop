package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class ConditionSerializationTest {
    static MockedStatic<WorstShop> plugin;
    @BeforeAll
    static void beforeAll() {
        WorstShop inst = mock(WorstShop.class);
        inst.logger = Logger.getLogger("WorstShop");
        plugin = mockStatic(WorstShop.class);
        plugin.when(WorstShop::get).thenReturn(inst);
    }

    @AfterAll
    public static void afterAll() {
        plugin.close();
    }


    @Test
    public void createConstant() {
        Config configTrue = new Config(Collections.singletonMap("preset", true)),
                configTrue2 = new Config(Collections.singletonMap("preset", "true")),
                configFalse = new Config(Collections.singletonMap("preset", false)),
                configFalse2 = new Config(Collections.singletonMap("preset", "false"));

        Condition condTrue = Condition.fromMap(configTrue), condTrue2 = Condition.fromMap(configTrue2),
                condFalse = Condition.fromMap(configFalse), condFalse2 = Condition.fromMap(configFalse2);

        Assertions.assertEquals(ConditionConstant.TRUE, condTrue);
        Assertions.assertEquals(ConditionConstant.FALSE, condFalse);
        Assertions.assertEquals(ConditionConstant.TRUE, condTrue2);
        Assertions.assertEquals(ConditionConstant.FALSE, condFalse2);
    }

    private String logicYaml = String.join("\n",
            "logic: and",
            "conditions:",
            "- logic: or",
            "  conditions:",
            "  - preset: true",
            "  - preset: false",
            "- logic: not",
            "  condition:",
            "    preset: false"
    );
    @Test
    public void createLogic() {
        Map<String, Object> map = new Yaml().load(logicYaml);
        Config logicConfig = new Config(map);
        Condition condition = Condition.fromMap(logicConfig);

        Assertions.assertEquals(condition, (ConditionConstant.TRUE.or(ConditionConstant.FALSE)).and(ConditionConstant.FALSE.negate()));
    }

    @Test
    public void createPermFromPermString() {
        String permStr = "a & (!(b & (\"c d\")))";
        Condition condition = ConditionPermission.fromPermString(permStr);

        Assertions.assertEquals(condition,
                (new ConditionPermission("a")).and(
                        (new ConditionPermission("b").and(new ConditionPermission("c d"))).negate()
                )
        );
    }
}
