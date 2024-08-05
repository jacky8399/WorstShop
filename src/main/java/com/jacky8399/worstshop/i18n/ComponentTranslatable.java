package com.jacky8399.worstshop.i18n;

import com.jacky8399.worstshop.helper.ConfigHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.AttributedCharacterIterator;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public class ComponentTranslatable implements Function<Component[], Component> {
    public ComponentTranslatable(String path) {
        this.path = path.toLowerCase(Locale.ROOT);
        update();
    }

    public final String path;

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

    private static final Set<Style.Merge> MERGES = Set.of(Style.Merge.COLOR, Style.Merge.DECORATIONS, Style.Merge.FONT);

    @Override
    public Component apply(Component... components) {
        if (components.length == 0) {
            return LegacyComponentSerializer.legacySection().deserialize(pattern);
        }
        // borrowed from TranslatableComponentRenderer
        // I love Adventure :3
        var builder = Component.text();
        final Object[] nulls = new Object[components.length];
        final StringBuffer sb = format.format(nulls, new StringBuffer(), null);
        final AttributedCharacterIterator it = format.formatToCharacterIterator(nulls);
        Style.Builder style = Style.style();

        while (it.getIndex() < it.getEndIndex()) {
            final int end = it.getRunLimit();
            final Integer index = (Integer) it.getAttribute(MessageFormat.Field.ARGUMENT);
            if (index != null) {
                Component component = components[index];
                // retain the component's styles
                builder.append(component.style(component.style().merge(style.build(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET, MERGES)));
            } else {
                TextComponent userText = LegacyComponentSerializer.legacySection().deserialize(sb.substring(it.getIndex(), end));
                if (!userText.content().isEmpty()) {
                    style.merge(userText.style(), MERGES);
                    builder.append(Component.text(userText.content(), style.build()));
                }
                for (Component child : userText.children()) {
                    style.merge(child.style(), MERGES);
                    builder.append(child.style(style));
                }
            }
            it.setIndex(end);
        }
        return builder.build();
    }
}
