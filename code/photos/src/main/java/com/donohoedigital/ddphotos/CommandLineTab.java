package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDTabPanel;

import javax.swing.*;

public class CommandLineTab extends DDTabPanel
{
    private final String command_;

    public CommandLineTab(String command)
    {
        super(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        command_ = command;
    }

    @Override
    protected void createUI()
    {
        // Phase 6 will implement command execution and output for: ddphotos + command_
    }
}
