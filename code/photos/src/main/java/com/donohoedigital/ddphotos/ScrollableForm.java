package com.donohoedigital.ddphotos;

import javax.swing.*;
import java.awt.*;

/** Form that fills the viewport width but scrolls horizontally once it can't shrink further. */
final class ScrollableForm extends JPanel implements Scrollable {
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
    @Override public boolean getScrollableTracksViewportWidth() {
        // Fill the viewport while it's wide enough; once it's narrower than our
        // minimum, stop shrinking and let the scroll pane add a horizontal bar.
        Component parent = getParent();
        return !(parent instanceof JViewport) || parent.getWidth() >= getMinimumSize().width;
    }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
