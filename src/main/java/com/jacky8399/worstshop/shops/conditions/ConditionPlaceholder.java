package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.WorstShop;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ConditionPlaceholder extends Condition {
    public String placeholderStr;
    public Predicate<String> matcher;
    public ConditionPlaceholder(Map<String, Object> yaml) {
        if (!WorstShop.get().placeholderAPI) throw new IllegalStateException("PlaceholderAPI not enabled!");
        placeholderStr = (String) yaml.get("placeholder");
        if (yaml.containsKey("matches")) {
            matcher = Pattern.compile((String) yaml.get("matches")).asPredicate();
        } else {
            String str = (String) yaml.get("equals");
            matcher = str::equals;
        }
    }

    @Override
    public boolean test(Player player) {
        String replacedPlaceholderStr = PlaceholderAPI.setPlaceholders(player, placeholderStr);
        return matcher.test(replacedPlaceholderStr);
    }
}
