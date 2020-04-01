package com.jacky8399.worstshop.helper;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateTimeUtils {
    private static final Pattern TIME_STR = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");
    public static Duration parseTimeStr(String str) {
        Matcher matcher = TIME_STR.matcher(str);
        if (!str.isEmpty() && matcher.matches()) {
            int secondsIntoFuture = 0;
            String days = matcher.group(1), hours = matcher.group(2), minutes = matcher.group(3), seconds = matcher.group(4);
            if (days != null) {
                secondsIntoFuture += Integer.parseInt(days) * 86400;
            }
            if (hours != null) {
                secondsIntoFuture += Integer.parseInt(hours) * 3600;
            }
            if (minutes != null) {
                secondsIntoFuture += Integer.parseInt(minutes) * 60;
            }
            if (seconds != null) {
                secondsIntoFuture += Integer.parseInt(seconds);
            }
            return Duration.of(secondsIntoFuture, ChronoUnit.SECONDS);
        }
        throw new IllegalArgumentException(str + " is not a valid time string");
    }

    public static String formatTime(Duration duration) {
        int seconds = (int) duration.getSeconds();
        int days = seconds / 86400;
        int hours = (seconds - days * 86400) / 3600;
        int minutes = (seconds - days * 86400 - hours * 3600) / 60;
        int remainingSeconds = seconds - days * 86400 - hours * 3600 - minutes * 60;
        StringBuilder sb = new StringBuilder();
        if (days != 0)
            sb.append(days).append("d");
        if (hours != 0)
            sb.append(hours).append("h");
        if (minutes != 0)
            sb.append(minutes).append("m");
        if (remainingSeconds != 0)
            sb.append(remainingSeconds).append("s");
        return sb.length() == 0 ? "0s" : sb.toString();
    }
}
