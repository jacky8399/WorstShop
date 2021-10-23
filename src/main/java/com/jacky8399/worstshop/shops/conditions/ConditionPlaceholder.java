package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.Config;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ConditionPlaceholder extends Condition {
    public final String placeholderStr;
    private final Internal matcher;
    public ConditionPlaceholder(Config config) {
//        if (!WorstShop.get().placeholderAPI) throw new IllegalStateException("PlaceholderAPI not enabled!");
        placeholderStr = config.get("placeholder", String.class);
        Optional<String> regex = config.find("matches", String.class);
        if (regex.isPresent()) {
            matcher = new CompareRegex(regex.get());
        } else {
            matcher = new CompareString(config.get("equals", String.class));
        }
    }

    @Override
    public boolean test(Player player) {
        String replacedPlaceholderStr = I18n.doPlaceholders(player, placeholderStr);
        return matcher.test(replacedPlaceholderStr);
    }

    @Override
    public String toString() {
        return matcher.toString();
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "placeholder");
        map.put("placeholder", placeholderStr);
        if (matcher instanceof CompareRegex)
            map.put("matches", ((CompareRegex) matcher).pattern.toString());
        else
            map.put("equals", ((CompareString) matcher).string);
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(placeholderStr, matcher.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConditionPlaceholder))
            return false;
        ConditionPlaceholder other = (ConditionPlaceholder) obj;
        return other.placeholderStr.equals(placeholderStr) && other.matcher.equals(matcher);
    }

    private static abstract class Internal implements Predicate<String> {
        @Override
        public abstract String toString();
    }

    private class CompareRegex extends Internal {
        private final Pattern pattern;
        CompareRegex(String pattern) {
            this.pattern = Pattern.compile(pattern, Pattern.UNICODE_CHARACTER_CLASS);
        }

        @Override
        public String toString() {
            return "[\"" + placeholderStr + "\" matches \"" + pattern + "\"]";
        }

        @Override
        public boolean test(String s) {
            return pattern.matcher(s).matches();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CompareRegex && ((CompareRegex) obj).pattern.toString().equals(pattern.toString());
        }
    }

    private class CompareString extends Internal {
        private final String string;
        CompareString(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return "[\"" + placeholderStr + "\" == \"" + string + "\"]";
        }

        @Override
        public boolean test(String s) {
            return string.equals(s);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CompareString && ((CompareString) obj).string.equals(string);
        }
    }
}
