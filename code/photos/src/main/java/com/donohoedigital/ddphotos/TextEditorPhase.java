package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.EngineConstants;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.AppEngine;
import com.donohoedigital.app.engine.BasePhase;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.TextFile;
import com.donohoedigital.ddphotos.config.TextFileException;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Standalone resizable window for editing one of a site's plain-text config files - a
 * {@link TextFile} shown whole in a text area, with the file's path and a button that opens its
 * folder in the OS file manager.  {@link CssEditorPhase} is the first subclass; {@code site.env} is
 * expected to be the next.
 *
 * <p>These files are for advanced users who already have an editor they like, so this is a plain
 * text box - no syntax coloring, no gutter - with undo/redo courtesy of {@link DDTextArea}.
 *
 * <p>One window per site per file type, keyed in {@link #OPEN}: {@link #open} focuses an existing
 * window rather than opening a duplicate.  Each window gets its own name so the engine remembers
 * its size and position separately.
 */
public abstract class TextEditorPhase extends BasePhase {

    private static final Logger logger = LogManager.getLogger(TextEditorPhase.class);

    protected static final String STYLE = "TextEditor";

    /** Monospaced and bold, so the file being edited reads as the filename it is. */
    private static final String PATH_STYLE = "TextEditorPath";

    public static final String PARAM_SITE = "site";
    static final String PARAM_KEY = "key";

    /** One open editor per site per phase (key = phaseName/siteId); prevents duplicate windows. */
    private static final Map<String, TextEditorPhase> OPEN = new HashMap<>();

    protected Site site_;
    private String key_;

    private TextFile file_;
    private String originalText_ = "";

    private boolean built_;

    // Held so finish() can unregister it; quitting the app must not discard edits silently either.
    private AppEngine.CloseListener quitGuard_;

    // Watches the file being edited; closed in finish() alongside the quit guard.
    private ConfigWatcher.Registration watch_;

    private LogoWindowPanel base_;
    private DDLabel siteLabel_;
    private DDLabel pathLabel_;
    private DDTextArea text_;
    private DDHtmlArea helptext_;
    private EditorButtonBar buttons_;

    // -------------------------------------------------------------------------
    // Launch / single-instance
    // -------------------------------------------------------------------------

    /**
     * Opens (or focuses the existing) editor window.
     *
     * @param phaseName  the appdef.xml phase to run
     * @param windowName this window's identity, already unique - it names the window and is what
     *                   its remembered size and position are keyed off, so it must be 1:1 with
     *                   {@link #OPEN}'s key: the engine builds a context per window name, and
     *                   {@code OPEN} is the only thing keeping a second one from being created
     * @param title      the window title
     * @param params     subclass params to hand the phase; the launch params are added here
     */
    protected static void open(AppContext context, Site site, String phaseName,
                               String windowName, String title, TypedHashMap params) {
        if (context == null || site == null) return;
        String key = phaseName + "/" + windowName;
        TextEditorPhase existing = OPEN.get(key);
        if (existing != null && existing.context_ != null) {
            existing.context_.getWindow().toFront();
            return;
        }
        params.setObject(PARAM_SITE, site);
        params.setString(PARAM_KEY, key);
        params.setString(EngineConstants.PARAM_WINDOW_NAME, windowName);
        params.setString(EngineConstants.PARAM_WINDOW_TITLE, title);
        context.processPhase(phaseName, params);
    }

    // -------------------------------------------------------------------------
    // Subclass hooks
    // -------------------------------------------------------------------------

    /** The file to edit, freshly loaded.  May return null when it cannot be resolved. */
    protected abstract TextFile openFile();

    /** HTML explaining what this file is for, shown above the text area. */
    protected abstract String instructionsHtml();

    /** Widget name for the text area, so styles and help text can be per-file. */
    protected abstract String textAreaName();

    /**
     * Persists {@link #getFile()} and anything that has to follow it.  Returns false when the save
     * failed, in which case the editor stays dirty and open.  Errors are reported by the subclass.
     */
    protected abstract boolean saveFile();

    /**
     * Whether a successful save should remind the user to run {@code photogen}.  True for files
     * photogen reads (the stylesheet); false for those it never sees, like {@code site.env}.
     */
    protected boolean needsPhotogen() { return false; }

    protected TextFile getFile() { return file_; }

    // -------------------------------------------------------------------------
    // Phase lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void start() {
        if (!built_) {
            site_ = (Site) phase_.getObject(PARAM_SITE);
            key_ = phase_.getString(PARAM_KEY, null);
            if (key_ != null) OPEN.put(key_, this);
            buildUI();
            quitGuard_ = this::confirmDiscard;
            AppEngine.getAppEngine().addCloseListener(quitGuard_);
            built_ = true;
        }
        context_.setMainUIComponent(this, base_, true, text_);
        context_.getWindow().setHelpTextWidget(helptext_);
        context_.getWindow().showHelp(siteLabel_); // init help
    }

    @Override
    public void finish() {
        if (key_ != null && OPEN.get(key_) == this) {
            OPEN.remove(key_);
        }
        if (quitGuard_ != null) {
            AppEngine.getAppEngine().removeCloseListener(quitGuard_);
            quitGuard_ = null;
        }
        if (watch_ != null) {
            watch_.close();
            watch_ = null;
        }
        super.finish();
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        file_ = openFile();
        originalText_ = file_ != null ? file_.getContent() : "";

        base_ = new EditorWindowPanel(buildTopBar());
        helptext_ = base_.getHelpText();

        DDPanel center = new DDPanel();
        base_.setCenterComponent(center);
        center.add(buildHeader(), BorderLayout.NORTH);

        text_ = new DDTextArea(textAreaName(), STYLE);
        text_.setText(originalText_);
        text_.setCaretPosition(0);
        // A config file's own line breaks are meaningful; wrapping them would misrepresent it.
        text_.setLineWrap(false);
        // Tab indents, it doesn't leave the field - this is an editor, not a form.
        text_.setTabChangesFocus(false);
        // Likewise, focus lands in the file rather than selecting all of it.
        text_.setSelectAllOnFocus(false);
        text_.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { onDirtyChanged(); }
            public void removeUpdate(DocumentEvent e)  { onDirtyChanged(); }
            public void changedUpdate(DocumentEvent e) { onDirtyChanged(); }
        });

        DDScrollPane scroll = new DDScrollPane(text_, STYLE, DDScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                DDScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        // FlatLaf draws a text area's border (and focus ring) from its scroll pane, not the area;
        // DDScrollPane clears that border, so restore it here.
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        text_.setScrollPane(scroll);
        center.add(scroll, BorderLayout.CENTER);

        buttons_ = new EditorButtonBar(STYLE, this::onCancel, this::onSave, this::onSaveClose, this::onClose);
        center.add(buttons_, BorderLayout.SOUTH);

        // These windows stay open for long stretches holding the whole file in memory, so they
        // have the same exposure to an outside edit that albums.yaml does.
        watch_ = ConfigWatcher.watch(this::getFile, this::onFileChangedOnDisk);

        onDirtyChanged();
    }

    // -------------------------------------------------------------------------
    // External changes
    // -------------------------------------------------------------------------

    /**
     * The file being edited was rewritten by something else.  Untouched, the window just picks the
     * new text up; with edits pending the user chooses, and the log records which way it went.
     * Declining leaves the file changed on disk, which {@link #save()} then asks about.
     */
    private void onFileChangedOnDisk() {
        if (file_ == null) return;
        if (isDirty() && !ExternalChange.confirmDiscard(context_, file_.getPath())) {
            return;
        }
        reloadFromDisk();
        ExternalChange.logReloaded(file_.getPath());
    }

    /** Re-reads the file and re-baselines the text area against it, keeping the caret where it can. */
    private void reloadFromDisk() {
        file_.load();
        // load() is a no-op for a file that has been deleted, and would leave the model still
        // reading as changed - and so re-reported every tick.  Stamp whatever is there now,
        // including nothing, keeping the text we already have.
        file_.restamp();
        originalText_ = file_.getContent();
        int caret = text_.getCaretPosition();
        text_.setText(originalText_);
        text_.setCaretPosition(Math.min(caret, originalText_.length()));
        showPath();
        onDirtyChanged();
    }

    /** The widget row to the right of the logo; the logo and its insets come from LogoWindowPanel. */
    private JComponent buildTopBar() {
        DDPanel widgets = new DDPanel();
        widgets.setLayout(new BoxLayout(widgets, BoxLayout.X_AXIS));
        siteLabel_ = new DDLabel("texteditorsite", STYLE);
        siteLabel_.setText("<html>" + PhotosUtils.siteLabelHtml(site_) + "</html>");
        widgets.add(siteLabel_);
        widgets.add(Box.createHorizontalGlue());
        return widgets;
    }

    /** Instructions plus the file path and its reveal button. */
    private JComponent buildHeader() {
        DDPanel header = new DDPanel();
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        DDHtmlArea instructions = new DDHtmlArea("texteditorinstruct", STYLE);
        instructions.setText(instructionsHtml());
        instructions.setDisplayOnly(true);
        // DDHtmlArea builds itself a lowered bevel border; this is body text, not a widget.
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        header.add(instructions, BorderLayout.NORTH);

        DDPanel pathRow = new DDPanel();
        pathRow.setBorderLayoutGap(0, 8);
        pathRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        DDButton revealBtn = DDIconButtons.iconButton("texteditorreveal", STYLE, DDIconButtons.EXTERNAL_LINK);
        revealBtn.addActionListener(_ -> revealFolder());
        pathRow.add(revealBtn, BorderLayout.WEST);

        pathLabel_ = new DDLabel("texteditorpath", PATH_STYLE);
        showPath();
        // A JLabel's minimum size is its preferred size, so a long path would stop the window
        // shrinking.  Let it be squeezed instead - Swing ellipsis-izes what doesn't fit, and the
        // tooltip still has the whole thing.
        pathLabel_.setMinimumSize(new Dimension(60, pathLabel_.getPreferredSize().height));
        pathRow.add(pathLabel_, BorderLayout.CENTER);

        header.add(pathRow, BorderLayout.CENTER);
        return header;
    }

    private Path filePath() {
        return file_ != null ? file_.getPath() : null;
    }

    private void showPath() {
        String path = filePath() != null ? Objects.requireNonNull(filePath()).toString() : "";
        pathLabel_.setText(path);
    }

    /**
     * Opens the file's folder in the OS file manager.  The folder rather than the file itself:
     * {@link Utils#openFolder} can't select a file, and the file may not exist yet.
     */
    private void revealFolder() {
        Path path = filePath();
        Path dir = path != null ? path.getParent() : null;
        if (dir == null) return;
        if (!Utils.openFolder(dir.toFile())) {
            EngineUtils.displayErrorDialog(context_,
                    PropertyConfig.getMessage("msg.error.openfolder", dir.toString()));
        }
    }

    // -------------------------------------------------------------------------
    // Buttons / dirty state
    // -------------------------------------------------------------------------

    private void onDirtyChanged() {
        buttons_.setDirty(isDirty());
    }

    private boolean isDirty() {
        return file_ != null && !text_.getText().equals(originalText_);
    }

    private void onSave() {
        save();
    }

    private void onSaveClose() {
        if (save()) context_.close();
    }

    private void onCancel() {
        if (confirmDiscard()) context_.close();
    }

    /** Only enabled when nothing is pending, so there is nothing to confirm. */
    private void onClose() {
        context_.close();
    }

    /** True when it's safe to close: nothing pending, or the user confirmed the discard. */
    private boolean confirmDiscard() {
        return !isDirty() || EngineUtils.displayConfirmationDialog(context_,
                PropertyConfig.getMessage("msg.confirm.texteditor.discard"));
    }

    /** The window X and Cmd-W land here - same discard confirmation as Cancel. */
    @Override
    public boolean okayToClose() {
        return confirmDiscard();
    }

    private boolean save() {
        if (file_ == null) return false;
        if (!okayToOverwrite()) return false;
        file_.setContent(text_.getText());
        if (!saveFile()) return false;

        // The model may have been tidied on the way to disk (a trailing newline added to a new
        // file), so take what was actually written as the new baseline.
        originalText_ = file_.getContent();
        if (!originalText_.equals(text_.getText())) {
            int caret = text_.getCaretPosition();
            text_.setText(originalText_);
            text_.setCaretPosition(Math.min(caret, originalText_.length()));
        }
        showPath();
        onDirtyChanged();
        // Save & Close shows this before the window goes away, so the reminder isn't missed.
        if (needsPhotogen()) PhotosUtils.showPhotogenReminder(context_);
        return true;
    }

    /**
     * Confirms writing over a file something else has rewritten since it was read.  Answering no
     * abandons the save and leaves the window exactly as it was, still dirty.
     */
    private boolean okayToOverwrite() {
        if (!file_.isChangedOnDisk()) return true;
        return ExternalChange.confirmOverwrite(context_, file_.getPath());
    }

    /** Reports a failed write against the file being edited. */
    protected void showSaveError(TextFileException e) {
        logger.error("Failed to save {}", filePath(), e);
        PhotosUtils.showSaveError(context_, filePath(), e);
    }
}
