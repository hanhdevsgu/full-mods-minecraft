package com.tntmod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandTNT extends CommandBase {
    @Override
    public String getName() {
        return "t";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/t [toggle|amount <so>|room <ten>|clear|help]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Accessible to normal players
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        if ("toggle".equals(sub)) {
            ModConfig.enabled = !ModConfig.enabled;
            ModConfig.save();
            sender.sendMessage(new TextComponentString("§6[TNT-Mod] §fTrang thai Mod: " + (ModConfig.enabled ? "§a[BAT]" : "§c[TAT]")));
        } else if ("amount".equals(sub)) {
            if (args.length >= 2) {
                try {
                    int amt = Integer.parseInt(args[1]);
                    if (amt > 0) {
                        ModConfig.dispenserTargetAmount = amt;
                        ModConfig.save();
                        sender.sendMessage(new TextComponentString("§6[TNT-Mod] §aDa dat dinh muc nap Dispenser la: §e" + amt));
                    } else {
                        sender.sendMessage(new TextComponentString("§c[TNT-Mod] So luong phai lon hon 0!"));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(new TextComponentString("§c[TNT-Mod] So khong hop le!"));
                }
            } else {
                sender.sendMessage(new TextComponentString("§eCu phap: /t amount <so_luong>"));
            }
        } else if ("room".equals(sub)) {
            if (args.length >= 2) {
                ModConfig.teamRoomId = args[1];
                ModConfig.save();
                TeamSyncClient.reconnect();
                sender.sendMessage(new TextComponentString("§6[TNT-Mod] §aDa doi phong dong bo Team thanh: §e" + args[1]));
            } else {
                sender.sendMessage(new TextComponentString("§eCu phap: /t room <ten_phong>"));
            }
        } else if ("status".equals(sub)) {
            boolean connected = TeamSyncClient.isConnected();
            TeamSyncClient.requestRoster();
            sender.sendMessage(new TextComponentString("§6========== §e§lTRANG THAI DONG BO TNT §6=========="));
            sender.sendMessage(new TextComponentString("§f- May chu Cloud: §b" + ModConfig.teamServerUrl));
            sender.sendMessage(new TextComponentString("§f- Phong Team: §e" + ModConfig.teamRoomId));
            sender.sendMessage(new TextComponentString("§f- Ket noi 24/7: " + (connected ? "§a● THANH CONG (Dang dong bo)" : "§c○ DANG KET NOI LAI...")));
            java.util.List<String> players = TeamSyncClient.getConnectedPlayers();
            if (players.isEmpty()) {
                String myName = (net.minecraft.client.Minecraft.getMinecraft().player != null) ? net.minecraft.client.Minecraft.getMinecraft().player.getName() : "Ban";
                sender.sendMessage(new TextComponentString("§f- Thanh vien chung room: §a" + myName + " §7(Dang ket noi mot minh)"));
            } else {
                sender.sendMessage(new TextComponentString("§f- Thanh vien chung room (" + players.size() + "): §a" + String.join("§f, §a", players)));
            }
            sender.sendMessage(new TextComponentString("§f- So diem ESP hien tai: §e" + BlockStateManager.getTrackedBlocks().size()));
        } else if ("mode".equals(sub)) {
            if (args.length >= 2) {
                String m = args[1].toLowerCase();
                if (m.contains("dia") || m.equals("0") || m.contains("kimcuong")) {
                    ModConfig.renderMode = 0;
                    ModConfig.save();
                    sender.sendMessage(new TextComponentString("§6[TNT-Mod] §aDa chuyen sang che do: §b[Block Kim Cuong 3D Skin]"));
                } else if (m.contains("green") || m.contains("xanh") || m.equals("1") || m.contains("dam")) {
                    ModConfig.renderMode = 1;
                    ModConfig.save();
                    sender.sendMessage(new TextComponentString("§6[TNT-Mod] §aDa chuyen sang che do: §a[To Dam Xanh Neon Full May]"));
                } else if (m.contains("trans") || m.contains("suot") || m.equals("2")) {
                    ModConfig.renderMode = 2;
                    ModConfig.save();
                    sender.sendMessage(new TextComponentString("§6[TNT-Mod] §aDa chuyen sang che do: §3[Xanh Trong Suot (ESP)]"));
                } else {
                    sender.sendMessage(new TextComponentString("§cCu phap: /t mode [diamond|green|translucent]"));
                }
            } else {
                ModConfig.renderMode = (ModConfig.renderMode + 1) % 3;
                ModConfig.save();
                String name = ModConfig.renderMode == 0 ? "§b[Block Kim Cuong 3D]" : (ModConfig.renderMode == 1 ? "§a[To Dam Xanh Full May]" : "§3[Xanh Trong Suot]");
                sender.sendMessage(new TextComponentString("§6[TNT-Mod] §aChe do hien tai: " + name));
            }
        } else if ("clear".equals(sub)) {
            BlockStateManager.clearAll();
            sender.sendMessage(new TextComponentString("§6[TNT-Mod] §aDa xoa toan bo danh dau ESP Ruong & Dispenser tren ban do."));
        } else {
            sendHelp(sender);
        }
    }

    private void sendHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString("§6========== §e§lTNT AUTO LOGISTICS MOD §6=========="));
        sender.sendMessage(new TextComponentString("§e/t status §7- Kiem tra ket noi Cloud va phong dong bo"));
        sender.sendMessage(new TextComponentString("§e/t toggle §7- Bat/Tat mod"));
        sender.sendMessage(new TextComponentString("§e/t mode [diamond|green] §7- Doi che do Kim Cuong / To dam xanh"));
        sender.sendMessage(new TextComponentString("§e/t amount <so> §7- Dat so luong TNT nap vao may phan phat"));
        sender.sendMessage(new TextComponentString("§e/t room <ten> §7- Doi phong dong bo ESP cung dong doi"));
        sender.sendMessage(new TextComponentString("§e/t clear §7- Xoa toan bo ESP da danh dau"));
        sender.sendMessage(new TextComponentString("§bPhim F7: §fMenu cai dat chi tiet (Dinh muc, Che do skin, Tu dong dong)"));
        sender.sendMessage(new TextComponentString("§bPhim F8: §fMenu bat/tat nhanh ESP & che do"));
    }
}
