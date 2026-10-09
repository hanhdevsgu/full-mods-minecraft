package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.gui.ImpactClickGui;
import com.minclient.module.ModuleManager;

public class GuiCommand extends Command {
    private final ModuleManager moduleManager;

    public GuiCommand(ModuleManager moduleManager) {
        super("gui", "Mở giao diện Impact ClickGUI (.gui)", ".gui");
        this.moduleManager = moduleManager;
    }

    @Override
    public void execute(String[] args) {
        mc.addScheduledTask(() -> mc.displayGuiScreen(new ImpactClickGui(moduleManager)));
    }
}
