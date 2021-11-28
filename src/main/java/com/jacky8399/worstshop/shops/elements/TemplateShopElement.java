package com.jacky8399.worstshop.shops.elements;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TemplateShopElement extends ShopElement {

    public final String variable;
    public String materialOverride;
    public TemplateShopElement(String variable) {
        this.variable = variable;
    }

    public static TemplateShopElement fromYaml(Config config) {
        TemplateShopElement element = new TemplateShopElement(config.get("use", String.class));
        config.find("with", Config.class)
                .map(ConfigHelper::parseVariables)
                .ifPresent(element.variables::putAll);
        element.materialOverride = config.find("override-item", String.class).orElse(null);
        ParseContext.pushContext(element);
        return element;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public List<RenderElement> getRenderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
        PlaceholderContext selfContext = placeholder.withElement(this);

        Material newMaterial;
        if (materialOverride != null) {
            String mat = Placeholders.setPlaceholders(materialOverride, selfContext).replace(' ', '_');
            newMaterial = Material.matchMaterial(mat);
            if (newMaterial == null) {
                WorstShop.get().logger.warning("Error while trying to set placeholders for material '" +
                        materialOverride + "' for " + this + ": " + mat + " is not a valid item type!");
            }
        } else {
            newMaterial = null;
        }

        Object template = selfContext.getVariable(variable);
        if (template instanceof ShopElement element) {
            return element.getRenderElement(renderer, selfContext).stream()
                    .map(renderElement -> {
                        if (newMaterial != null) {
                            ItemStack stack = renderElement.stack().clone();
                            stack.setType(newMaterial);
                            return new RenderElement(this, renderElement.positions(), stack,
                                    selfContext, renderElement.handler(), renderElement.flags());
                        } else {
                            return renderElement.withOwner(this, selfContext);
                        }
                    })
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "[template " + variable + "]";
    }
}
