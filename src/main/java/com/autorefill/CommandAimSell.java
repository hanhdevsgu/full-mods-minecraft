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
            // Gõ /aimsell: Mặc định là thuốc súng (gunpowder)
            // Nếu đang bật ghim thuốc súng -> Tắt ghim
            // Nếu đang tắt hoặc đang ghim item khác -> Bật ghim thuốc súng
            if (SignAimHandler.isAimLockActive && SignAimHandler.isGunpowderTarget(SignAimHandler.lastItemTarget)) {
                SignAimHandler.stopAimLock(mc);
                mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[AutoAim] Đã TẮT ghim camera."));
            } else {
                SignAimHandler.startAimLock("gunpowder");
            }
            return;
        }

        String rawInput = String.join(" ", args).trim();
        String cleaned = rawInput.replaceAll("[<>\\[\\]]", "").trim();
        String sub = cleaned.toLowerCase();

        // Xử lý /aimsell lock hoặc /aimsell toggle
        if (sub.equals("lock") || sub.equals("toggle")) {
            if (SignAimHandler.isAimLockActive && SignAimHandler.isGunpowderTarget(SignAimHandler.lastItemTarget)) {
                SignAimHandler.stopAimLock(mc);
                mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[AutoAim] Đã TẮT ghim camera."));
            } else {
                SignAimHandler.startAimLock("gunpowder");
            }
            return;
        } else if (sub.startsWith("lock ") || sub.startsWith("toggle ")) {
            String targetItem = cleaned.substring(cleaned.indexOf(' ')).trim();
            SignAimHandler.toggleAimLock(targetItem);
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
        } else if (sub.startsWith("offset")) {
            String[] parts = cleaned.split("\\s+");
            if (parts.length >= 2) {
                try {
                    SignAimHandler.yOffset = Double.parseDouble(parts[1]);
                    mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim] Đã đặt độ lệch Y = " + SignAimHandler.yOffset));
                } catch (Exception e) {
                    mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "Số không hợp lệ!"));
                }
            } else {
                mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim] Độ lệch Y hiện tại: " + String.format("%.2f", SignAimHandler.yOffset)));
            }
        } else if (sub.equals("help")) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== LỆNH AUTO-AIM GHIM BẢNG SELL ==="));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell" + TextFormatting.WHITE + " - Bật/Tắt ghim bảng Thuốc súng (mặc định)"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell <ID_ITEM>" + TextFormatting.WHITE + " - Ghim bảng theo ID (vd: /aimsell 42 hoặc /aimsell <42> bán Khối sắt)"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell <Tên_Item>" + TextFormatting.WHITE + " - Ghim bảng theo tên (vd: /aimsell gunpowder, /aimsell iron_block)"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell off" + TextFormatting.WHITE + " - Tắt ghim camera"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell click" + TextFormatting.WHITE + " - Click phải vào bảng đang ghim"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/aimsell debug" + TextFormatting.WHITE + " - Quét danh sách các bảng xung quanh"));
        } else {
            // Ghim item bất kỳ (theo ID như 42, <42> hoặc tên)
            SignAimHandler.startAimLock(cleaned);
        }
    }
}
