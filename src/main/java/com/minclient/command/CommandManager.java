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

        // Ưu tiên xử lý lệnh riêng của MinClient (.look, .toggle, .light, .help)
        Command command = commands.get(cmdName);
        if (command != null) {
            command.execute(args);
            return true;
        }

        // Toàn bộ các lệnh còn lại (goto, stop, mine, sel, follow, path, tunnel, farm...)
        // được chuyển tiếp TRỰC TIẾP sang bộ máy Baritone 100% nguyên bản
        try {
            Object baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                Object cmdManager = baritone.getClass().getMethod("getCommandManager").invoke(baritone);
                java.lang.reflect.Method execMethod = cmdManager.getClass().getMethod("execute", String.class);
                boolean executed = (boolean) execMethod.invoke(cmdManager, content);
                if (executed) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§c[Baritone] Lỗi khi thực thi lệnh: " + t.getMessage()));
            }
            return true;
        }

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

        mc.player.sendMessage(new TextComponentString("§6=== MinClient Commands ==="));
        for (Command cmd : commands.values()) {
            mc.player.sendMessage(new TextComponentString(String.format("§e%s §7- %s", cmd.getSyntax(), cmd.getDescription())));
        }
        mc.player.sendMessage(new TextComponentString("§6=== Lệnh Baritone Gốc 100% (. hoặc #) ==="));
        mc.player.sendMessage(new TextComponentString("§e#goto <x> <y> <z> §7- Tự tìm đường đi đến tọa độ (hỗ trợ ~)"));
        mc.player.sendMessage(new TextComponentString("§e#stop §7- Dừng ngay mọi hoạt động của bot"));
        mc.player.sendMessage(new TextComponentString("§e#mine <block> §7- Tự tìm đường và đào khoáng sản"));
        mc.player.sendMessage(new TextComponentString("§e#sel 1 / #sel 2 / #sel ca §7- Chọn vùng và đào sạch"));
        mc.player.sendMessage(new TextComponentString("§6=== Trạng Thái Modules ==="));
        for (Module m : moduleManager.getModules()) {
            mc.player.sendMessage(new TextComponentString(String.format("§b%s §7[%s§7] - %s",
                    m.getName(), m.isEnabled() ? "§aBẬT" : "§cTẮT", m.getDescription())));
        }
        mc.player.sendMessage(new TextComponentString("§7(Ấn phím §bRSHIFT§7 để mở bảng điều khiển ClickGUI)"));
    }
}
