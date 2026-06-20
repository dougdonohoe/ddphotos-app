package com.donohoedigital.zydeco;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone playground for checking whether a font can render console-style
 * output containing emoji and box-drawing characters - e.g. the RobotoMono
 * font used for the "ConsoleBig" style in DD Photos. Pure Swing, no
 * DD widgets, so the only thing under test is the font itself.
 */
public class ZydecoFontApp extends JFrame
{
    private static final String ROBOTO_MONO_NAME = "RobotoMono Regular (bundled .ttf)";

    // candidate paths depending on where `mvn exec:java` (or the IDE) sets the working directory
    private static final String[] ROBOTO_MONO_PATHS = {
            "../gui/src/main/resources/config/fonts/RobotoMono-Regular.ttf",
            "code/gui/src/main/resources/config/fonts/RobotoMono-Regular.ttf",
            "gui/src/main/resources/config/fonts/RobotoMono-Regular.ttf",
    };

    private static final String SAMPLE_TEXT = """
            ⛅️ wrangler 4.95.0
            ───────────────────
            Getting User settings...
            👋 You are logged in with an OAuth Token, associated with the email doug@donohoe.info.
            ┌─────────────────────────────┬──────────────────────────────────┐
            │ Account Name                │ Account ID                       │
            ├─────────────────────────────┼──────────────────────────────────┤
            │ Doug@donohoe.info's Account │ 66f3ab2c085e331beef66fdec63f8d67 │
            └─────────────────────────────┴──────────────────────────────────┘
            🔓 Token Permissions:
            Scope (Access)
            - account (read)
            [and more]""";

    private final Map<String, Font> fontsByName = new LinkedHashMap<>();
    private final JComboBox<String> fontCombo = new JComboBox<>();
    private final JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(18, 6, 72, 1));
    private final JCheckBox boldCheck = new JCheckBox("Bold", true);
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea textArea = new JTextArea(SAMPLE_TEXT);

    public ZydecoFontApp()
    {
        super("Zydeco Font Test");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 600);
        setLocationRelativeTo(null);

        loadFonts();

        textArea.setLineWrap(false);
        textArea.setTabSize(4);
        JScrollPane scroll = new JScrollPane(textArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        setLayout(new BorderLayout());
        add(buildControls(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        applyFont();
    }

    private JComponent buildControls()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));

        panel.add(new JLabel("Font:"));
        for (String name : fontsByName.keySet()) {
            fontCombo.addItem(name);
        }
        fontCombo.addActionListener(_ -> applyFont());
        panel.add(fontCombo);

        panel.add(new JLabel("Size:"));
        sizeSpinner.addChangeListener(_ -> applyFont());
        panel.add(sizeSpinner);

        boldCheck.addActionListener(_ -> applyFont());
        panel.add(boldCheck);

        return panel;
    }

    private void loadFonts()
    {
        Font roboto = loadRobotoMono();
        if (roboto != null) {
            fontsByName.put(ROBOTO_MONO_NAME, roboto);
        }

        fontsByName.put("Monospaced", new Font(Font.MONOSPACED, Font.PLAIN, 12));

        for (String family : detectMonospacedFamilies()) {
            fontsByName.put(family, new Font(family, Font.PLAIN, 12));
        }
    }

    private Font loadRobotoMono()
    {
        for (String path : ROBOTO_MONO_PATHS) {
            File file = new File(path);
            if (!file.isFile()) {
                continue;
            }
            try (FileInputStream is = new FileInputStream(file)) {
                return Font.createFont(Font.TRUETYPE_FONT, is);
            } catch (IOException | FontFormatException e) {
                System.err.println("Failed to load font from " + file.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        System.err.println("RobotoMono-Regular.ttf not found; tried: " + String.join(", ", ROBOTO_MONO_PATHS));
        return null;
    }

    /**
     * Finds installed font families that look fixed-width by comparing the
     * advance widths of a narrow and a wide glyph.
     */
    private static List<String> detectMonospacedFamilies()
    {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        try {
            List<String> families = new ArrayList<>();
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                Font font = new Font(family, Font.PLAIN, 14);
                if (!font.canDisplay('i') || !font.canDisplay('M')) {
                    continue;
                }
                FontMetrics metrics = g2d.getFontMetrics(font);
                int narrow = metrics.charWidth('i');
                int wide = metrics.charWidth('M');
                if (narrow > 0 && narrow == wide) {
                    families.add(family);
                }
            }
            return families;
        } finally {
            g2d.dispose();
        }
    }

    private void applyFont()
    {
        String name = (String) fontCombo.getSelectedItem();
        Font base = fontsByName.get(name);
        if (base == null) {
            return;
        }

        int size = (Integer) sizeSpinner.getValue();
        int style = boldCheck.isSelected() ? Font.BOLD : Font.PLAIN;
        Font font = base.deriveFont(style, (float) size);
        textArea.setFont(font);

        FontMetrics metrics = textArea.getFontMetrics(font);
        boolean monospaced = metrics.charWidth('i') == metrics.charWidth('M');
        statusLabel.setText(String.format(
                " %s, %dpt%s  -  glyph widths %s  -  can display test glyphs: %s",
                font.getFamily(), size, boldCheck.isSelected() ? " bold" : "",
                monospaced ? "match (monospaced)" : "DIFFER (not monospaced)",
                canDisplayTestGlyphs(font) ? "yes" : "no"));
    }

    private static boolean canDisplayTestGlyphs(Font font)
    {
        return font.canDisplay(0x26C5)   // ⛅ cloud with sun
                && font.canDisplay(0x1F44B) // 👋 waving hand
                && font.canDisplay(0x250C)  // ┌ box drawing corner
                && font.canDisplay(0x2500); // ─ box drawing horizontal
    }

    static void main()
    {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Zydeco Font Test");

        FlatLightLaf.setup();

        SwingUtilities.invokeLater(() -> new ZydecoFontApp().setVisible(true));
    }
}
