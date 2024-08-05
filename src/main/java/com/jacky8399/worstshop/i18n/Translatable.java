package com.jacky8399.worstshop.i18n;

import com.jacky8399.worstshop.helper.ConfigHelper;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.function.Function;

public class Translatable implements Function<Object[], String> {
    public Translatable(String path) {
        this.path = path.toLowerCase(Locale.ROOT);
        update();
    }

    public final String path;

    @Override
    public String apply(Object... strings) {
        return format.format(strings);
    }

    @Override
    public String toString() {
        return pattern;
    }

    private MessageFormat format;
    private String pattern;

    public void update() {
        pattern = ConfigHelper.translateString(I18n.lang.getString(path, path));
        format = new MessageFormat(pattern);
    }
}
