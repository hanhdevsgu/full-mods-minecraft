package com.autorefill;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandRefill extends CommandBase {

    @Override
    public String getName() {
        return "refill";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("autorefill", "rf");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/refill help";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Client-side command, an toan 100%
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "toggle":
                ConfigManager.enabled = !ConfigManager.enabled;
                ConfigManager.save();
                sendMessage(sender, ConfigManager.enabled
                        ? TextFormatting.GREEN + "[AutoRefill] Đã BẬT mod!"
                        : TextFormatting.RED + "[AutoRefill] Đã TẮT mod!");
                break;

            case "set":
                if (args.length < 2) {
                    sendMessage(sender, TextFormatting.RED + "Cách dùng: /rf set <tên_block> (Ví dụ: /rf set cobblestone)");
                    return;
                }
                String blockInput = args[1];
                InventoryUtils.ParsedItem parsed = InventoryUtils.parseItemString(blockInput);
                if (parsed != null && parsed.item instanceof ItemBlock) {
                    RefillHandler.setTarget(parsed.item, parsed.meta, parsed.getDisplayName());
                    sendMessage(sender, TextFormatting.GREEN + "[AutoRefill] Đã đặt block mục tiêu: "
                            + TextFormatting.AQUA + parsed.getDisplayName());
                    sendMessage(sender, TextFormatting.WHITE + "Chỉ khi hotbar hết sạch block này, mod mới lấy từ túi đồ xuống!");
                    if (ConfigManager.maxPV > 0) {
                        sendMessage(sender, TextFormatting.YELLOW + "[AutoRefill] Đã bật tự động rút từ /pv 1 đến /pv " + ConfigManager.maxPV + " khi túi đồ hết!");
                    }
                } else {
                    sendMessage(sender, TextFormatting.RED + "[AutoRefill] Block không hợp lệ hoặc không phải là block xây dựng: " + blockInput);
                }
                break;

            case "hand":
                ItemStack held = mc.player.getHeldItemMainhand();
                if (held.isEmpty() || !(held.getItem() instanceof ItemBlock)) {
                    sendMessage(sender, TextFormatting.RED + "[AutoRefill] Bạn phải đang cầm 1 block xây dựng trên tay chính!");
                    return;
                }
                RefillHandler.setTarget(held.getItem(), held.getMetadata(), held.getDisplayName());
                sendMessage(sender, TextFormatting.GREEN + "[AutoRefill] Đã đặt block mục tiêu theo tay cầm: "
                        + TextFormatting.AQUA + held.getDisplayName());
                break;

            case "clear":
            case "stop":
                RefillHandler.clearTarget();
                sendMessage(sender, TextFormatting.YELLOW + "[AutoRefill] Đã hủy mục tiêu. Mod đang ở trạng thái nghỉ, không đụng vào kho đồ.");
                break;

            case "pv":
                if (args.length < 2) {
                    sendPVStatus(sender);
                    sendMessage(sender, TextFormatting.WHITE + "Cách dùng:");
                    sendMessage(sender, TextFormatting.YELLOW + "/rf pv <số_kho> " + TextFormatting.WHITE + ": Đặt số kho tối đa (ví dụ: /rf pv 3 hoặc /rf pv 10)");
                    sendMessage(sender, TextFormatting.YELLOW + "/rf pv reset " + TextFormatting.WHITE + ": Đặt lại bắt đầu từ /pv 1");
                    sendMessage(sender, TextFormatting.YELLOW + "/rf pv off " + TextFormatting.WHITE + ": Tắt tính năng tự mở PV");
                    return;
                }
                String pvArg = args[1].toLowerCase();
                if (pvArg.equals("reset")) {
                    ConfigManager.currentPV = 1;
                    ConfigManager.save();
                    PVHandler.reset();
                    sendMessage(sender, TextFormatting.GREEN + "[AutoRefill] Đã đặt lại kho bắt đầu: /pv 1!");
                } else if (pvArg.equals("off") || pvArg.equals("0")) {
                    ConfigManager.maxPV = 0;
                    ConfigManager.save();
                    PVHandler.reset();
                    sendMessage(sender, TextFormatting.YELLOW + "[AutoRefill] Đã TẮT tính năng tự động mở /pv.");
                } else if (pvArg.equals("status")) {
                    sendPVStatus(sender);
                } else {
                    try {
                        int num = Integer.parseInt(pvArg);
                        if (num < 1 || num > 100) {
                            sendMessage(sender, TextFormatting.RED + "[AutoRefill] Số kho phải từ 1 đến 100.");
                            return;
                        }
                        ConfigManager.maxPV = num;
                        ConfigManager.currentPV = 1;
                        ConfigManager.save();
                        PVHandler.reset();
                        sendMessage(sender, TextFormatting.GREEN + "[AutoRefill] Đã bật tự động rút kho PV từ "
                                + TextFormatting.YELLOW + "/pv 1" + TextFormatting.GREEN + " đến "
                                + TextFormatting.YELLOW + "/pv " + num + "!");
                        sendMessage(sender, TextFormatting.WHITE + "Khi túi đồ hết sạch block, mod sẽ mở từng kho, shift-click cực nhanh delay = 0, rồi đóng lại cho Baritone tiếp tục xây!");
                    } catch (NumberFormatException e) {
                        sendMessage(sender, TextFormatting.RED + "[AutoRefill] Số kho không hợp lệ!");
                    }
                }
                break;

            case "delay":
                if (args.length < 2) {
                    sendMessage(sender, TextFormatting.YELLOW + "[AutoRefill] Độ trễ an toàn hiện tại: " + ConfigManager.delayTicks + " ticks (" + (ConfigManager.delayTicks * 50) + "ms).");
                    sendMessage(sender, TextFormatting.WHITE + "Cách chỉnh: /rf delay <số_tick> (Mặc định: 2 ticks = 100ms)");
                    return;
                }
                try {
                    int d = Integer.parseInt(args[1]);
                    if (d < 1 || d > 20) {
                        sendMessage(sender, TextFormatting.RED + "[AutoRefill] Độ trễ phải từ 1 đến 20 ticks.");
                        return;
                    }
                    ConfigManager.delayTicks = d;
                    ConfigManager.save();
                    sendMessage(sender, TextFormatting.GREEN + "[AutoRefill] Đã cập nhật độ trễ thành: " + d + " ticks (" + (d * 50) + "ms).");
                } catch (NumberFormatException e) {
                    sendMessage(sender, TextFormatting.RED + "[AutoRefill] Số tick không hợp lệ!");
                }
                break;

            case "silent":
                ConfigManager.silent = !ConfigManager.silent;
                ConfigManager.save();
                sendMessage(sender, ConfigManager.silent
                        ? TextFormatting.YELLOW + "[AutoRefill] Đã tắt thông báo chat trên màn hình."
                        : TextFormatting.GREEN + "[AutoRefill] Đã bật thông báo chat trên màn hình.");
                break;

            case "status":
                sendStatus(sender);
                break;

            default:
                sendMessage(sender, TextFormatting.RED + "[AutoRefill] Lệnh không hợp lệ. Gõ /rf help để xem hướng dẫn.");
                break;
        }
    }

    private void sendHelp(ICommandSender sender) {
        sendMessage(sender, TextFormatting.GOLD + "========== [ AUTO HOTBAR REFILL - HƯỚNG DẪN ] ==========");
        sendMessage(sender, TextFormatting.YELLOW + "/rf toggle " + TextFormatting.WHITE + ": Bật hoặc tắt mod (Phím tắt: K)");
        sendMessage(sender, TextFormatting.YELLOW + "/rf set <block> " + TextFormatting.WHITE + ": Đặt block cần lấy xuống hotbar (vd: /rf set cobblestone)");
        sendMessage(sender, TextFormatting.YELLOW + "/rf hand " + TextFormatting.WHITE + ": Lấy block đang cầm trên tay làm mục tiêu");
        sendMessage(sender, TextFormatting.YELLOW + "/rf clear " + TextFormatting.WHITE + ": Dừng lấy đồ ngay lập tức");
        sendMessage(sender, TextFormatting.YELLOW + "/rf pv <số_kho> " + TextFormatting.WHITE + ": Tự động mở /pv rút đồ khi túi hết (vd: /rf pv 3 hoặc /rf pv 10)");
        sendMessage(sender, TextFormatting.YELLOW + "/rf pv reset " + TextFormatting.WHITE + ": Đặt lại kho bắt đầu từ /pv 1");
        sendMessage(sender, TextFormatting.YELLOW + "/rf delay <ticks> " + TextFormatting.WHITE + ": Chỉnh độ trễ (mặc định 2 ticks = 100ms)");
        sendMessage(sender, TextFormatting.YELLOW + "/rf silent " + TextFormatting.WHITE + ": Bật/tắt thông báo trên màn hình");
        sendMessage(sender, TextFormatting.YELLOW + "/rf status " + TextFormatting.WHITE + ": Xem trạng thái hiện tại");
        sendMessage(sender, TextFormatting.AQUA + "Lưu ý: Mod chỉ chạy khi bạn gõ #set set <block> hoặc /rf set <block>. Tuyệt đối không đụng vào cung, kiếm, công cụ!");
        sendMessage(sender, TextFormatting.GOLD + "========================================================");
    }

    private void sendStatus(ICommandSender sender) {
        sendMessage(sender, TextFormatting.GOLD + "--- [ AutoRefill - Trạng Thái Hiện Tại ] ---");
        sendMessage(sender, TextFormatting.WHITE + "Trạng thái: " + (ConfigManager.enabled ? TextFormatting.GREEN + "ĐANG BẬT" : TextFormatting.RED + "ĐANG TẮT"));
        sendMessage(sender, TextFormatting.WHITE + "Block mục tiêu: " + (RefillHandler.hasTarget() ? TextFormatting.AQUA + RefillHandler.getTargetDisplayName() : TextFormatting.GRAY + "Không có (Đang nghỉ, không thao tác túi đồ)"));
        sendMessage(sender, TextFormatting.WHITE + "Tự động rút kho /pv: " + (ConfigManager.maxPV > 0
                ? TextFormatting.GREEN + "BẬT (Đang ở /pv " + ConfigManager.currentPV + ", Tối đa: /pv " + ConfigManager.maxPV + ")"
                : TextFormatting.GRAY + "TẮT (Gõ /rf pv <số> để bật)"));
        sendMessage(sender, TextFormatting.WHITE + "Độ trễ (Delay): " + TextFormatting.YELLOW + ConfigManager.delayTicks + " ticks (" + (ConfigManager.delayTicks * 50) + "ms)");
        sendMessage(sender, TextFormatting.WHITE + "Tự nhận diện Baritone: " + TextFormatting.GREEN + "BẬT");
        sendMessage(sender, TextFormatting.WHITE + "Chế độ im lặng: " + (ConfigManager.silent ? TextFormatting.YELLOW + "BẬT" : TextFormatting.GRAY + "TẮT"));
    }

    private void sendPVStatus(ICommandSender sender) {
        sendMessage(sender, TextFormatting.GOLD + "--- [ Trạng Thái Tự Động Rút Kho /pv ] ---");
        if (ConfigManager.maxPV > 0) {
            sendMessage(sender, TextFormatting.WHITE + "Chế độ: " + TextFormatting.GREEN + "ĐANG BẬT");
            sendMessage(sender, TextFormatting.WHITE + "Kho hiện tại: " + TextFormatting.YELLOW + "/pv " + ConfigManager.currentPV);
            sendMessage(sender, TextFormatting.WHITE + "Kho tối đa: " + TextFormatting.YELLOW + "/pv " + ConfigManager.maxPV);
        } else {
            sendMessage(sender, TextFormatting.WHITE + "Chế độ: " + TextFormatting.GRAY + "ĐANG TẮT (Chưa cài đặt số kho)");
            sendMessage(sender, TextFormatting.WHITE + "Ví dụ bật 3 kho: " + TextFormatting.YELLOW + "/rf pv 3");
            sendMessage(sender, TextFormatting.WHITE + "Ví dụ bật 10 kho: " + TextFormatting.YELLOW + "/rf pv 10");
        }
    }

    private void sendMessage(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "toggle", "set", "hand", "clear", "stop", "pv", "delay", "silent", "status", "help");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return getListOfStringsMatchingLastWord(args, "cobblestone", "stone", "dirt", "oak_planks", "sandstone", "obsidian", "brick_block");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pv")) {
            return getListOfStringsMatchingLastWord(args, "3", "5", "10", "reset", "off", "status");
        }
        return Collections.emptyList();
    }
}
