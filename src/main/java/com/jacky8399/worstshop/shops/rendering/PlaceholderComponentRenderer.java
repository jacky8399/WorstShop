package com.jacky8399.worstshop.shops.rendering;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

// we are basically translating placeholders, right? RIGHT???
public class PlaceholderComponentRenderer extends TranslatableComponentRenderer<PlaceholderContext> {

    public static final PlaceholderComponentRenderer INSTANCE = new PlaceholderComponentRenderer();

    // apply placeholders, and optionally split the component if it contains color codes
    protected TextComponent applyPlaceholdersAndSplit(String input, PlaceholderContext context) {
        String result = Placeholders.setPlaceholders(input, context);
        if (result.indexOf(LegacyComponentSerializer.SECTION_CHAR) != -1) {
            return LegacyComponentSerializer.legacySection().deserialize(result);
        } else {
            return Component.text(result);
        }
    }

    @Override
    protected @NotNull Component renderText(@NotNull TextComponent component, @NotNull PlaceholderContext context) {
        TextComponent.Builder builder = applyPlaceholdersAndSplit(component.content(), context).toBuilder();
        return this.mergeStyleAndOptionallyDeepRender(component, builder, context); // appends to existing children so it's fine
    }

    @Override
    protected @NotNull Component renderTranslatable(@NotNull TranslatableComponent component, @NotNull PlaceholderContext context) {
        TranslatableComponent.Builder builder = Component.translatable().key(component.key()).fallback(component.fallback());
        return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
    }
}
