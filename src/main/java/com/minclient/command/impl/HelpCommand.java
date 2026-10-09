package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.command.CommandManager;
import com.minclient.module.Module;
import com.minclient.module.ModuleManager;

public class HelpCommand extends Command {
    private final CommandManager commandManager;
    private final ModuleManager moduleManager;

    public HelpCommand(CommandManager commandManager, ModuleManager moduleManager) {
        super("help", "Hiển thị danh sách các lệnh và modules", ".help");
        this.commandManager = commandManager;
        this.moduleManager = moduleManager;
    }

    @Override
    public void execute(String[] args) {
        sendMessage("§6=== MinClient Commands ===");
        for (Command cmd : commandManager.getCommands()) {
            sendMessage(String.format("§e%s §7- %s", cmd.getSyntax(), cmd.getDescription()));
        }

        sendMessage("§6=== Modules Hiện Có ===");
        for (Module m : moduleManager.getModules()) {
            sendMessage(String.format("§b%s §7[%s§7] - %s",
                    m.getName(), m.isEnabled() ? "§aBẬT" : "§cTẮT", m.getDescription()));
        }
    }
}
