package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionPermission extends Condition {
    private String perm;

    public ConditionPermission(String permission) {
        this.perm = permission;
    }

    public static Condition fromPermString(String permRaw) {
        final String perm = permRaw.trim();
        if (perm.equalsIgnoreCase("true") || perm.equalsIgnoreCase("false")) {
            boolean result = perm.equalsIgnoreCase("true");
            return ConditionConstant.valueOf(result);
        } else if (ONLY_PERMS.matcher(perm).matches()) {
            return new ConditionPermission(perm);
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
        throw new IllegalArgumentException("Invalid perm string " + perm);
    }

    @Override
    public boolean test(Player player) {
        return player.hasPermission(perm);
    }

    private static final Pattern BRACKETS = Pattern.compile("^\\(\\s*(.+)\\s*\\)$");
    //private static final String permsCriterion = "(?:[A-Za-z0-9.\\-_]|\"[A-Za-z0-9.\\-_\\s]+\")+";
    private static final String permsCriterion = "(?:[A-Za-z0-9.\\-_]|(?<=\\.)\".+?\")+";
    private static final Pattern AND = Pattern.compile("^(\\(.+\\)|" + permsCriterion + ")\\s*&\\s*(\\(.+\\)|" + permsCriterion + ")$");
    private static final Pattern OR = Pattern.compile("^(\\(.+\\)|" + permsCriterion + ")\\s*\\|\\s*(\\(.+\\)|" + permsCriterion + ")$");
    private static final Pattern NEGATE = Pattern.compile("^[!~](\\(.+\\)|" + permsCriterion + ")$");
    private static final Pattern ONLY_PERMS = Pattern.compile("^" + permsCriterion + "$");
}
