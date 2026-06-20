package com.donohoedigital.gui;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.beans.*;
import java.io.*;
import java.util.function.*;
import java.util.regex.Pattern;

/**
 * An Option widget combining a text field and a browse-for-file button.
 * Mirrors the OptionText API plus file-chooser configuration.
 */
public class OptionFileChooser extends DDOption implements PropertyChangeListener {

    private final DDLabel label_;
    private final DDTextField text_;
    private final DDButton browseBtn_;
    private final String sDefault_;
    private final String requiredFilename_;
    private String requiredExtension_;
    private Supplier<String> startDirSupplier_;
    private UnaryOperator<String> pathProcessor_;
    private String chooserTitle_;
    private boolean directoryMode_ = false;

    /**
     * @param sPrefNode       prefs node (null for dummy/no-persist)
     * @param sName           option name key (used for label, default, help lookups)
     * @param sStyle          style name
     * @param map             TypedHashMap for value storage
     * @param nLengthLimit    max text length
     * @param nWidth          preferred width of the text field
     * @param requiredFilename if non-null, file chooser filters to this exact filename and the
     *                        field validates that the value ends with this name
     */
    public OptionFileChooser(String sPrefNode, String sName, String sStyle,
                             TypedHashMap map, int nLengthLimit, int nWidth,
                             String requiredFilename) {
        super(sPrefNode, sName, sStyle, map);
        sDefault_ = PropertyConfig.getRequiredStringProperty(getDefaultKey());
        requiredFilename_ = requiredFilename;

        setBorderLayoutGap(0, 8);

        text_ = new DDTextField(GuiManager.DEFAULT, STYLE);
        text_.setTextLengthLimit(nLengthLimit);
        if (requiredFilename != null) {
            text_.setRegExp("|(.*[/\\\\])?" + Pattern.quote(requiredFilename));
            text_.setCustomValidator(s -> s.isBlank() || new File(s.trim()).exists());
        } else {
            text_.setRegExp(".*");
        }
        resetToPrefs();
        saveToMap();
        Dimension pref = text_.getPreferredSize();
        text_.setPreferredSize(new Dimension(nWidth, pref.height));
        text_.addPropertyChangeListener("value", this);
        text_.addMouseListener(this);

        browseBtn_ = new DDButton("browsefile", STYLE);
        browseBtn_.addActionListener(_ -> onBrowse());
        DDIconButtons.makeFolderIcon(browseBtn_);

        label_ = new DDLabel(GuiManager.DEFAULT, STYLE);
        label_.setText(getLabel());
        label_.addMouseListener(this);

        DDPanel inner = new DDPanel();
        inner.setBorderLayoutGap(0, 4);
        inner.add(text_, BorderLayout.CENTER);
        inner.add(browseBtn_, BorderLayout.EAST);

        add(label_, BorderLayout.WEST);
        add(inner, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // Browse button configuration
    // -------------------------------------------------------------------------

    public void setBrowseButtonIcon(Icon icon) {
        browseBtn_.setIcon(icon);
    }

    public void setBrowseButtonText(String text) {
        browseBtn_.setText(text);
    }

    public void setBrowseButtonPreferredSize(Dimension d) {
        browseBtn_.setPreferredSize(d);
    }

    // -------------------------------------------------------------------------
    // File-chooser behavior
    // -------------------------------------------------------------------------

    /** Overrides the dialog title (default: "Choose {requiredFilename}" or "Choose a file/folder"). */
    public void setChooserTitle(String title) {
        chooserTitle_ = title;
    }

    /**
     * When true the chooser picks a directory instead of a file.
     * The text field validator is left unchanged; callers should set a custom validator
     * (e.g. {@code s -> s.isBlank() || Files.isDirectory(Path.of(s.trim()))}).
     */
    public void setDirectoryMode(boolean directoryMode) {
        directoryMode_ = directoryMode;
    }

    /**
     * Restricts the field and the chooser dialog to files with the given extension (e.g. "txt").
     * The regex is updated so only blank or values ending in ".{ext}" are valid.
     * Has no effect when a requiredFilename was already set in the constructor.
     */
    public void setFileExtensionFilter(String extension) {
        requiredExtension_ = extension;
        if (requiredFilename_ == null) {
            text_.setRegExp("|.*\\." + Pattern.quote(extension));
        }
    }

    /**
     * Supplier for the chooser's initial directory.  Called each time the dialog
     * opens.  When null, falls back to the parent directory of the current text value.
     */
    public void setStartDirSupplier(Supplier<String> supplier) {
        startDirSupplier_ = supplier;
    }

    /**
     * Post-processes the absolute path returned by the file chooser before it is
     * placed into the text field.  Useful for relativizing against a base directory.
     * When null, the absolute path is used as-is.
     */
    public void setPickedPathProcessor(UnaryOperator<String> processor) {
        pathProcessor_ = processor;
    }

    /** Delegates to the underlying DDTextField. */
    public void setCustomValidator(Predicate<String> validator) {
        text_.setCustomValidator(validator);
    }

    // -------------------------------------------------------------------------
    // Text access
    // -------------------------------------------------------------------------

    public DDTextField getTextField() {
        return text_;
    }

    public String getText() {
        return text_.getText();
    }

    public void setText(String value) {
        text_.setText(value);
    }

    // -------------------------------------------------------------------------
    // DDOption / DDValidatable implementation
    // -------------------------------------------------------------------------

    @Override
    public JComponent getLabelComponent() {
        return label_;
    }

    @Override
    public boolean isValidData() {
        return text_.isValidData();
    }

    @Override
    public void setDisplayOnly(boolean b) {
        text_.setDisplayOnly(b);
        browseBtn_.setEnabled(!b);
    }

    @Override
    public void setEnabled(boolean b) {
        text_.setEnabled(b);
        browseBtn_.setEnabled(b);
        label_.setEnabled(b);
    }

    @Override
    public boolean isEnabled() {
        return text_.isEnabled();
    }

    @Override
    public void resetToDefault() {
        text_.setText(sDefault_);
    }

    @Override
    public void resetToPrefs() {
        text_.setText(prefs_.get(sName_, sDefault_));
    }

    @Override
    public void resetToMap() {
        String value = map_.getString(sName_, sDefault_);
        if (!value.equals(text_.getText())) text_.setText(value);
    }

    @Override
    public void saveToMap() {
        map_.setString(sName_, text_.getText().trim());
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        fireStateChanged();
        if (!text_.isValidData()) return;
        prefs_.put(sName_, text_.getText().trim());
        saveToMap();
    }

    // -------------------------------------------------------------------------
    // Browse action
    // -------------------------------------------------------------------------

    private void onBrowse() {
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        String startDir;
        if (startDirSupplier_ != null) {
            startDir = startDirSupplier_.get();
        } else {
            String current = text_.getText().trim();
            if (current.isBlank()) {
                startDir = null;
            } else if (directoryMode_) {
                // current value is itself a directory — start there, not in its parent
                startDir = current;
            } else {
                startDir = new File(current).getParent();
            }
        }
        String title = chooserTitle_ != null ? chooserTitle_
                : directoryMode_ ? PropertyConfig.getMessage("msg.filechooser.default.folder")
                : requiredFilename_ != null
                        ? PropertyConfig.getMessage("msg.filechooser.default.named", requiredFilename_)
                        : PropertyConfig.getMessage("msg.filechooser.default.file");

        String chosen = pickFile(frame, startDir, title, requiredFilename_, requiredExtension_, directoryMode_);
        if (chosen == null) return;
        if (pathProcessor_ != null) chosen = pathProcessor_.apply(chosen);
        text_.setText(chosen);
    }

    private static String pickFile(Frame frame, String startDir, String title,
                                    String requiredFilename, String requiredExtension,
                                    boolean directoryMode) {
        if (startDir == null || startDir.isBlank()) startDir = System.getProperty("user.home");
        if (directoryMode) {
            if (Utils.ISMAC) {
                String prev = System.getProperty("apple.awt.fileDialogForDirectories", "false");
                System.setProperty("apple.awt.fileDialogForDirectories", "true");
                try {
                    FileDialog fd = new FileDialog(frame, title, FileDialog.LOAD);
                    fd.setDirectory(startDir);
                    fd.setVisible(true);
                    String dir  = fd.getDirectory();
                    String file = fd.getFile();
                    return file != null ? new File(dir, file).getAbsolutePath() : null;
                } finally {
                    System.setProperty("apple.awt.fileDialogForDirectories", prev);
                }
            } else {
                JFileChooser fc = new JFileChooser(startDir);
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.setFileHidingEnabled(false);
                fc.setDialogTitle(title);
                return fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION
                        ? fc.getSelectedFile().getAbsolutePath() : null;
            }
        }
        if (Utils.ISMAC) {
            FileDialog fd = new FileDialog(frame, title, FileDialog.LOAD);
            fd.setDirectory(startDir);
            if (requiredFilename != null) {
                fd.setFilenameFilter((_, name) -> name.equals(requiredFilename));
            } else if (requiredExtension != null) {
                String dot = "." + requiredExtension;
                fd.setFilenameFilter((_, name) -> name.endsWith(dot));
            }
            fd.setVisible(true);
            String dir  = fd.getDirectory();
            String file = fd.getFile();
            return file != null ? new File(dir, file).getAbsolutePath() : null;
        } else {
            JFileChooser fc = new JFileChooser(startDir);
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setFileHidingEnabled(false);
            fc.setDialogTitle(title);
            if (requiredFilename != null) {
                fc.setFileFilter(new FileFilter() {
                    @Override public boolean accept(File f) { return f.isDirectory() || f.getName().equals(requiredFilename); }
                    @Override public String getDescription() { return requiredFilename; }
                });
            } else if (requiredExtension != null) {
                String dot = "." + requiredExtension;
                fc.setFileFilter(new FileFilter() {
                    @Override public boolean accept(File f) { return f.isDirectory() || f.getName().endsWith(dot); }
                    @Override public String getDescription() { return "*" + dot; }
                });
            }
            return fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION
                    ? fc.getSelectedFile().getAbsolutePath() : null;
        }
    }
}
