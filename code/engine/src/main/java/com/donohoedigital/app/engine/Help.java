/*
 * Help.java
 *
 * Created on March 29, 2003, 4:13 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;
import com.donohoedigital.app.config.*;
import com.donohoedigital.gui.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;


/**
 * @author Doug Donohoe
 */
public class Help extends BasePhase implements ListSelectionListener,
                                               HyperlinkListener, ActionListener
{
    private DDPanel base_;
    private DDHtmlArea html_;
    private OptionList<HelpTopic> list_;
    private DefaultListModel<HelpTopic> listModel_;
    private DDButton bak_, fwd_;
    private int nHistIndex_ = 0;
    private final List<HelpTopic> history_ = new ArrayList<>();
    private boolean bRunning_ = false;

    /**
     * init data
     */
    @Override
    public void init(AppEngine engine, AppContext context, AppPhase phase)
    {
        super.init(engine, context, phase);
        createDialogContents();
    }

    /**
     * create chat ui
     */
    private void createDialogContents()
    {
        String STYLE = phase_.getString(DialogPhase.PARAM_STYLE, GuiManager.DEFAULT);

        // contents
        base_ = new DDPanel();
        BorderLayout layout = (BorderLayout) base_.getLayout();
        layout.setVgap(5);
        base_.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // top - logo/label/nav
        DDPanel topbase = new DDPanel();
        DDImageButton button = new DDImageButton("icon48");
        topbase.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        base_.setBorderLayoutGap(0, 2);
        base_.add(topbase, BorderLayout.NORTH);

        DDLabel label = new DDLabel("helpwindow", STYLE);
        label.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        topbase.setBorderLayoutGap(0, 20);
        topbase.add(GuiUtils.NORTH(label), BorderLayout.CENTER);
        topbase.add(button, BorderLayout.WEST);

        DDPanel navbase = new DDPanel();
        navbase.setLayout(new GridLayout(1, 2, 5, 0));
        navbase.setBorder(BorderFactory.createEmptyBorder(10, 10, 2, 10));
        topbase.add(GuiUtils.NORTH(navbase), BorderLayout.EAST);
        bak_ = DDIconButtons.iconButton("help-bak", STYLE, DDIconButtons.ARROW_LEFT);
        bak_.addActionListener(this);
        navbase.add(bak_);
        fwd_ = DDIconButtons.iconButton("help-fwd", STYLE, DDIconButtons.ARROW_RIGHT);
        fwd_.addActionListener(this);
        navbase.add(fwd_);

        // HTML display
        html_ = new HelpHtml();
        html_.addHyperlinkListener(this);
        DDScrollPane scroll = new DDScrollPane(html_, STYLE, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                               JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        html_.setOpaque(true);
        scroll.setBackground(Color.WHITE);
        html_.setBackground(Color.WHITE);

        DDPanel wrapper = new DDPanel();
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        wrapper.add(scroll, BorderLayout.CENTER);
        base_.add(wrapper, BorderLayout.CENTER);

        ////
        //// Topic list
        ////
        listModel_ = new DefaultListModel<>();
        for (HelpTopic topic : HelpConfig.getHelpTopics())
        {
            listModel_.addElement(topic);
        }

        list_ = new OptionList<>(listModel_, "help-window", "helptopic", STYLE);
        list_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list_.setCellRenderer(new HelpTopicRenderer());
        list_.addListSelectionListener(this);
        list_.restoreFromPrefs();

        DDScrollPane tScroll = new DDScrollPane(list_, STYLE, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        tScroll.setPreferredSize(new Dimension(200, 200));
        base_.add(tScroll, BorderLayout.WEST);
        tScroll.setBorder(BorderFactory.createEtchedBorder());
    }

    /**
     * renders a help topic by its display name
     */
    private static class HelpTopicRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus)
        {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof HelpTopic topic)
            {
                label.setText(topic.getDisplay());
            }
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }
    }

    private static class HelpHtml extends DDHtmlArea
    {
        HelpHtml()
        {
            super(GuiManager.DEFAULT, "Help");
            setPreferredSize(new Dimension(525, 400));
            setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            setFocusable(true);
            setFocusTraversalKeysEnabled(true);
            GuiUtils.setDoNothingCaret(this);
        }
    }

    /**
     * Start of phase
     */
    @Override
    public void start()
    {
        String sTopic = phase_.getString(AppButton.PARAM_GENERIC, null);
        if (sTopic != null) displayHelpTopic(sTopic);

        // if users presses launch button again, this will be called.  don't run logic again in this case
        if (bRunning_) return;
        bRunning_ = true;

        // place the whole thing in the Engine's base panel
        context_.setMainUIComponent(this, base_, true, list_);
    }

    /**
     * finish
     */
    @Override
    public void finish()
    {
        bRunning_ = false;
        super.finish();
    }

    /**
     * Called when a hypertext link is updated.
     */
    public void hyperlinkUpdate(HyperlinkEvent e)
    {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
        String sName = e.getDescription();

        // strip off .html if it is there
        int indexof = sName.indexOf(".html");
        if (!sName.startsWith("http") && !sName.startsWith("ok-") && indexof != -1)
        {
            sName = sName.substring(0, indexof);
        }
        if (!displayHelpTopic(sName))
        {
            if (sName.startsWith("http"))
            {
                Utils.openURL(sName);
            }
            else if (sName.startsWith("ok-"))
            {
                String sURL = sName.substring(3);
                Utils.openURL(sURL);
            }
            else
            {
                displayHelp(PropertyConfig.getMessage("msg.helptopic.notfound", sName));
            }
            html_.setCaretPosition(0);
        }
    }

    /**
     * Display topic at current index
     */
    public void displayCurrentIndex()
    {
        HelpTopic topic = history_.get(nHistIndex_);
        displayHelpTopic(topic.getName());
    }

    /**
     * display given help topic
     */
    public boolean displayHelpTopic(String sTopic)
    {
        int nNum = listModel_.size();
        for (int i = 0; i < nNum; i++)
        {
            if (sTopic != null && sTopic.equals(listModel_.get(i).getName()))
            {
                list_.setSelectedIndex(i);
                list_.ensureIndexIsVisible(i);
                return true;
            }
        }
        return false;
    }

    HelpTopic selected_ = null;

    /**
     * Called whenever the value of the selection changes.
     *
     * @param e the event that characterizes the change.
     */
    public void valueChanged(ListSelectionEvent e)
    {
        if (e.getValueIsAdjusting()) return;
        int index = list_.getSelectedIndex();
        if (index >= 0)
        {
            //logger.debug("Not empty "+index);
            HelpTopic select = listModel_.get(index);
            if (select == selected_) return;
            if (selected_ != null)
            {
                //logger.debug(selected_.getName() + " STORING RECT: "+html_.getVisibleRect());
                selected_.setScrollPosition(html_.getVisibleRect());
            }
            selected_ = select;
            displaySelectedHelpTopic();
        }
        else
        {
            //logger.debug("Empty");
            selected_ = null;
        }
    }

    /**
     * nav buttons
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == fwd_)
        {
            goFwd();
        }
        else
        {
            goBak();
        }
    }

    private void goFwd()
    {
        bSkipHist_ = true;
        nHistIndex_++;
        displayCurrentIndex();
        bSkipHist_ = false;
    }

    private void goBak()
    {
        bSkipHist_ = true;
        nHistIndex_--;
        displayCurrentIndex();
        bSkipHist_ = false;
    }

    private void checkButtons()
    {
        bak_.setEnabled(nHistIndex_ > 0);
        fwd_.setEnabled(nHistIndex_ < (history_.size() - 1));
    }

    private boolean bSkipHist_ = false;

    private void displaySelectedHelpTopic()
    {
        if (!bSkipHist_)
        {
            if (nHistIndex_ < (history_.size() - 1))
            {
                if (history_.size() > nHistIndex_ + 1) {
                    history_.subList(nHistIndex_ + 1, history_.size()).clear();
                }
            }
            history_.add(selected_);
            nHistIndex_ = history_.size() - 1;
        }

        displayHelp(selected_.getContents());

        if (selected_.getScrollPosition() != null)
        {
            SwingUtilities.invokeLater(
                    () -> {
                        html_.scrollRectToVisible(selected_.getScrollPosition());
                        html_.repaint();
                    }
            );
        }

        checkButtons();
    }

    /**
     * add message to chat window
     */
    private void displayHelp(String sHelp)
    {
        html_.setText(sHelp);
    }
}
