package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.module.ModuleManager;
import com.minclient.module.impl.LightModule;

public class LightCommand extends Command {
    private final ModuleManager moduleManager;

    public LightCommand(ModuleManager moduleManager) {
        super("light", "Bật/tắt Fullbright tăng độ sáng tối đa (.light)", ".light");
        this.moduleManager = moduleManager;
    }

    @Override
    public void execute(String[] args) {
        LightModule light = moduleManager.getLightModule();
        light.toggle();
        sendMessage(String.format("§e[MinClient] Fullbright (Light) hiện đang: %s",
                light.isEnabled() ? "§aBẬT (100% Brightness)" : "§cTẮT"));
    }
}
