package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionPermission extends Condition {
    public final String perm;

    public ConditionPermission(String permission) {
        this(permission, false);
    }

    public ConditionPermission(String permission, boolean isFromShorthand) {
        this.perm = permission;
        this.isFromShorthand = isFromShorthand;
    }

    public transient boolean isFromShorthand;

    // i forgot to escape quotes lol
    public static ConditionPermission untangleQuotes(String permission) {
        StringBuilder builder = new StringBuilder(permission);
        int idx = 0;
        while ((idx = builder.indexOf("\"", idx)) != -1) {
            if (idx != 0 && builder.charAt(idx - 1) == '\\')
                builder.deleteCharAt(idx - 1);
            else // remove unescaped quotation marks
                builder.deleteCharAt(idx);
        }
        return new ConditionPermission(builder.toString(), true);
    }

    @Deprecated
    public static Condition fromPermString(String permRaw) {
        final String perm = permRaw.trim();
        if (perm.equalsIgnoreCase("true") || perm.equalsIgnoreCase("false")) {
            boolean result = perm.equalsIgnoreCase("true");
            return ConditionConstant.valueOf(result);
        } else if (ONLY_PERMS.matcher(perm).matches()) {
            return untangleQuotes(perm);
        }
        Matcher bracketsMatcher = BRACKETS.matcher(perm);
        if (bracketsMatcher.matches()) {
            return fromPermString(bracketsMatcher.group(1));
        }
        Matcher andMatcher = AND.matcher(perm);
        if (andMatcher.matches()) {
            Condition p1 = fromPermString(andMatcher.group(1)), p2 = fromPermString(andMatcher.group(2));
            return p1.and(p2);
        }
        Matcher orMatcher = OR.matcher(perm);
        if (orMatcher.matches()) {
            Condition p1 = fromPermString(orMatcher.group(1)), p2 = fromPermString(orMatcher.group(2));
            return p1.or(p2);
        }
        Matcher notMatcher = NEGATE.matcher(perm);
        if (notMatcher.matches()) {
            Condition p = fromPermString(notMatcher.group(1));
            return p.negate();
        }
        throw new IllegalArgumentException("Invalid permission string " + perm);
    }

    @Override
    public String toString() {
        return perm;
    }

    @Override
    public boolean test(Player player) {
        return player.hasPermission(perm);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "permission");
        map.put("permission", perm);
        return map;
    }

    @Override
    public int hashCode() {
        return perm.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ConditionPermission && ((ConditionPermission) obj).perm.equals(perm);
    }

    private static final Pattern BRACKETS = Pattern.compile("^\\(\\s*(.+)\\s*\\)$");
    //private static final String permsCriterion = "(?:[A-Za-z0-9.\\-_]|\"[A-Za-z0-9.\\-_\\s]+\")+";
    private static final String permRegex = "(?:[A-Za-z0-9.\\-_]|(?<=[.\\s]|^)\".+?(?<!\\\\)\")+";
    private static final Pattern AND = Pattern.compile("^(\\(.+\\)|" + permRegex + ")\\s*&\\s*(\\(.+\\)|" + permRegex + ")$");
    private static final Pattern OR = Pattern.compile("^(\\(.+\\)|" + permRegex + ")\\s*\\|\\s*(\\(.+\\)|" + permRegex + ")$");
    private static final Pattern NEGATE = Pattern.compile("^[!~](\\(.+\\)|" + permRegex + ")$");
    private static final Pattern ONLY_PERMS = Pattern.compile("^" + permRegex + "$");
}
