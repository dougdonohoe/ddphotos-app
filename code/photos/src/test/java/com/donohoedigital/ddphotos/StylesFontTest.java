package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.StylesConfig;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_OFF;
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Guards the font choice in {@code styles.xml}: every font must be a system/logical family
 * (SansSerif, Monospaced, Dialog), never a bundled {@code .ttf}.
 *
 * <p>Swing's text views locate the caret using {@code FontMetrics} taken from an
 * identity-transform {@link FontRenderContext}, but the glyphs are painted through the
 * {@code Graphics2D} device transform - 2x on a Retina display.  A font loaded from a bundled
 * {@code .ttf} via {@code Font.createFont()} quantizes its advance widths against the *device*
 * pixel grid, so the width Swing measures and the width Java2D paints disagree, and the error
 * accumulates left-to-right.  The result is a caret that drifts further from the text the longer
 * the text gets.  Bundled Inter at 15pt drifted ~20px - about 3 characters - over an 82-char
 * caption, and no point size avoids it (measured 9-41px across sizes 11-24).
 *
 * <p>Only widgets with a caret can show the drift, but the rule is enforced for every font so
 * that a bundled face cannot creep back in via a label style and later get reused on an input.
 *
 * <p>This test discovers the fonts from {@code styles.xml} rather than hard-coding them, so a
 * newly added style is covered automatically.
 *
 * <p><b>The measurement check is macOS-only.</b>  The immunity of logical families comes from
 * CoreText, which quantizes advances in user space and so ignores the device transform.  Linux
 * and Windows have no CoreText - every font goes through the FreeType scaler, which is the same
 * path a bundled {@code .ttf} takes on macOS.  Measured on Linux (temurin 25), <em>every</em>
 * logical family drifts: SansSerif 26-36px, Serif 25-29px, Monospaced 27-59px.  No font choice
 * fixes it there, so asserting zero drift off macOS would be a permanent, unactionable CI
 * failure.  Note this means HiDPI Linux/Windows users can still see caret drift; only a custom
 * {@code TextUI} that measures at the device scale would fix that.  (There is no drift at all at
 * 100% scaling, which is the common case on those platforms.)
 */
public class StylesFontTest {

    /** Long enough that per-glyph rounding error accumulates into something visible. */
    private static final String SAMPLE =
            "Sunset over the Golden Gate Bridge, taken from the Marin Headlands in October 2024";

    /** Display scales to check: 1x, the common Windows fractional steps, Retina, and 3x. */
    private static final double[] SCALES = {1.0, 1.25, 1.5, 1.75, 2.0, 3.0};

    /** Sub-pixel tolerance - anything at or below this is invisible to the eye. */
    private static final double TOLERANCE = 0.5;

    private static final String STYLES = "/config/ddphotos/styles.xml";

    @BeforeClass
    public static void loadStyles() {
        new StylesConfig(new String[]{"common", "ddphotos"});
    }

    @Test
    public void everyStyleFontMeasuresTheSameAtEveryDisplayScale() throws Exception {
        // See the class javadoc: only macOS has a font path that can satisfy this.  Every family
        // drifts on Linux/Windows regardless of what styles.xml says, so this would be a
        // permanent CI failure there rather than a signal about our font choice.
        Assume.assumeTrue("measurement check is macOS-only (no CoreText elsewhere)", Utils.ISMAC);

        List<String> names = fontStyleNames();
        assertFalse("no <font> entries found in " + STYLES, names.isEmpty());

        for (String name : names) {
            Font font = StylesConfig.getFont(name);
            assertNotNull("font not loaded: " + name, font);

            double model = width(font, null);
            for (double scale : SCALES) {
                double device = width(font, AffineTransform.getScaleInstance(scale, scale));
                assertEquals(
                        name + " (" + font.getFontName() + " " + font.getSize() + "pt) measures "
                        + model + "px but renders " + device + "px at " + scale + "x, so glyphs "
                        + "will not sit where Swing thinks they do - carets and selection in "
                        + "editable fields drift.  styles.xml needs a system/logical font family "
                        + "(SansSerif, Monospaced), not a bundled .ttf.",
                        model, device, TOLERANCE);
            }
        }
    }

    /** The bundled-font directory is gone; nothing should reference a .ttf any more. */
    @Test
    public void noStyleReferencesABundledFontFile() throws Exception {
        for (Element font : fontElements()) {
            String fontname = font.getAttribute("fontname");
            assertFalse(font.getAttribute("name") + " references bundled font file '" + fontname
                        + "' - use a system/logical family instead (see the FONTS note in "
                        + STYLES + ")",
                    fontname.toLowerCase().endsWith(".ttf"));
        }
    }

    /** Width of {@link #SAMPLE} as measured through {@code tx} (null = Swing's identity FRC). */
    private static double width(Font font, AffineTransform tx) {
        FontRenderContext frc =
                new FontRenderContext(tx, VALUE_TEXT_ANTIALIAS_ON, VALUE_FRACTIONALMETRICS_OFF);
        return font.getStringBounds(SAMPLE, frc).getWidth();
    }

    /** The {@code name} of every {@code <font>} declared in styles.xml. */
    private static List<String> fontStyleNames() throws Exception {
        List<String> names = new ArrayList<>();
        for (Element font : fontElements()) names.add(font.getAttribute("name"));
        return names;
    }

    /** Every {@code <font>} element in styles.xml, read straight from the resource. */
    private static List<Element> fontElements() throws Exception {
        List<Element> elements = new ArrayList<>();
        try (InputStream in = StylesFontTest.class.getResourceAsStream(STYLES)) {
            assertNotNull("missing resource " + STYLES, in);
            NodeList fonts = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(in).getElementsByTagName("font");
            for (int i = 0; i < fonts.getLength(); i++) elements.add((Element) fonts.item(i));
        }
        return elements;
    }
}
