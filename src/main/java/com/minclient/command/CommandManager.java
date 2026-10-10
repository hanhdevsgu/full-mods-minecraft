package com.minclient.command;

import baritone.api.BaritoneAPI;
import com.minclient.command.impl.*;
import com.minclient.module.Module;
import com.minclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

public class CommandManager {
    private final Map<String, Command> commands = new HashMap<>();
    private final ModuleManager moduleManager;

    public CommandManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        registerCommand(new LookCommand());
        registerCommand(new GotoCommand());
        registerCommand(new StopCommand());
        registerCommand(new ToggleCommand(moduleManager));
        registerCommand(new LightCommand(moduleManager));
        registerCommand(new HelpCommand(this, moduleManager));
    }

    public void registerCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    public Collection<Command> getCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }

    /**
     * Xử lý chuỗi chat nhập vào từ client.
     * TUYỆT ĐỐI KHÔNG để lọt bất kỳ tin nhắn nào bắt đầu bằng '.' hoặc '#' lên Server.
     */
    public boolean handleChat(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return false;
        }

        char prefix = rawMessage.charAt(0);
        if (prefix != '.' && prefix != '#') {
            return false;
        }

        String content = rawMessage.substring(1).trim();

        if (content.isEmpty()) {
            showHelpList();
            return true;
        }

        String[] parts = content.split("\\s+");
        String cmdName = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        if (cmdName.equals("t")) {
            cmdName = "toggle";
        }

        Command command = commands.get(cmdName);
        if (command != null) {
            command.execute(args);
            return true;
        }

        // Nếu là lệnh không thuộc MinClient (ví dụ các lệnh #sel, #mine, #follow, #build, #clear...),
        // chuyển tiếp trực tiếp sang Baritone API gốc 100%
        try {
            Object cmdManager = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();
            java.lang.reflect.Method execMethod = cmdManager.getClass().getMethod("execute", String.class);
            boolean executed = (boolean) execMethod.invoke(cmdManager, content);
            if (executed) {
                return true;
            }
        } catch (Throwable ignored) {}

        // Khi gõ nhầm lệnh
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§c[MinClient] Lệnh không hợp lệ: §f" + rawMessage));
        }
        showHelpList();

        return true;
    }

    private void showHelpList() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        mc.player.sendMessage(new TextComponentString("§6=== Danh Sách Lệnh (MinClient & Baritone) ==="));
        for (Command cmd : commands.values()) {
            mc.player.sendMessage(new TextComponentString(String.format("§e%s §7- %s", cmd.getSyntax(), cmd.getDescription())));
        }
        mc.player.sendMessage(new TextComponentString("§6=== Trạng Thái Modules ==="));
        for (Module m : moduleManager.getModules()) {
            mc.player.sendMessage(new TextComponentString(String.format("§b%s §7[%s§7] - %s",
                    m.getName(), m.isEnabled() ? "§aBẬT" : "§cTẮT", m.getDescription())));
        }
        mc.player.sendMessage(new TextComponentString("§7(Ấn phím §bRSHIFT§7 để mở bảng điều khiển ClickGUI)"));
    }
}
