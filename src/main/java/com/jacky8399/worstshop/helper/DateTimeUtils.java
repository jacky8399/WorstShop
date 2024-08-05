package com.jacky8399.worstshop.helper;

import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateTimeUtils {
    private static final Pattern TIME_STR = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?(?:(\\d+)t)?");
    public static Duration parseTimeStr(String str) {
        Matcher matcher = TIME_STR.matcher(str);
        if (!str.isEmpty() && matcher.matches()) {
            long secondsIntoFuture = 0;
            int ticksIntoFuture = 0;
            String days = matcher.group(1), hours = matcher.group(2), minutes = matcher.group(3), seconds = matcher.group(4), ticks = matcher.group(5);
            if (days != null) {
                secondsIntoFuture += Long.parseLong(days) * 86400L;
            }
            if (hours != null) {
                secondsIntoFuture += Long.parseLong(hours) * 3600L;
            }
            if (minutes != null) {
                secondsIntoFuture += Long.parseLong(minutes) * 60L;
            }
            if (seconds != null) {
                secondsIntoFuture += Integer.parseInt(seconds);
            }
            if (ticks != null) {
                ticksIntoFuture = Integer.parseInt(ticks);
            }
            return Duration.of(secondsIntoFuture * 1000L + ticksIntoFuture * 50L, ChronoUnit.MILLIS);
        }
        throw new IllegalArgumentException(str + " is not a valid time string");
    }

    public static String formatTime(Duration duration) {
        return formatTime(duration, false);
    }

    public static String formatTime(Duration duration, boolean withTicks) {
        long days = duration.toDaysPart();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        StringBuilder sb = new StringBuilder();
        if (days != 0)
            sb.append(days).append("d");
        if (hours != 0)
            sb.append(hours).append("h");
        if (minutes != 0)
            sb.append(minutes).append("m");
        if (seconds != 0)
            sb.append(seconds).append("s");
        if (withTicks && duration.getNano() != 0) {
            int millis = duration.getNano() / ChronoUnit.MILLIS.getDuration().getNano();
            sb.append(millis / 50).append("t");
        }
        return sb.isEmpty() ? "0s" : sb.toString();
    }

    public static Component formatReadableDuration(Duration duration, Locale locale) {
        long days = duration.toDaysPart();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        List<String> parts = new ArrayList<>();
        if (days != 0)
            parts.add(days + " " + ChronoField.DAY_OF_MONTH.getDisplayName(locale));
        if (hours != 0)
            parts.add(hours + " " + ChronoField.HOUR_OF_DAY.getDisplayName(locale));
        if (minutes != 0)
            parts.add(minutes + " " + ChronoField.MINUTE_OF_HOUR.getDisplayName(locale));
        if (seconds != 0)
            parts.add(seconds + " " + ChronoField.SECOND_OF_MINUTE.getDisplayName(locale));
        return Component.text(parts.isEmpty() ?
                "0 " + ChronoField.SECOND_OF_MINUTE.getDisplayName(locale) :
                String.join(" ", parts));
    }

    public static String formatTime(ZonedDateTime time) {
        return time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG));
    }

    public static String formatTime(LocalDateTime time) {
        return formatTime(time.atZone(ZoneId.systemDefault()));
    }
}
