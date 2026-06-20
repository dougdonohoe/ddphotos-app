package com.donohoedigital.gui;

import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import java.awt.*;

/**
 * FlowLayout subclass that fully supports wrapping of components.
 * Extends FlowLayout to correctly recalculate preferred height based on container width.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout(
            @MagicConstant(intValues = {FlowLayout.LEFT, FlowLayout.CENTER, FlowLayout.RIGHT})
            int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    // FlowLayout always starts each row at insets.left + hgap, creating an unwanted leading
    // margin. We want hgap to mean the gap *between* items only, so after the super call we
    // shift every component left by hgap. Components at the start of a row land at insets.left;
    // inter-item gaps are unchanged because all components on the row move by the same amount.
    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        int shift = getHgap();
        if (shift == 0) return;
        Insets insets = target.getInsets();
        int minX = insets.left;
        for (Component m : target.getComponents()) {
            if (m.isVisible()) {
                Point loc = m.getLocation();
                m.setLocation(Math.max(minX, loc.x - shift), loc.y);
            }
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;

            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            // Only one hgap here (trailing), not two — layoutContainer removes the leading one.
            int horizontalInsetsAndGaps = insets.left + insets.right + hgap;
            int maxWidth = targetWidth - horizontalInsetsAndGaps;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);

                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                    if (rowWidth + d.width > maxWidth) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }

                    if (rowWidth > 0) {
                        rowWidth += hgap;
                    }

                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }

            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGaps;
            dim.height += insets.top + insets.bottom + (vgap * 2);

            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid()) {
                dim.width -= (hgap + 1);
            }

            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);

        if (dim.height > 0) {
            dim.height += getVgap();
        }

        dim.height += rowHeight;
    }
}
