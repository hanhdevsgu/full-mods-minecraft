package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;
import com.minclient.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

public class KillAuraModule extends Module {
    private final NumberSetting range = new NumberSetting("Range", 4.2, 3.0, 6.0, 0.1);
    private final NumberSetting delay = new NumberSetting("Delay", 10.0, 1.0, 20.0, 1.0);
    private final BooleanSetting players = new BooleanSetting("Players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", false);
    private int tickTimer = 0;

    public KillAuraModule() {
        super("KillAura", "Tự động tấn công các mục tiêu xung quanh trong tầm đánh", Category.COMBAT, false);
        addSetting(range);
        addSetting(delay);
        addSetting(players);
        addSetting(mobs);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        tickTimer++;
        if (tickTimer < delay.getValue()) return;

        double targetRange = range.getValue();
        EntityLivingBase target = null;
        double closestDist = targetRange;

        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == mc.player) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || living.getHealth() <= 0) continue;

            if (living instanceof EntityPlayer && !players.getValue()) continue;
            if (living instanceof IMob && !mobs.getValue()) continue;

            double dist = mc.player.getDistance(living);
            if (dist <= closestDist) {
                closestDist = dist;
                target = living;
            }
        }

        if (target != null && mc.playerController != null) {
            mc.playerController.attackEntity(mc.player, target);
            mc.player.swingArm(EnumHand.MAIN_HAND);
            tickTimer = 0;
        }
    }
}
