package com.jacky8399.worstshop.helper;

import org.bukkit.entity.Player;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PermStringHelper {
    private static final Pattern brackets = Pattern.compile("^\\(\\s*(.+)\\s*\\)$");
    private static final String permsCriterion = "(?:[A-Za-z0-9.\\-_]|\"[A-Za-z0-9.\\-_\\s]+\")+";
    private static final Pattern and = Pattern.compile("^(\\(.+\\)|" + permsCriterion + ")\\s*&\\s*(\\(.+\\)|" + permsCriterion + ")$");
    private static final Pattern or = Pattern.compile("^(\\(.+\\)|" + permsCriterion + ")\\s*\\|\\s*(\\(.+\\)|" + permsCriterion + ")$");
    private static final Pattern negate = Pattern.compile("^[!~](\\(.+\\)|" + permsCriterion + ")$");
    private static final Pattern onlyPerms = Pattern.compile("^" + permsCriterion + "$");
    public static Predicate<Player> parsePermString(String permRaw) {
        final String perm = permRaw.trim();
        if (perm.equalsIgnoreCase("true") || perm.equalsIgnoreCase("false")) {
            boolean result = perm.equalsIgnoreCase("true");
            return new Predicate<Player>() {
                @Override
                public boolean test(Player player) {
                    return result;
                }

                @Override
                public String toString() {
                    return Boolean.toString(result);
                }
            };
        } else if (onlyPerms.matcher(perm).matches()) {
            return new Predicate<Player>() {
                @Override
                public boolean test(Player player) {
                    return player.hasPermission(perm);
                }

                @Override
                public String toString() {
                    return "[" + perm + "]";
                }
            };
        }
        Matcher bracketsMatcher = brackets.matcher(perm);
        if (bracketsMatcher.matches()) {
            return parsePermString(bracketsMatcher.group(1));
        }
        Matcher andMatcher = and.matcher(perm);
        if (andMatcher.matches()) {
            Predicate<Player> p1 = parsePermString(andMatcher.group(1)), p2 = parsePermString(andMatcher.group(2));
            return new Predicate<Player>() {
                @Override
                public boolean test(Player p) {
                    return p1.test(p) && p2.test(p);
                }

                @Override
                public String toString() {
                    return "(" + p1.toString() + " and " + p2.toString() + ")";
                }
            };
        }
        Matcher orMatcher = or.matcher(perm);
        if (orMatcher.matches()) {
            Predicate<Player> p1 = parsePermString(orMatcher.group(1)), p2 = parsePermString(orMatcher.group(2));
            return new Predicate<Player>() {
                @Override
                public boolean test(Player p) {
                    return p1.test(p) || p2.test(p);
                }

                @Override
                public String toString() {
                    return "(" + p1.toString() + " or " + p2.toString() + ")";
                }
            };
        }
        Matcher notMatcher = negate.matcher(perm);
        if (notMatcher.matches()) {
            Predicate<Player> p = parsePermString(notMatcher.group(1));
            return new Predicate<Player>() {
                @Override
                public boolean test(Player player) {
                    return !p.test(player);
                }

                @Override
                public String toString() {
                    return "(not " + p.toString() + ")";
                }
            };
        }
        throw new IllegalArgumentException("Invalid perm string " + perm);
    }
}
