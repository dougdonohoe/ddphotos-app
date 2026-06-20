package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.util.*;

public class TextUtil 
{
    public double x;
    public double y;
    public double width;
    public double lineHeight;
    public double totalHeight;
    public double lineSpacing;
    public int numLines;
    public String sText;
    public Font fFont;
    public Graphics2D g;
    public FontRenderContext frc;
    public LineMetrics metrics;

    public static final char SEP = '\n';
    public static final String SEPS = "\n";
    public static final String A2Z = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    /**
     * Create metrics using default line spacing of 1.5f
     */
    public TextUtil(Graphics2D g, Font fFont, String sText)
    {
        this(g, fFont, sText, 0.0f);
    }
    
    /**
     * Create metrics for drawing given string
     */
    public TextUtil(Graphics2D g, Font fFont, String sText, float fLineSpacing)
    {
        this.g = g;
        this.fFont = fFont;
        this.sText = sText;
       
        Font oldFont = g.getFont();
        g.setFont(fFont);
        frc = g.getFontRenderContext();
        
        // figure out number of lines we are drawing
        numLines = 1;
        for (int i = 0; i < sText.length(); i++)
        {
            if (sText.charAt(i) == SEP) numLines++;
        }
        metrics = g.getFont().getLineMetrics(A2Z, frc);
        lineHeight = metrics.getDescent() + (metrics.getAscent() * .75f); // adjust for space above chars for accents and stuff
        lineSpacing = lineHeight * fLineSpacing;

        totalHeight = (lineHeight * numLines) + ((numLines - 1) * lineSpacing);
        //float starty = (float) (y - nTotalHeight/2) + nHeight; // add height once to adjust

        Rectangle2D stringbounds;
        width = 0.0f;

        // perf improvement - don't use tokenizer if not multiple lines
        if (numLines > 1) 
        {
            String sLine;
            StringTokenizer st = new StringTokenizer(sText, SEPS);
        
            while (st.hasMoreTokens())
            {
                sLine = st.nextToken();
                stringbounds = g.getFont().getStringBounds(sLine, frc);
                width = Math.max(width, (float)stringbounds.getWidth());
            }
        }
        else
        {
            stringbounds = g.getFont().getStringBounds(sText, frc);
            width = (float)stringbounds.getWidth();
        }
        
        g.setFont(oldFont);
    }
}
