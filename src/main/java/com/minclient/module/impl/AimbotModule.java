package com.minclient.module.impl;

import com.minclient.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AimbotModule - Mô-đun ngắm mục tiêu tự động (Aimbot) cho Minecraft 1.12.2.
 * 
 * Toàn bộ logic được phục hồi và thiết kế dạng module sạch, độc lập, có thể tùy chỉnh.
 * Bạn có thể dễ dàng sửa đổi các công thức tính góc, cơ chế lọc hoặc cách xoay camera tại đây.
 */
public class AimbotModule extends Module {

    // =========================================================================
    // 1. CÁC THAM SỐ CẤU HÌNH (BẠN CÓ THỂ CHỈNH SỬA THEO NHU CẦU)
    // =========================================================================

    /** Tầm ngắm tối đa (đơn vị: block). Mặc định 4.5 blocks */
    public float range = 4.5f;

    /** Góc nhìn tối đa quanh tâm ngắm (FOV). 360 = ngắm mọi hướng, 90 = chỉ ngắm phía trước */
    public float fov = 120.0f;

    /** Bật/tắt xoay mượt (Smooth Aim) để tránh giật camera bất thường */
    public boolean smooth = true;

    /** Tốc độ xoay mỗi tick (độ/tick). Càng nhỏ xoay càng chậm và tự nhiên, càng lớn càng nhanh */
    public float smoothSpeed = 15.0f;

    /** Cho phép ngắm xuyên qua khối block (bỏ qua kiểm tra che khuất tầm nhìn) */
    public boolean throughWalls = false;

    /** Bộ lọc đối tượng */
    public boolean targetPlayers = true;
    public boolean targetMobs = true;
    public boolean targetPassives = false;

    /** Vị trí ngắm trên cơ thể mục tiêu: HEAD (Đầu), CHEST (Ngực/Thân), FEET (Chân) */
    public enum AimPart {
        HEAD, CHEST, FEET
    }
    public AimPart aimPart = AimPart.CHEST;

    /** Chế độ ưu tiên mục tiêu: DISTANCE (Gần nhất), HEALTH (Ít máu nhất), FOV (Gần tâm ngắm nhất) */
    public enum PriorityMode {
        DISTANCE, HEALTH, FOV
    }
    public PriorityMode priorityMode = PriorityMode.DISTANCE;

    // Mục tiêu hiện tại đang bị khóa
    private EntityLivingBase currentTarget = null;

    public AimbotModule() {
        super("Aimbot", "Tự động khóa hướng nhìn (camera) vào mục tiêu tối ưu gần nhất", Category.MODULES, false);
    }

    @Override
    public void onDisable() {
        this.currentTarget = null;
    }

