package com.jacky8399.worstshop.shops.rendering;

import com.google.common.collect.Maps;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.content.SlotPos;

import java.util.*;
import java.util.function.Function;

public class DefaultSlotFiller {
    private static final HashMap<String, DefaultSlotFiller> FILLERS = new HashMap<>();
    // very special case
    public static final SlotFiller NONE;
    public static final SlotFiller ALL;
    public final String name;
    public final String usage;
    public final Function<String[], SlotFiller> fillerFactory;

    private DefaultSlotFiller(String name, SlotFiller staticFiller) {
        this(name, name, input -> staticFiller);
    }

    private DefaultSlotFiller(String name, String usage, Function<String[], SlotFiller> filler) {
        this.name = name;
        this.usage = usage;
        this.fillerFactory = input -> new SlotFiller() {
            final SlotFiller actual = filler.apply(input);

            @Override
            public Collection<SlotPos> fill(ShopElement element, ShopRenderer renderer) {
                return actual.fill(element, renderer);
            }

            @Override
            public String toString() {
                return input.length == 0 ? name : name + " " + String.join(" ", input);
            }
        };
        FILLERS.put(name, this);
    }

    static {
        ALL = new DefaultSlotFiller("all", (element, renderer) -> renderer.getSlots())
                .fillerFactory.apply(null);
        NONE = new DefaultSlotFiller("none", (element, renderer) -> {
            if (element.itemPositions != null) {
                return element.itemPositions;
            } else {
                return null;
            }
        }).fillerFactory.apply(null);
        new DefaultSlotFiller("border", "border <radius>", input -> {
            int radius = Integer.parseInt(input[0]);
            return (element, renderer) -> {
                List<SlotPos> pos = new ArrayList<>();
                for (int i = 0, rows = renderer.getRows(); i < rows; i++) {
                    for (int j = 0, columns = renderer.getColumns(); j < columns; j++) {
                        if (i < radius || i >= rows - radius || j < radius || j >= columns - radius) {
                            pos.add(new SlotPos(i, j));
                        }
                    }
                }
                return pos;
            };
        });
        new DefaultSlotFiller("remaining", (element, renderer) -> {
            int row = renderer.getRows(), column = renderer.getColumns();
            Set<SlotPos> items = new HashSet<>();
            for (int i = 0; i < row; i++) {
                for (int j = 0; j < column; j++) {
                    items.add(new SlotPos(i, j));
                }
            }
            renderer.backgrounds.add((context, ignored) -> {
                if (element.condition.test(renderer.player)) {
                    List<RenderElement> elements = element.getRenderElement(context, new PlaceholderContext(context));
                    if (!elements.isEmpty()) {
                        var first = elements.getFirst();
                        return Maps.asMap(items, pos -> first);
                    }
                }
                return Collections.emptyMap();
            });
            return null;
        });
        new DefaultSlotFiller("row", "row <row>", input -> {
            int row = Integer.parseInt(input[0]);
            return ((element, renderer) -> {
                List<SlotPos> pos = new ArrayList<>();
                for (int i = 0, columns = renderer.getColumns(); i < columns; i++) {
                    pos.add(new SlotPos(row, i));
                }
                return pos;
            });
        });
        new DefaultSlotFiller("column", "column <column>", input -> {
            int column = Integer.parseInt(input[0]);
            return (element, renderer) -> {
                List<SlotPos> pos = new ArrayList<>();
                for (int i = 0, rows = renderer.getRows(); i < rows; i++) {
                    pos.add(new SlotPos(i, column));
                }
                return pos;
            };
        });
        new DefaultSlotFiller("rectangle", "rectangle <row,col> <row,col> [solid]", input -> {
            SlotPos pos1 = ShopElement.parsePos(input[0]).get(0), pos2 = ShopElement.parsePos(input[1]).get(0);
            boolean solid = input.length == 3 && ("solid".equals(input[2]) || "true".equals(input[2]));
            return (element, renderer) -> {
                List<SlotPos> pos = new ArrayList<>();
                for (int i = 0, rows = renderer.getRows(); i < rows; i++) {
                    for (int j = 0, columns = renderer.getColumns(); j < columns; j++) {
                        if (i == pos1.getRow() || i == pos2.getRow() ||
                                j == pos1.getColumn() || j == pos2.getColumn() ||
                                (solid && i > pos1.getRow() && i < pos2.getRow() && j > pos1.getColumn() && j < pos2.getColumn()))
                            pos.add(new SlotPos(i, j));
                    }
                }
                return pos;
            };
        });
    }

    public static SlotFiller fromInput(String input) {
        input = input.toLowerCase(Locale.ROOT);
        String[] split = input.split(" ");
        DefaultSlotFiller filler = FILLERS.get(split[0]);
        if (filler == null)
            throw new IllegalArgumentException(input);
        try {
            return filler.fillerFactory.apply(Arrays.copyOfRange(split, 1, split.length));
        } catch (Exception e) {
            throw new IllegalArgumentException(filler.usage, e);
        }
    }
}
