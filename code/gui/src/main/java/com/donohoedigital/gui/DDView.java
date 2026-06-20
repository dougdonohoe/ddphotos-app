package com.donohoedigital.gui;

import javax.swing.text.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: sneth
 * Date: Mar 3, 2005
 * Time: 11:34:01 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DDView extends View
{
    public DDView(Element elem)
    {
        super(elem);
    }

    /**
     * Provides a mapping from the document model coordinate space
     * to the coordinate space of the view mapped to it.
     *
     * @param pos the position to convert
     * @param a the allocated region to render into
     * @return the bounding box of the given position
     * @see javax.swing.text.View#modelToView
     */
    public Shape modelToView(int pos, Shape a, Position.Bias b)
    {
        int p0 = getStartOffset();
        int p1 = getEndOffset();
        if ((pos >= p0) && (pos <= p1))
        {
            Rectangle r = a.getBounds();
            if (pos == p1) {
                r.x += r.width;
            }
            r.width = 0;
            return r;
        }
        return null;
    }

    /**
     * Provides a mapping from the view coordinate space to the logical
     * coordinate space of the model.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param a the allocated region to render into
     * @return the location within the model that best represents the
     *  given point of view
     * @see javax.swing.text.View#viewToModel
     */
    public int viewToModel(float x, float y, Shape a, Position.Bias[] bias)
    {
        Rectangle alloc = (Rectangle) a;
        if (x < alloc.x + alloc.width) {
            bias[0] = Position.Bias.Forward;
            return getStartOffset();
        }
        bias[0] = Position.Bias.Backward;
        return getEndOffset();
    }
}
