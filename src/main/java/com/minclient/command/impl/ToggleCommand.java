package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.module.Module;
import com.minclient.module.ModuleManager;

public class ToggleCommand extends Command {
    private final ModuleManager moduleManager;

    public ToggleCommand(ModuleManager moduleManager) {
        super("toggle", "Bật hoặc tắt module (.t <tên>)", ".toggle <module> hoặc .t <module>");
        this.moduleManager = moduleManager;
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: " + getSyntax());
            return;
        }

        String modName = args[0];
        Module module = moduleManager.getModuleByName(modName);
        if (module == null) {
            sendMessage("§c[MinClient] Không tìm thấy module: " + modName);
            return;
        }

        module.toggle();
        sendMessage(String.format("§e[MinClient] Module §b%s§e hiện đang: %s",
                module.getName(), module.isEnabled() ? "§aBẬT" : "§cTẮT"));
    }
}
