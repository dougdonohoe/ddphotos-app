package com.donohoedigital.zydeco;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class NewAlbumDialog extends JDialog
{
    private JTextField idField;
    private JTextField siteNameField;
    private JTextField siteUrlField;
    private JTextArea  siteDescriptionArea;
    private JTextField copyrightOwnerField;
    private JSpinner   copyrightYearSpinner;
    private JCheckBox  allowCrawlingCheck;
    private JTextField descriptionsField;
    private JTextArea  siteTitleHtmlArea;
    private JTextArea  siteSubtitleHtmlArea;
    private JTextArea  siteOverviewHtmlArea;
    private JLabel     errorLabel;

    private AlbumSettings result;

    public NewAlbumDialog(Frame owner, AlbumSettings initial)
    {
        super(owner, "New Album", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI();
        populate(initial);
    }

    public AlbumSettings getResult()
    {
        return result;
    }

    // -------------------------------------------------------------------------
    // Pre-population
    // -------------------------------------------------------------------------

    private void populate(AlbumSettings s)
    {
        if (s == null) return;
        idField.setText(s.id());
        siteNameField.setText(s.siteName());
        siteUrlField.setText(s.siteUrl());
        siteDescriptionArea.setText(s.siteDescription());
        copyrightOwnerField.setText(s.copyrightOwner());
        copyrightYearSpinner.setValue(s.copyrightYear());
        allowCrawlingCheck.setSelected(s.allowCrawling());
        descriptionsField.setText(s.descriptions());
        siteTitleHtmlArea.setText(s.siteTitleHtml());
        siteSubtitleHtmlArea.setText(s.siteSubtitleHtml());
        siteOverviewHtmlArea.setText(s.siteOverviewHtml());
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUI()
    {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(4, 4, 4, 4));

        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0;
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.weightx = 1.0;
        sc.insets = new Insets(0, 0, 8, 0);

        sc.gridy = 0; form.add(buildBasicSection(), sc);
        sc.gridy = 1; form.add(buildCopyrightSection(), sc);
        sc.gridy = 2; form.add(buildHtmlSection(), sc);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0; filler.gridy = 3;
        filler.weighty = 1.0; filler.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createGlue(), filler);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        errorLabel = new JLabel(" ");
        errorLabel.setForeground(Color.RED.darker());
        errorLabel.setBorder(new EmptyBorder(2, 0, 4, 0));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(_ -> dispose());

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(_ -> {
            if (validateForm()) {
                result = buildResult();
                dispose();
            }
        });
        getRootPane().setDefaultButton(saveBtn);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelBtn);
        buttons.add(saveBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(new EmptyBorder(8, 0, 0, 0));
        south.add(errorLabel, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(scroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(640, 720);
        setLocationRelativeTo(getOwner());
    }

    private JPanel buildBasicSection()
    {
        JPanel panel = section("Settings");
        int row = 0;

        idField = new JTextField(30);
        row = addField(panel, "ID *", idField, row);

        siteNameField = new JTextField(30);
        row = addField(panel, "Site Name *", siteNameField, row);

        siteUrlField = new JTextField(30);
        row = addField(panel, "Site URL", siteUrlField, row);

        siteDescriptionArea = plainArea(3);
        row = addField(panel, "Site Description", scrolled(siteDescriptionArea), row);

        descriptionsField = new JTextField("descriptions.txt", 30);
        addField(panel, "Descriptions File", descriptionsField, row);

        return panel;
    }

    private JPanel buildCopyrightSection()
    {
        JPanel panel = section("Copyright & Crawling");
        int row = 0;

        copyrightOwnerField = new JTextField(30);
        row = addField(panel, "Copyright Owner", copyrightOwnerField, row);

        int currentYear = Year.now().getValue();
        copyrightYearSpinner = new JSpinner(new SpinnerNumberModel(currentYear, 1800, currentYear, 1));
        copyrightYearSpinner.setEditor(new JSpinner.NumberEditor(copyrightYearSpinner, "#"));
        row = addField(panel, "Copyright Year", copyrightYearSpinner, row);

        allowCrawlingCheck = new JCheckBox();
        allowCrawlingCheck.setSelected(true);
        addField(panel, "Allow Crawling", allowCrawlingCheck, row);

        return panel;
    }

    private JPanel buildHtmlSection()
    {
        JPanel panel = section("HTML Content");
        int row = 0;

        siteTitleHtmlArea = htmlArea(2);
        row = addField(panel, "Site Title HTML", scrolled(siteTitleHtmlArea), row);

        siteSubtitleHtmlArea = htmlArea(2);
        row = addField(panel, "Site Subtitle HTML", scrolled(siteSubtitleHtmlArea), row);

        siteOverviewHtmlArea = htmlArea(4);
        addField(panel, "Site Overview HTML", scrolled(siteOverviewHtmlArea), row);

        return panel;
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    private static JPanel section(String title)
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static int addField(JPanel panel, String label, Component field, int row)
    {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row;
        lc.anchor = GridBagConstraints.NORTHEAST;
        lc.insets = new Insets(5, 8, 5, 8);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(4, 0, 4, 8);

        panel.add(new JLabel(label), lc);
        panel.add(field, fc);
        return row + 1;
    }

    private static JTextArea plainArea(int rows)
    {
        JTextArea area = new JTextArea(rows, 30);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static JTextArea htmlArea(int rows)
    {
        JTextArea area = plainArea(rows);
        Font labelFont = UIManager.getFont("Label.font");
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN,
                labelFont != null ? labelFont.getSize() : 13));
        return area;
    }

    private static JScrollPane scrolled(JTextArea area)
    {
        return new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    // -------------------------------------------------------------------------
    // Result
    // -------------------------------------------------------------------------

    private AlbumSettings buildResult()
    {
        return new AlbumSettings(
                idField.getText().trim(),
                siteNameField.getText().trim(),
                siteUrlField.getText().trim(),
                siteDescriptionArea.getText().trim(),
                copyrightOwnerField.getText().trim(),
                ((Number) copyrightYearSpinner.getValue()).intValue(),
                allowCrawlingCheck.isSelected(),
                descriptionsField.getText().trim(),
                siteTitleHtmlArea.getText().trim(),
                siteSubtitleHtmlArea.getText().trim(),
                siteOverviewHtmlArea.getText().trim()
        );
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private boolean validateForm()
    {
        List<String> errors = new ArrayList<>();

        String id = idField.getText().trim();
        if (id.isEmpty()) {
            errors.add("ID is required");
            markError(idField);
        } else if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]*")) {
            errors.add("ID must start with a letter or digit and contain only letters, digits, hyphens, or underscores");
            markError(idField);
        } else {
            clearError(idField);
        }

        String name = siteNameField.getText().trim();
        if (name.isEmpty()) {
            errors.add("Site Name is required");
            markError(siteNameField);
        } else {
            clearError(siteNameField);
        }

        String url = siteUrlField.getText().trim();
        if (!url.isEmpty() && !isValidUrl(url)) {
            errors.add("Site URL must be a valid http or https URL");
            markError(siteUrlField);
        } else {
            clearError(siteUrlField);
        }

        int maxYear = Year.now().getValue();
        int year = ((Number) copyrightYearSpinner.getValue()).intValue();
        if (year > maxYear) {
            errors.add("Copyright Year cannot be in the future");
            markError(copyrightYearSpinner);
        } else {
            clearError(copyrightYearSpinner);
        }

        if (errors.isEmpty()) {
            errorLabel.setText(" ");
            return true;
        }

        errorLabel.setText("<html>• " + String.join("<br>• ", errors) + "</html>");
        return false;
    }

    private static boolean isValidUrl(String url)
    {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void markError(JComponent c)
    {
        c.putClientProperty("JComponent.outline", "error");
    }

    private static void clearError(JComponent c)
    {
        c.putClientProperty("JComponent.outline", null);
    }
}
