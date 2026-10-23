package com.autorefill;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class SignAimHandler {

    // Trạng thái ghim camera theo thời gian thực (AimLock)
    public static boolean isAimLockActive = false;
    public static BlockPos targetSignPos = null;
    public static String lastItemTarget = "gunpowder";
    public static double yOffset = 0.0;


    // Vị trí gốc đứng bán (anchor) để chỉ bước lại khi bị đẩy lùi khỏi điểm này
    public static Vec3d anchorPos = null;
    public static boolean isWarmupActive = false;

    // Tự động đi lại quầy khi bị người khác đẩy ra xa (Anti-Push / Auto-Return)
    public static boolean autoReturnEnabled = true;
    private static boolean isAutoWalking = false;

    // Bộ đếm trễ phản xạ tự nhiên của con người
    private static int outOfRangeTicks = 0;
    private static int stuckTicks = 0;
    private static double lastCheckX = 0;
    private static double lastCheckZ = 0;

    // Cự ly chuẩn tự nhiên:
    // Đứng bán lý tưởng: ~1.8 block
    // Cự ly chuẩn tự nhiên:
    // Đứng bán lý tưởng: ~1.7 block
    // Cho phép bị đẩy lùi tự nhiên tới: ~2.4 block
    private static final double TRIGGER_WALK_DIST_XZ = 2.4; 
    private static final double IDEAL_SELL_DIST_XZ = 1.7;   

    public static String cleanText(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    public static boolean isGunpowderTarget(String target) {
        if (target == null) return false;
        String t = target.toLowerCase().trim();
        return t.equals("gunpowder") || t.equals("thuoc sung") || t.equals("thuốc súng") 
            || t.equals("thuocsung") || t.equals("powder");
    }

    public static boolean isReedsTarget(String target) {
        if (target == null) return false;
        String t = target.toLowerCase().trim();
        return t.equals("reeds") || t.equals("reed") || t.equals("sugar_cane") 
            || t.equals("sugarcane") || t.equals("sugar cane") || t.equals("mia") || t.equals("mía");
    }

    /**
     * Kiểm tra xem bảng có phải là bảng MUA (Buy sign) hay không.
     * Hỗ trợ mọi định dạng: [Buy], Buy, [Mua], Mua, B 100, B:100, Giá mua, v.v.
     */
    public static boolean isBuySign(TileEntitySign sign) {
        if (sign == null) return false;
        for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            if (raw.contains("buy") || raw.contains("[buy]")
                || raw.contains("mua") || raw.contains("[mua]")
                || raw.contains("b ") || raw.startsWith("b ") || raw.endsWith(" b") || raw.equals("b")
                || raw.contains("b:") || raw.contains(":b")
                || raw.contains("giá mua") || raw.contains("mua:") || raw.contains("buy:")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnySellSign(TileEntitySign sign) {
        if (sign == null) return false;
        if (isBuySign(sign)) return false;
        boolean hasSell = false;
        for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            if (raw.contains("sell") || raw.contains("[sell]") || raw.contains("bán") || raw.contains("[bán]") || raw.contains("s ") || raw.startsWith("s ")) {
                hasSell = true;
            }
        }
        return hasSell;
    }

    public static boolean isMatchingSellSign(TileEntitySign sign, String itemTarget) {
        if (sign == null) return false;
        if (isBuySign(sign)) return false; // TUYỆT ĐỐI KHÔNG BAO GIỜ COI BẢNG BUY LÀ BẢNG SELL!

        StringBuilder sb = new StringBuilder();
        boolean hasSell = false;

        for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            sb.append(" ").append(raw);
            if (raw.contains("sell") || raw.contains("[sell]") || raw.contains("bán") || raw.contains("[bán]") || raw.contains("s ") || raw.startsWith("s ")) {
                hasSell = true;
            }
        }

        if (!hasSell) return false;
        if (itemTarget == null || itemTarget.trim().isEmpty() || itemTarget.equals("any") || itemTarget.equals("all")) {
            return true;
        }

        String combined = sb.toString();
        if (isGunpowderTarget(itemTarget)) {
            return combined.contains("gunpowder") || combined.contains("thuoc sung") 
                || combined.contains("thuốc súng") || combined.contains("thuocsung") || combined.contains("powder");
        } else if (isReedsTarget(itemTarget)) {
            return combined.contains("reeds") || combined.contains("reed") || combined.contains("sugar")
                || combined.contains("cane") || combined.contains("mia") || combined.contains("mía");
        } else {
            return combined.contains(itemTarget.toLowerCase());
        }
    }

    public static boolean isSignVisible(EntityPlayerSP player, BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return false;

        Vec3d eyePos = player.getPositionEyes(1.0F);
        IBlockState state = mc.world.getBlockState(pos);
        AxisAlignedBB bbox = state.getBoundingBox(mc.world, pos);
        Vec3d signCenter = new Vec3d(
            pos.getX() + (bbox.minX + bbox.maxX) / 2.0,
            pos.getY() + 0.35 + yOffset,
            pos.getZ() + (bbox.minZ + bbox.maxZ) / 2.0
        );

        RayTraceResult hit = mc.world.rayTraceBlocks(eyePos, signCenter, false, true, false);
        return (hit == null || hit.typeOfHit == RayTraceResult.Type.MISS || hit.getBlockPos().equals(pos));
    }

    /**
     * Tìm bảng phù hợp gần nhất khi khởi động ghim
     */
    public static boolean findTargetSign(String itemTarget) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return false;

        EntityPlayerSP player = mc.player;
        if (itemTarget == null || itemTarget.trim().isEmpty()) {
            itemTarget = "gunpowder";
        }
        itemTarget = itemTarget.trim().toLowerCase();
        lastItemTarget = itemTarget;

        List<TileEntitySign> matchedSigns = new ArrayList<TileEntitySign>();
        double maxDistSq = 256.0; // 16 block

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                double distSq = player.getDistanceSq(sign.getPos());
                if (distSq > maxDistSq) continue;

                if (isMatchingSellSign(sign, itemTarget)) {
                    matchedSigns.add(sign);
                }
            }
        }

        // Nếu không tìm thấy bảng khớp từ khóa và itemTarget là "any" thì mới tìm bất kỳ bảng sell nào
        if (matchedSigns.isEmpty() && (itemTarget.equals("any") || itemTarget.equals("all"))) {
            for (TileEntity te : mc.world.loadedTileEntityList) {
                if (te instanceof TileEntitySign) {
                    TileEntitySign sign = (TileEntitySign) te;
                    double distSq = player.getDistanceSq(sign.getPos());
                    if (distSq > 144.0) continue; // 12 block

                    if (isAnySellSign(sign)) {
                        matchedSigns.add(sign);
                    }
                }
            }
        }

        if (matchedSigns.isEmpty()) {
            String name = isGunpowderTarget(itemTarget) ? "Thuốc súng (Gunpowder)" : (isReedsTarget(itemTarget) ? "Mía (Sugar Cane)" : itemTarget);
            player.sendMessage(new TextComponentString(TextFormatting.RED + 
                "[AutoAim] Không tìm thấy bảng Sell cho '" + name + "' trong khu vực xung quanh!"));
            return false;
        }

        TileEntitySign bestSign = null;
        double bestDistSq = Double.MAX_VALUE;

        for (TileEntitySign sign : matchedSigns) {
            double dSq = player.getDistanceSq(sign.getPos());
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestSign = sign;
            }
        }

        if (bestSign == null) {
            bestSign = matchedSigns.get(0);
        }

        targetSignPos = bestSign.getPos();
        return true;
    }

    /**
     * Tự động quét kiểm tra bảng:
     * - Khi đang đứng ở quầy và bảng hiện tại vẫn là bảng nông sản cần bán (trong 8 block):
     *   TUYỆT ĐỐI GIỮ NGUYÊN, không bao giờ tự ý nhảy sang bảng khác (như TNT, NetherStar, SpiderEye...)
     * - Chỉ tìm bảng mới khi bảng cũ biến mất hoặc bị dịch chuyển sang phòng khác (> 8 block).
     */
    private static void scanAndSwitchTargetSign(Minecraft mc) {
        if (mc.world == null || mc.player == null) return;
        EntityPlayerSP player = mc.player;

        // Nếu đã có bảng ghim và bảng đó vẫn khớp món đồ cần bán (thuốc súng/mía) trong cự ly 8 block:
        // TUYỆT ĐỐI KHÔNG ĐỔI SANG BẢNG KHÁC DÙ BỊ ĐẨY SANG BÊN NÀO!
        if (targetSignPos != null) {
            TileEntity te = mc.world.getTileEntity(targetSignPos);
            if (te instanceof TileEntitySign) {
                TileEntitySign currentSign = (TileEntitySign) te;
                if (isMatchingSellSign(currentSign, lastItemTarget) && player.getDistanceSq(targetSignPos) <= 64.0) {
                    return; // Đang ngắm đúng bảng món đồ cần bán, giữ chặt 100%!
                }
            }
        }

        // Chỉ khi bảng cũ không còn hoặc bị đẩy ra quá 8 block mới quét tìm bảng thuốc súng khác:
        TileEntitySign closestSign = null;
        double closestDistSq = Double.MAX_VALUE;

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                // BẮT BUỘC PHẢI KHỚP ĐÚNG MÓN ĐỒ (lastItemTarget), KHÔNG BAO GIỜ LẤY BẢNG MÓN KHÁC!
                if (!isMatchingSellSign(sign, lastItemTarget)) continue;

                double dSq = player.getDistanceSq(sign.getPos());
                if (dSq > 64.0) continue; // trong vòng 8 block

                if (dSq < closestDistSq) {
                    closestDistSq = dSq;
                    closestSign = sign;
                }
            }
        }

        if (closestSign != null) {
            targetSignPos = closestSign.getPos();
        }
    }

    public static void printDebugSigns() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        EntityPlayerSP player = mc.player;
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[AutoAim Debug] Đang quét các bảng xung quanh..."));
        int count = 0;
        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                double dist = Math.sqrt(player.getDistanceSq(sign.getPos()));
                if (dist > 12.0) continue;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    String line = cleanText(sign.signText[i].getUnformattedText());
                    if (!line.isEmpty()) sb.append("[").append(line).append("] ");
                }
                player.sendMessage(new TextComponentString(TextFormatting.GRAY + 
                    String.format("%.1fm: %s tai (%d, %d, %d)", dist, sb.toString(), sign.getPos().getX(), sign.getPos().getY(), sign.getPos().getZ())));
                count++;
            }
        }
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim Debug] Tìm thấy " + count + " bảng."));
    }

    public static void startAimLock(String itemTarget) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (itemTarget == null || itemTarget.trim().isEmpty()) {
            itemTarget = "gunpowder";
        }
        lastItemTarget = itemTarget.trim().toLowerCase();

        boolean found = findTargetSign(lastItemTarget);
        if (found) {
            isAimLockActive = true;
            outOfRangeTicks = 0;
            stuckTicks = 0;
            isWarmupActive = false;

            // Lưu lại vị trí đứng bán ban đầu làm anchor
            if (mc.player != null) {
                anchorPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
            }

            // Đảm bảo game tiếp tục chạy ngầm khi Alt-Tab làm việc khác (F3 + P off)
            if (mc.gameSettings != null) {
                mc.gameSettings.pauseOnLostFocus = false;
            }

            // Tắt sprint ngay khi bật để giữ thanh feed
            mc.player.setSprinting(false);
            String displayName = isGunpowderTarget(lastItemTarget) ? "THUỐC SÚNG (Gunpowder)" : lastItemTarget.toUpperCase();
            mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + 
                "[AutoAim] ĐÃ BẬT GHIM BẢNG [Sell] " + displayName));
            mc.player.sendMessage(new TextComponentString(TextFormatting.AQUA + 
                "-> Camera ghim vào bảng mượt như người thật. Bạn thoải mái đè chuột phải để bán đồ!"));
            mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + 
                "-> Tự bước lại quầy nếu bị đẩy. Chuột hoàn toàn tự do, không bị chiếm chuột."));
        }
    }

    public static void toggleAimLock(String itemTarget) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (isAimLockActive) {
            stopAimLock(mc);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + 
                "[AutoAim] ĐÃ TẮT GHIM CAMERA."));
        } else {
            startAimLock(itemTarget);
        }
    }

    public static void stopAimLock(Minecraft mc) {
        isAimLockActive = false;
        outOfRangeTicks = 0;
        stuckTicks = 0;
        isWarmupActive = false;
        anchorPos = null;
        stopAutoWalk(mc);
    }

    public static void stopAutoWalk(Minecraft mc) {
        isAutoWalking = false;
        if (mc != null && mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    /**
     * Cập nhật góc nhìn mô phỏng 100% chuyển động chuột người thật (Human-like Aiming):
     * - Khi Alt-Tab ra ngoài làm việc khác: camera vẫn bám bảng ngầm, TUYỆT ĐỐI KHÔNG can thiệp chuột Windows
     * - Khi đang trong game và người chơi rê chuột tay: nhường quyền 100% cho người chơi
     */
    private static void updateCameraSmooth(Minecraft mc, float partialTicks) {
        if (!isAimLockActive || targetSignPos == null || mc.player == null || mc.world == null) return;
        // Nếu đang mở menu màn hình (ESC, chat, hòm đồ) -> không can thiệp góc nhìn
        if (mc.currentScreen != null) return;

        // TUYỆT ĐỐI KHÔNG CHIẾM CHUỘT:
        // Khi game đang có Focus VÀ người chơi đang chủ động rê chuột tay, nhường quyền 100% cho người chơi
        if (mc.inGameHasFocus && mc.mouseHelper != null && (Math.abs(mc.mouseHelper.deltaX) > 1 || Math.abs(mc.mouseHelper.deltaY) > 1)) {
            return;
        }

        // Khi người chơi đã ngắm trúng bảng VÀ không bị ai xô đẩy:
        // TUYỆT ĐỐI KHÔNG can thiệp xoay hay rung lắc camera, để người chơi hoàn toàn tự do lia chuột!
        if (isAimingAtSign(mc) && !isAutoWalking) {
            return;
        }

        EntityPlayerSP player = mc.player;

        double eyeX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
        double eyeY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks + (double) player.getEyeHeight();
        double eyeZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;

        IBlockState state = mc.world.getBlockState(targetSignPos);
        AxisAlignedBB bbox = state.getBoundingBox(mc.world, targetSignPos);
        Vec3d target = new Vec3d(
            targetSignPos.getX() + (bbox.minX + bbox.maxX) / 2.0,
            targetSignPos.getY() + 0.35 + yOffset,
            targetSignPos.getZ() + (bbox.minZ + bbox.maxZ) / 2.0
        );

        double dx = eyeX - target.x;
        double dy = eyeY - target.y;
        double dz = eyeZ - target.z;

        float targetYaw = (float) (MathHelper.atan2(dx, -dz) * (180.0 / Math.PI));
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = (float) (MathHelper.atan2(dy, dist) * (180.0 / Math.PI));

        targetYaw = MathHelper.wrapDegrees(targetYaw);

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - player.rotationYaw);
        float deltaPitch = targetPitch - player.rotationPitch;

        boolean aimingAtSign = isAimingAtSign(mc);

        // Mô phỏng độ trễ và gia tốc chuột người thật (Human-like glide & ease-out):
        // 1. Tính bước nhảy góc theo độ nhạy chuột game (Mouse Sensitivity Quantization / GCD)
        float sens = (mc.gameSettings != null) ? mc.gameSettings.mouseSensitivity : 0.5F;
        float f = sens * 0.6F + 0.2F;
        float gcd = f * f * f * 8.0F * 0.15F;

        // 2. Tính gia tốc lướt chuột (glide into sign):
        float totalDiff = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        float smoothFactor;
        if (aimingAtSign) {
            smoothFactor = 0.14F;
        } else {
            if (totalDiff > 60.0F) {
                smoothFactor = 0.18F;
            } else if (totalDiff > 25.0F) {
                smoothFactor = 0.26F;
            } else if (totalDiff > 8.0F) {
                smoothFactor = 0.32F;
            } else {
                smoothFactor = 0.22F; // giảm tốc êm khi sắp cập bến bảng (glide decel)
            }
        }

        float stepYaw = deltaYaw * smoothFactor;
        float stepPitch = deltaPitch * smoothFactor;

        // Giới hạn vận tốc xoay tối đa mỗi frame tránh snap giật góc
        float maxTurnRate = aimingAtSign ? 3.0F : 9.5F;
        stepYaw = MathHelper.clamp(stepYaw, -maxTurnRate, maxTurnRate);
        stepPitch = MathHelper.clamp(stepPitch, -maxTurnRate, maxTurnRate);

        // Làm tròn theo pixel chuột phần cứng để chuyển động 100% tự nhiên không bị giật
        if (gcd > 0.0001F) {
            stepYaw = Math.round(stepYaw / gcd) * gcd;
            stepPitch = Math.round(stepPitch / gcd) * gcd;
        }

        player.rotationYaw += stepYaw;
        player.rotationPitch += stepPitch;
    }

    public static boolean isAimingAtSign(Minecraft mc) {
        if (mc == null || mc.world == null || mc.objectMouseOver == null || targetSignPos == null) return false;
        if (mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return false;
        BlockPos hitPos = mc.objectMouseOver.getBlockPos();
        if (!targetSignPos.equals(hitPos)) return false;

        TileEntity te = mc.world.getTileEntity(hitPos);
        if (te instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) te;
            if (isBuySign(sign)) return false; // Tuyệt đối không coi bảng Buy là bảng Sell!
            if (!isMatchingSellSign(sign, lastItemTarget)) return false;
        }
        return true;
    }

    /**
     * Kiểm tra xem tâm ngắm hiện tại có đang chỉ vào bất kỳ bảng Mua (Buy sign) nào không,
     * hoặc bảng nằm ở dưới (Y < targetSignPos.getY()).
     * Dùng để ngắt chuột phải NGAY LẬP TỨC nếu bị đẩy lệch tâm xuống các bảng phía dưới.
     */
    public static boolean isLookingAtAnyBuySign(Minecraft mc) {
        if (mc == null || mc.world == null || mc.objectMouseOver == null) return false;
        if (mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return false;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        TileEntity te = mc.world.getTileEntity(pos);
        if (te instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) te;
            if (isBuySign(sign)) return true;
            if (targetSignPos != null && pos.getY() < targetSignPos.getY()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bán đồ tự động bằng tương tác gói tin nội bộ của game (Packet / ProcessRightClick)
     * TUYỆT ĐỐI KHÔNG SỬ DỤNG CHUỘT HỆ ĐIỀU HÀNH (OS MOUSE)
     */
    public static boolean clickTargetSign() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.playerController == null || targetSignPos == null) return false;

        // Chỉ click khi tâm ngắm thực sự chỉ vào bảng (chống AntiCheat kiểm tra góc nhìn)
        if (!isAimingAtSign(mc)) {
            return false;
        }

        EnumFacing facing = mc.objectMouseOver.sideHit;
        Vec3d hitVec = mc.objectMouseOver.hitVec;

        mc.playerController.processRightClickBlock(mc.player, mc.world, targetSignPos, facing, hitVec, EnumHand.MAIN_HAND);

        // Vung tay visual trên màn hình (Client-side ONLY) - KHÔNG gửi CPacketAnimation lên server
        // để AntiCheat (GrimAC, Vulcan) không phát hiện BadPackets / AutoClicker
        if (mc.player != null) {
            if (!mc.player.isSwingInProgress || mc.player.swingProgressInt >= 3) {
                mc.player.isSwingInProgress = true;
                mc.player.swingProgressInt = -1;
                mc.player.swingingHand = EnumHand.MAIN_HAND;
            }
        }
        return true;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        updateCameraSmooth(mc, event.renderTickTime);
    }

    /**
     * Kiểm tra xem có người chơi khác đang đứng chắn ngay phía trước mặt/hướng về quầy không
     * -> Nếu chỗ đó có người đứng rồi thì thôi, không đứng chỗ đó nữa (người thật không chen lấn)
     */
    private static boolean isAnotherPlayerBlocking(Minecraft mc, EntityPlayerSP player) {
        if (mc.world == null || player == null || targetSignPos == null) return false;
        double toSignX = (targetSignPos.getX() + 0.5) - player.posX;
        double toSignZ = (targetSignPos.getZ() + 0.5) - player.posZ;
        for (net.minecraft.entity.player.EntityPlayer other : mc.world.playerEntities) {
            if (other == null || other == player) continue;
            double d = player.getDistance(other);
            if (d < 1.3) {
                double dx = other.posX - player.posX;
                double dz = other.posZ - player.posZ;
                double dot = dx * toSignX + dz * toSignZ;
                if (dot > 0) {
                    return true; // Có người chơi đang đứng chắn phía trước
                }
            }
        }
        return false;
    }

    /**
     * Can thiệp trực tiếp vào Input movement của Minecraft để tự động di chuyển mượt mà
     * TUYỆT ĐỐI KHÔNG SPRINT -> KHÔNG TIÊU HAO ĐỘ ĐÓI (FEED)
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isAimLockActive || targetSignPos == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return; // Không can thiệp nếu đang mở GUI/Chat

        if (isAutoWalking && !isWarmupActive && targetSignPos != null && mc.player != null) {
            // Nếu đã ngắm trúng bảng bán thì tuyệt đối không di chuyển
            if (isAimingAtSign(mc)) {
                return;
            }

            EntityPlayerSP player = mc.player;
            double dx = player.posX - (targetSignPos.getX() + 0.5);
            double dz = player.posZ - (targetSignPos.getZ() + 0.5);
            double distXZ = Math.sqrt(dx * dx + dz * dz);

            boolean playerBlockingAhead = isAnotherPlayerBlocking(mc, player);

            if (distXZ > 2.2) {
                // Nếu bị đẩy lùi khỏi quầy: bước xích lại gần quầy
                double toSignX = (targetSignPos.getX() + 0.5) - player.posX;
                double toSignZ = (targetSignPos.getZ() + 0.5) - player.posZ;
                float moveAngle = (float) (MathHelper.atan2(toSignZ, toSignX) * (180.0D / Math.PI)) - 90.0F;
                float diff = MathHelper.wrapDegrees(moveAngle - player.rotationYaw);

                if (Math.abs(diff) <= 60.0F) {
                    event.getMovementInput().moveForward = 0.8F;
                    event.getMovementInput().forwardKeyDown = true;
                    event.getMovementInput().moveStrafe = 0.0F;
                } else if (diff > 60.0F && diff < 120.0F) {
                    event.getMovementInput().moveForward = 0.2F;
                    event.getMovementInput().moveStrafe = -0.8F;
                } else if (diff < -60.0F && diff > -120.0F) {
                    event.getMovementInput().moveForward = 0.2F;
                    event.getMovementInput().moveStrafe = 0.8F;
                } else {
                    event.getMovementInput().moveForward = -0.8F;
                }
            } else if (playerBlockingAhead) {
                // Cự ly đủ gần nhưng có người chắn tầm nhìn: nhích ngang tìm góc hở
                event.getMovementInput().moveForward = 0.0F;
                event.getMovementInput().moveStrafe = 0.6F;
            }

            mc.player.setSprinting(false);
            if (mc.gameSettings != null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isAimLockActive) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.gameSettings == null) return;
        if (mc.currentScreen != null) return; // Tạm dừng nếu đang mở chat/menu

        EntityPlayerSP player = mc.player;

        // Quét tự động chuyển bảng mỗi 5 tick (0.25s) khi người chơi bước sang bảng khác
        if (player.ticksExisted % 5 == 0) {
            scanAndSwitchTargetSign(mc);
        }

        if (targetSignPos == null) {
            if (isAutoWalking) stopAutoWalk(mc);
            return;
        }

        // ĐIỀU KIỆN 100% NGƯỜI THẬT:
        // "miễn sao vẫn trong tầm ngắm thì ko di chuyển gì hết"
        // "nếu ra khỏi tầm nhắm bán ko lên tiền thì mới bắt đầu di chuyển"
        if (isAimingAtSign(mc)) {
            // ĐANG TRONG TẦM NGẮM BÁN ĐƯỢC TIỀN: ĐỨNG YÊN HOÀN TOÀN!
            if (isAutoWalking) {
                stopAutoWalk(mc);
            }
            outOfRangeTicks = 0;
            stuckTicks = 0;
        } else {
            // RA KHỎI TẦM NGẮM (bị đẩy ra quá xa hoặc bị người khác che chắn không bán được):
            if (autoReturnEnabled && !isWarmupActive) {
                outOfRangeTicks++;

                // Phản xạ tự nhiên: sau 8 tick (0.4s) không ngắm được bảng mới bắt đầu di chuyển tìm lại góc ngắm
                if (!isAutoWalking && outOfRangeTicks >= 8) {
                    isAutoWalking = true;
                    stuckTicks = 0;
                    lastCheckX = player.posX;
                    lastCheckZ = player.posZ;
                }

                if (isAutoWalking) {
                    player.setSprinting(false);

                    // Khi vừa vào lại tầm ngắm trúng bảng: DỪNG LẠI NGAY LẬP TỨC!
                    if (isAimingAtSign(mc)) {
                        stopAutoWalk(mc);
                        outOfRangeTicks = 0;
                        stuckTicks = 0;
                    } else if (player.collidedHorizontally) {
                        double moved = Math.sqrt(Math.pow(player.posX - lastCheckX, 2) + Math.pow(player.posZ - lastCheckZ, 2));
                        if (moved < 0.03) {
                            stuckTicks++;
                        }
                        if (stuckTicks >= 6) {
                            stopAutoWalk(mc);
                            outOfRangeTicks = 0;
                            stuckTicks = 0;
                        }
                    } else {
                        stuckTicks = 0;
                    }
                    lastCheckX = player.posX;
                    lastCheckZ = player.posZ;
                }
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        if (event.getMessage() == null) return;
        String text = cleanText(event.getMessage().getUnformattedText()).toLowerCase();
        if (text.contains("dịch chuyển sẽ diễn ra") || text.contains("đừng chạy nhảy")) {
            isWarmupActive = true;
            stopAutoWalk(Minecraft.getMinecraft());
        } else if (text.contains("yêu cầu dịch chuyển đã bị hủy") || text.contains("đã dịch chuyển") || text.contains("mở kho")) {
            isWarmupActive = false;
        }
    }
}
