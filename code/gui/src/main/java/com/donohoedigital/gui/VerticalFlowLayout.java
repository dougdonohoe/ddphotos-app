package com.donohoedigital.gui;

import java.awt.*;

/**
 * A vertical flow layout where each component assumes its natural (preferred) size.
 */
public class VerticalFlowLayout implements LayoutManager, java.io.Serializable {

    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int TOP = 3;
    public static final int BOTTOM = 4;
    public static final int FILL = 5;

    int align;
    int subAlign;
    int hgap;
    int vgap;

    public VerticalFlowLayout(int align, int hgap, int vgap, int subAlign) {
        this.align = align;
        this.hgap = hgap;
        this.vgap = vgap;
        this.subAlign = subAlign;
    }

    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    public void addLayoutComponent(String name, Component comp) {
    }

    public void removeLayoutComponent(Component comp) {
    }

    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();
                    dim.width = Math.max(dim.width, d.width);
                    if (i > 0) {
                        dim.height += vgap;
                    }
                    dim.height += d.height;
                }
            }
            Insets insets = target.getInsets();
            dim.height += insets.top + insets.bottom;
            dim.width += insets.left + insets.right + hgap * 2;
            return dim;
        }
    }

    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getMinimumSize();
                    dim.width = Math.max(dim.width, d.width);
                    if (i > 0) {
                        dim.height += vgap;
                    }
                    dim.height += d.height;
                }
            }
            Insets insets = target.getInsets();
            dim.height += insets.top + insets.bottom;
            dim.width += insets.left + insets.right + hgap * 2;
            return dim;
        }
    }

    private void moveComponents(Container target, int x, int y, int width, int height, int columnStart, int columnEnd) {
        synchronized (target.getTreeLock()) {
            switch (align) {
                case TOP:
                    break;
                case CENTER:
                    y += height / 2;
                    break;
                case BOTTOM:
                    y += height;
                    break;
            }
            for (int i = columnStart; i < columnEnd; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    int dx = switch (subAlign) {
                        case CENTER -> (width - m.getSize().width) / 2;
                        case LEFT, FILL -> 0;
                        case RIGHT -> width - m.getSize().width;
                        default -> throw new IllegalStateException("subAlign = " + subAlign);
                    };
                    m.setLocation(x + dx, y);
                    y += vgap + m.getSize().height;
                }
            }
        }
    }

    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxheight = target.getSize().height - (insets.top + insets.bottom);
            int nmembers = target.getComponentCount();
            int y = 0, x = insets.left + hgap;
            int columnw = 0, start = 0;

            int fillWidth = target.getWidth() - insets.left - insets.right - hgap * 2;
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();
                    int mw = (subAlign == FILL) ? Math.max(1, fillWidth) : d.width;
                    m.setSize(mw, d.height);

                    if ((y == 0) || ((y + d.height) <= maxheight)) {
                        if (y > 0) {
                            y += vgap;
                        }
                        y += d.height;
                        columnw = Math.max(columnw, mw);
                    } else {
                        moveComponents(target, x, insets.top, columnw, maxheight - y, start, i);
                        y = d.height;
                        x += hgap + columnw;
                        columnw = mw;
                        start = i;
                    }
                }
            }
            moveComponents(target, x, insets.top, columnw, maxheight - y, start, nmembers);
        }
    }

    public String toString() {
        String str = switch (align) {
            case TOP -> ",align=top";
            case CENTER -> ",align=center";
            case BOTTOM -> ",align=bottom";
            default -> "";
        };
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + str + subAlign + "]";
    }
}

