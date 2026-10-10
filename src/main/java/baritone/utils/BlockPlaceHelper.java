/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

import java.lang.reflect.Method;

public class BlockPlaceHelper {

    private final IPlayerContext ctx;
    private int rightClickTimer;
    private boolean isHoldingKeyUse;
    private static Method rightClickMouseMethod;

    static {
        try {
            rightClickMouseMethod = Minecraft.class.getDeclaredMethod("rightClickMouse");
            rightClickMouseMethod.setAccessible(true);
        } catch (Throwable t1) {
            try {
                // Obfuscated / SRG name in 1.12.2
                rightClickMouseMethod = Minecraft.class.getDeclaredMethod("func_147121_ag");
                rightClickMouseMethod.setAccessible(true);
            } catch (Throwable t2) {
                rightClickMouseMethod = null;
            }
        }
    }

    BlockPlaceHelper(IPlayerContext playerContext) {
        this.ctx = playerContext;
    }

    public void release() {
        if (isHoldingKeyUse) {
            isHoldingKeyUse = false;
            try {
                if (ctx.minecraft() != null && ctx.minecraft().gameSettings != null) {
                    KeyBinding key = ctx.minecraft().gameSettings.keyBindUseItem;
                    KeyBinding.setKeyBindState(key.getKeyCode(), false);
                }
            } catch (Throwable ignored) {}
        }
    }

    public void tick(boolean rightClickRequested) {
        // Multi-client background support: prevent game pause/ESC menu when alt-tabbed
        try {
            if (ctx.minecraft() != null && ctx.minecraft().gameSettings != null) {
                ctx.minecraft().gameSettings.pauseOnLostFocus = false;
            }
        } catch (Throwable ignored) {}

        // When chat, inventory or any GUI is open, genuine mouse input cannot place blocks in the world
        if (ctx.minecraft() != null && ctx.minecraft().currentScreen != null) {
            release();
            return;
        }

        if (!rightClickRequested) {
            release();
            return;
        }

        // Simulate holding down right-click keybind like a real player
        try {
            if (ctx.minecraft() != null && ctx.minecraft().gameSettings != null) {
                KeyBinding key = ctx.minecraft().gameSettings.keyBindUseItem;
                KeyBinding.setKeyBindState(key.getKeyCode(), true);
                KeyBinding.onTick(key.getKeyCode());
                isHoldingKeyUse = true;
            }
        } catch (Throwable ignored) {}

        if (rightClickTimer > 0) {
            rightClickTimer--;
            return;
        }
        RayTraceResult mouseOver = ctx.objectMouseOver();
        if (ctx.player().isRowingBoat() || mouseOver == null || mouseOver.getBlockPos() == null || mouseOver.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        int speed = Baritone.settings().rightClickSpeed.value;
        rightClickTimer = Math.max(speed, 4); // Clamp to at least 4 ticks to prevent server anticheat FastPlace kicks

        // Execute genuine Windows hardware right click
        WindowsMouse.clickRight();

        // Execute genuine vanilla Minecraft rightClickMouse() logic
        boolean invokedVanilla = false;
        if (rightClickMouseMethod != null) {
            try {
                rightClickMouseMethod.invoke(ctx.minecraft());
                invokedVanilla = true;
            } catch (Throwable ignored) {}
        }

        // Fallback only if reflection failed
        if (!invokedVanilla) {
            for (EnumHand hand : EnumHand.values()) {
                if (ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), mouseOver.getBlockPos(), mouseOver.sideHit, mouseOver.hitVec, hand) == EnumActionResult.SUCCESS) {
                    ctx.player().swingArm(hand);
                    return;
                }
                if (!ctx.player().getHeldItem(hand).isEmpty() && ctx.playerController().processRightClick(ctx.player(), ctx.world(), hand) == EnumActionResult.SUCCESS) {
                    return;
                }
            }
        }
    }
}