    // =========================================================================
    // 2. VÒNG LẶP THỰC THI (CHẠY MỖI TICK KHI MODULE BẬT)
    // =========================================================================
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            currentTarget = null;
            return;
        }

        // 1. Tìm kiếm mục tiêu hợp lệ tốt nhất
        currentTarget = findBestTarget();
        if (currentTarget == null) {
            return;
        }

        // 2. Tính toán tọa độ 3D cần ngắm tới (Đầu, Ngực, hoặc Chân)
        Vec3d targetPoint = getTargetPoint(currentTarget);

        // 3. Tính toán góc quay mong muốn [Yaw, Pitch]
        float[] desiredRotations = calculateRotations(targetPoint);

        // 4. Áp dụng góc quay vào Camera của người chơi (Trực tiếp hoặc Xoay mượt)
        applyRotations(desiredRotations[0], desiredRotations[1]);
    }

    // =========================================================================
    // 3. THUẬT TOÁN TÌM KIẾM & BỘ LỌC MỤC TIÊU (TARGET SELECTOR)
    // =========================================================================
    private EntityLivingBase findBestTarget() {
        List<EntityLivingBase> candidates = mc.world.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(this::isValidTarget)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        // Sắp xếp theo chế độ ưu tiên đã chọn
        switch (priorityMode) {
            case HEALTH:
                candidates.sort(Comparator.comparingDouble(EntityLivingBase::getHealth));
                break;
            case FOV:
                candidates.sort(Comparator.comparingDouble(this::getAngleToEntity));
                break;
            case DISTANCE:
            default:
                candidates.sort(Comparator.comparingDouble(e -> mc.player.getDistanceSq(e)));
                break;
        }

        return candidates.get(0);
    }

    /**
     * Kiểm tra một Entity có thỏa mãn điều kiện làm mục tiêu hay không.
     */
    private boolean isValidTarget(EntityLivingBase entity) {
        // Bỏ qua bản thân người chơi
        if (entity == mc.player) return false;

        // Bỏ qua mục tiêu đã chết
        if (entity.isDead || entity.getHealth() <= 0) return false;

        // Kiểm tra khoảng cách
        if (mc.player.getDistance(entity) > range) return false;

        // Phân loại thực thể
        if (entity instanceof EntityPlayer && !targetPlayers) return false;
        if (entity instanceof EntityMob && !targetMobs) return false;
        if (entity instanceof EntityAnimal && !targetPassives) return false;

        // Kiểm tra góc nhìn FOV
        if (getAngleToEntity(entity) > fov / 2.0f) return false;

        // Kiểm tra tầm nhìn (Line of Sight / Raycast) nếu không cho phép xuyên tường
        if (!throughWalls && !canEntityBeSeen(entity)) return false;

        return true;
    }

    /**
     * Bắn tia Raytrace để kiểm tra có khối block chắn giữa người chơi và mục tiêu hay không.
     */
    private boolean canEntityBeSeen(EntityLivingBase target) {
        Vec3d eyePos = mc.player.getPositionEyes(1.0F);
        Vec3d targetPos = getTargetPoint(target);

        RayTraceResult result = mc.world.rayTraceBlocks(eyePos, targetPos, false, true, false);
        return result == null; // null nghĩa là đường ngắm thông suốt không chạm block
    }

    // =========================================================================
    // 4. TOÁN HỌC VECTOR & TÍNH GÓC QUAY (YAW / PITCH ROTATIONS)
    // =========================================================================

    /**
     * Lấy tọa độ vector 3D của vị trí ngắm trên cơ thể mục tiêu.
     */
    private Vec3d getTargetPoint(EntityLivingBase target) {
        double yOffset;
        switch (aimPart) {
            case HEAD:
                yOffset = target.getEyeHeight();
                break;
            case FEET:
                yOffset = 0.1;
                break;
            case CHEST:
            default:
                yOffset = target.height / 2.0;
                break;
        }
        return new Vec3d(target.posX, target.posY + yOffset, target.posZ);
    }

    /**
     * Tính toán góc Yaw và Pitch chuẩn từ vị trí mắt người chơi đến vị trí đích.
     */
    public float[] calculateRotations(Vec3d targetPoint) {
        Vec3d eyes = mc.player.getPositionEyes(1.0F);

        double diffX = targetPoint.x - eyes.x;
        double diffY = targetPoint.y - eyes.y;
        double diffZ = targetPoint.z - eyes.z;

        double horizontalDistance = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);

        // Yaw: Góc xoay quanh trục Y (trục đứng)
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;

        // Pitch: Góc ngẩng/cúi quanh trục ngang
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontalDistance));

        return new float[]{
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0F, 90.0F)
        };
    }

    /**
     * Tính góc lệch (độ) giữa hướng nhìn hiện tại của người chơi và mục tiêu.
     */
    private float getAngleToEntity(Entity target) {
        float[] rots = calculateRotations(getTargetPoint((EntityLivingBase) target));
        float yawDiff = MathHelper.abs(MathHelper.wrapDegrees(rots[0] - mc.player.rotationYaw));
        float pitchDiff = MathHelper.abs(MathHelper.wrapDegrees(rots[1] - mc.player.rotationPitch));
        return (yawDiff + pitchDiff) / 2.0F;
    }

    // =========================================================================
    // 5. ÁP DỤNG GÓC QUAY VÀO CLIENT (SMOOTH INTERPOLATION HOẶC SNAP)
    // =========================================================================
    private void applyRotations(float targetYaw, float targetPitch) {
        if (!smooth) {
            // Khóa góc tức thời (Snap)
            mc.player.rotationYaw = targetYaw;
            mc.player.rotationPitch = targetPitch;
        } else {
            // Xoay mượt dần đều (Smooth Interpolation)
            float yawDelta = MathHelper.wrapDegrees(targetYaw - mc.player.rotationYaw);
            float pitchDelta = targetPitch - mc.player.rotationPitch;

            // Giới hạn bước nhảy mỗi tick không vượt quá smoothSpeed
            float stepYaw = MathHelper.clamp(yawDelta, -smoothSpeed, smoothSpeed);
            float stepPitch = MathHelper.clamp(pitchDelta, -smoothSpeed, smoothSpeed);

            mc.player.rotationYaw += stepYaw;
            mc.player.rotationPitch += stepPitch;
        }
    }

    public EntityLivingBase getCurrentTarget() {
        return currentTarget;
    }
}
