package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
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

    protected ConditionPlaceholder(String placeholderStr, Internal matcher) {
        this.placeholderStr = placeholderStr;
        this.matcher = matcher;
    }

    public static final Pattern SHORTHAND_PATTERN = Pattern.compile("^(.+?)\\s*(!)?(matches|=?=)\\s*(.*)$");
    public static Condition fromShorthand(String shorthand) {
        Matcher matcher = SHORTHAND_PATTERN.matcher(shorthand);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid shorthand syntax");
        }
        String placeholderStr = matcher.group(1);
        boolean isNegated = matcher.group(2) != null;
        String format = matcher.group(4);
        Internal internal = "matches".equals(matcher.group(3)) ? new CompareRegex(format) : new CompareString(format);
        ConditionPlaceholder condition = new ConditionPlaceholder(placeholderStr, internal);
        condition.isFromShorthand = true;
        return isNegated ? condition.negate() : condition;
    }
    public transient boolean isFromShorthand = false;

    @Override
    public boolean test(Player player) {
        PlaceholderContext context = PlaceholderContext.guessContext(player);
        String replacedPlaceholderStr = Placeholders.setPlaceholders(placeholderStr, context);
        return matcher.test(replacedPlaceholderStr);
    }

    @Override
    public String toString() {
        return "[\"" + placeholderStr + "\" " + matcher + "]";
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
        if (!(obj instanceof ConditionPlaceholder other))
            return false;
        return other.placeholderStr.equals(placeholderStr) && other.matcher.equals(matcher);
    }

    private static abstract class Internal implements Predicate<String> {
        @Override
        public abstract String toString();
    }

    private static class CompareRegex extends Internal {
        private final Pattern pattern;
        CompareRegex(String pattern) {
            this.pattern = Pattern.compile(pattern, Pattern.UNICODE_CHARACTER_CLASS);
        }

        @Override
        public String toString() {
            return "matches \"" + pattern + "\"";
        }

        @Override
        public boolean test(String s) {
            return pattern.matcher(s).matches();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CompareRegex cmp && cmp.pattern.pattern().equals(pattern.pattern());
        }
    }

    private static class CompareString extends Internal {
        private final String string;
        CompareString(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return "== \"" + string + "\"";
        }

        @Override
        public boolean test(String s) {
            return string.equals(s);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CompareString cmp && cmp.string.equals(string);
        }
    }
}
