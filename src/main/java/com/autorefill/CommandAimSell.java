package com.autorefill;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;
import java.util.List;

public class CommandAimSell extends CommandBase {

    @Override
    public String getName() {
        return "aimsell";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("aimsign", "sellaim");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/aimsell [item / off / click / debug / up / down]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (args.length == 0) {
            // Gõ /aimsell: Bật / Tắt ghim camera vào bảng Sell Gunpowder
            SignAimHandler.toggleAimLock("gunpowder");
            return;
        }

        String sub = args[0].toLowerCase();

        // Xử lý /aimsell lock hoặc /aimsell toggle
        if (sub.equals("lock") || sub.equals("toggle")) {
            if (args.length >= 2) {
                String subState = args[1].toLowerCase();
                if (subState.equals("off") || subState.equals("stop") || subState.equals("unlock")) {
                    SignAimHandler.stopAimLock(mc);
                    mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[AutoAim] Đã TẮT ghim camera."));
                    return;
                } else if (subState.equals("on") || subState.equals("start")) {
                    SignAimHandler.startAimLock("gunpowder");
                    return;
                }
            }
            // Mặc định /aimsell lock là bật / tắt toggle
            SignAimHandler.toggleAimLock("gunpowder");
            return;
        }

        if (sub.equals("on") || sub.equals("start")) {
            SignAimHandler.startAimLock("gunpowder");
        } else if (sub.equals("off") || sub.equals("stop") || sub.equals("unlock")) {
            SignAimHandler.stopAimLock(mc);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[AutoAim] Đã TẮT ghim camera."));
        } else if (sub.equals("click")) {
            SignAimHandler.clickTargetSign();
        } else if (sub.equals("debug") || sub.equals("scan") || sub.equals("list")) {
            SignAimHandler.printDebugSigns();
        } else if (sub.equals("down")) {
            SignAimHandler.yOffset -= 0.15;
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim] Độ lệch Y: " + String.format("%.2f", SignAimHandler.yOffset)));
        } else if (sub.equals("up")) {
            SignAimHandler.yOffset += 0.15;
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim] Độ lệch Y: " + String.format("%.2f", SignAimHandler.yOffset)));
        } else if (sub.equals("offset") && args.length >= 2) {
            try {
                SignAimHandler.yOffset = Double.parseDouble(args[1]);
                mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim] Đã đặt độ lệch Y = " + SignAimHandler.yOffset));
            } catch (Exception e) {
                mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "Số không hợp lệ!"));
            }
        } else if (sub.equals("help")) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== LỆNH AUTO-AIM GHIM BẢNG SELL ==="));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell" + TextFormatting.WHITE + " hoặc " + TextFormatting.YELLOW + "/aimsell lock" + TextFormatting.WHITE + " - Bật/Tắt ghim camera"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell on / off" + TextFormatting.WHITE + " - Bật hoặc Tắt ghim camera"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell <tên_item>" + TextFormatting.WHITE + " - Ghim bảng item khác (vd: /aimsell tnt)"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell click" + TextFormatting.WHITE + " - Click phải vào bảng đang ghim"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell debug" + TextFormatting.WHITE + " - Quét danh sách các bảng xung quanh"));
        } else {
            // Ghim item bất kỳ (nếu không phải các lệnh trên)
            SignAimHandler.startAimLock(sub);
        }
    }
}
