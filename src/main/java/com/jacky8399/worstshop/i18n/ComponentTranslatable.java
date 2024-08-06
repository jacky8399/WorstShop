package com.jacky8399.worstshop.i18n;

import com.jacky8399.worstshop.helper.ConfigHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public class ComponentTranslatable implements Function<Component[], Component> {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\d+?}");

    public ComponentTranslatable(String path) {
        this.path = path.toLowerCase(Locale.ROOT);
        update();
    }

    public final String path;

    @Override
    public String toString() {
        return MiniMessage.miniMessage().serialize(patternComponent);
    }
    public Component patternComponent;

    public void update() {
        String raw = ConfigHelper.translateString(I18n.lang.getString(path, path));
        var serializer = raw.indexOf(LegacyComponentSerializer.SECTION_CHAR) > -1 ?
                LegacyComponentSerializer.legacySection() :
                MiniMessage.miniMessage();
        patternComponent = serializer.deserialize(raw);
    }

    private static final Set<Style.Merge> MERGES = Set.of(Style.Merge.COLOR, Style.Merge.DECORATIONS, Style.Merge.FONT);

    @Override
    public Component apply(Component... components) {
        if (components.length == 0) {
            return patternComponent;
        }

        return Renderer.INSTANCE.render(patternComponent, components);
    }

    private static class Renderer extends TranslatableComponentRenderer<Component[]> {
        static final Renderer INSTANCE = new Renderer();

        // whether the component is a simple component that only contains text (no children and no styles)
        private static boolean isSimple(TextComponent textComponent) {
            return textComponent.children().isEmpty() &&
                    textComponent.style().isEmpty();
        }

        // apply placeholders
        protected static TextComponent.Builder applyPlaceholdersAndSplit(String input, Component[] context) {
            var builder = Component.text();
            String[] parts = PLACEHOLDER_PATTERN.splitWithDelimiters(input, 0);
            StringBuilder sb = new StringBuilder(); // collect simple components
            for (int i = 0; i < parts.length; i += 2) {
                // plain text
                sb.append(parts[i]);
                if (i + 1 < parts.length) {
                    String match = parts[i + 1];
                    int idx = Integer.parseInt(match.substring(1, match.length() - 1));
                    if (idx >= context.length) { // not in bounds for context
                        sb.append(match);
                        continue;
                    }
                    Component target = context[idx];
                    if (target instanceof TextComponent textComponent && isSimple(textComponent)) {
                        sb.append(textComponent.content());
                    } else {
                        // append previous simple parts
                        if (!sb.isEmpty()) {
                            builder.append(Component.text(sb.toString()));
                            sb.setLength(0);
                        }
                        builder.append(target);
                    }
                }
            }
            if (!sb.isEmpty()) { // append tail
                builder.append(Component.text(sb.toString()));
            }
            return builder;
        }

        @Override
        protected @NotNull Component renderText(@NotNull TextComponent component, Component @NotNull [] context) {
            TextComponent.Builder builder = applyPlaceholdersAndSplit(component.content(), context);
            return this.mergeStyleAndOptionallyDeepRender(component, builder, context); // appends to existing children so it's fine
        }

        @Override
        protected @NotNull Component renderTranslatable(@NotNull TranslatableComponent component, Component @NotNull [] context) {
            TranslatableComponent.Builder builder = Component.translatable().key(component.key()).fallback(component.fallback());
            return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
        }
    }
}
