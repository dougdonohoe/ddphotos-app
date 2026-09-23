package com.donohoedigital.ddphotos;

import com.donohoedigital.config.DataElement;
import com.donohoedigital.ddphotos.sync.SyncProvider;
import com.donohoedigital.ddphotos.sync.SyncProviders;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDComboBox;
import com.donohoedigital.gui.DDRadioButton;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * The album "Source Type" controls shared by {@link AlbumDialog} and {@link AlbumDetailPanel}:
 * <pre>(•) Local  ( ) Sync  [Immich ▾]  [Credentials...]</pre>
 * The provider combo and the Credentials button are enabled only for Sync.
 *
 * <p>The combo lists {@link SyncProviders#forUi()}.  An album can name a provider photogen accepts
 * but the app does not offer (the {@code mock} test provider); {@link #setProviderId} adds it to
 * the combo so the album still displays, and Credentials is disabled for it.
 */
final class SourceTypeRow extends JPanel {

    private static final int GAP = 8;

    private final List<String> providerIds_ = new ArrayList<>();
    private final List<String> providerNames_ = new ArrayList<>();
    private final DDRadioButton local_;
    private final DDRadioButton sync_;
    private final DDComboBox<String> provider_;
    private final DDButton credentials_;
    private final List<Runnable> listeners_ = new ArrayList<>();
    private boolean editable_ = true;
    /** What listeners were last told about; see {@link #changed()}. */
    private boolean lastSync_;
    private String lastProvider_;

    SourceTypeRow(String style) {
        // A grid bag rather than a FlowLayout: a flow wraps when squeezed, which drops the
        // Credentials button onto a second, clipped line.
        super(new GridBagLayout());
        setOpaque(false);

        local_ = new DDRadioButton("sourcetypelocal", style);
        sync_  = new DDRadioButton("sourcetypesync", style);
        ButtonGroup group = new ButtonGroup();
        group.add(local_);
        group.add(sync_);
        local_.setSelected(true);

        resetProviders(null);
        provider_ = new DDComboBox<>(new DataElement<>("syncprovider", providerIds_, providerNames_), style);
        provider_.setRequired(false);
        provider_.resetValues();
        provider_.setSelectedItem(SyncProviders.getDefault().id());

        credentials_ = new DDButton("synccredentials", style);

        local_.addActionListener(_ -> changed());
        sync_.addActionListener(_ -> changed());
        provider_.addActionListener(_ -> changed());

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, GAP);
        add(local_, c);
        add(sync_, c);
        add(provider_, c);
        c.insets = new Insets(0, 8, 0, 0);
        add(credentials_, c);
        // Soaks up any extra width so the controls stay packed to the left.
        c.weightx = 1.0;
        add(Box.createHorizontalGlue(), c);
        lastProvider_ = getProviderId();
        updateEnabled();
    }

    DDRadioButton getLocalRadio() { return local_; }
    DDRadioButton getSyncRadio() { return sync_; }
    DDComboBox<String> getProviderCombo() { return provider_; }
    DDButton getCredentialsButton() { return credentials_; }

    /** Called whenever the source type or provider changes, by the user or by a setter. */
    void addChangeListener(Runnable r) { listeners_.add(r); }

    boolean isSync() { return sync_.isSelected(); }

    void setSync(boolean sync) {
        (sync ? sync_ : local_).setSelected(true);
        changed();
    }

    /** The selected provider's {@code albums.yaml} key. */
    String getProviderId() {
        Object v = provider_.getSelectedItem();
        return v instanceof String s ? s : SyncProviders.getDefault().id();
    }

    /** The selected provider, or null when it is one the app does not offer. */
    SyncProvider getProvider() {
        return SyncProviders.get(getProviderId());
    }

    /** Selects a provider by key, adding it to the combo when the app does not offer it. */
    void setProviderId(String id) {
        String want = id != null ? id : SyncProviders.getDefault().id();
        if (!providerIds_.contains(want)) {
            resetProviders(want);
            provider_.resetValues();
        }
        provider_.setSelectedItem(want);
        changed();
    }

    /** Whether the radios and the combo accept input.  Credentials is not affected. */
    void setEditable(boolean editable) {
        editable_ = editable;
        updateEnabled();
    }

    private void resetProviders(String extraId) {
        providerIds_.clear();
        providerNames_.clear();
        for (SyncProvider p : SyncProviders.forUi()) {
            providerIds_.add(p.id());
            providerNames_.add(p.displayName());
        }
        if (extraId != null && !providerIds_.contains(extraId)) {
            providerIds_.add(extraId);
            providerNames_.add(extraId);
        }
    }

    /**
     * Tells listeners only when the source type or provider really changed.  A combo fires on
     * every pick, including re-picking the provider already shown, and that must be a no-op.
     */
    private void changed() {
        updateEnabled();
        boolean sync = isSync();
        String provider = getProviderId();
        if (sync == lastSync_ && provider.equals(lastProvider_)) return;
        lastSync_ = sync;
        lastProvider_ = provider;
        listeners_.forEach(Runnable::run);
    }

    private void updateEnabled() {
        local_.setEnabled(editable_);
        sync_.setEnabled(editable_);
        provider_.setEnabled(editable_ && isSync());
        // Credentials belong to the site, not the album, so they can be edited any time.
        credentials_.setEnabled(isSync() && getProvider() != null);
    }
}
