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

import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

import java.lang.reflect.Method;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {

    private final IPlayerContext ctx;
    private boolean didBreakLastTick;
    private boolean isHoldingKeyAttack;
    private static Method clickMouseMethod;
    private static Method sendClickBlockToControllerMethod;

    static {
        try {
            clickMouseMethod = Minecraft.class.getDeclaredMethod("clickMouse");
            clickMouseMethod.setAccessible(true);
        } catch (Throwable t1) {
            try {
                // Obfuscated / SRG name in 1.12.2
                clickMouseMethod = Minecraft.class.getDeclaredMethod("func_147116_af");
                clickMouseMethod.setAccessible(true);
            } catch (Throwable t2) {
                clickMouseMethod = null;
            }
        }

        try {
            sendClickBlockToControllerMethod = Minecraft.class.getDeclaredMethod("sendClickBlockToController", boolean.class);
            sendClickBlockToControllerMethod.setAccessible(true);
        } catch (Throwable t1) {
            try {
                // Obfuscated / SRG name in 1.12.2
                sendClickBlockToControllerMethod = Minecraft.class.getDeclaredMethod("func_147115_a", boolean.class);
                sendClickBlockToControllerMethod.setAccessible(true);
            } catch (Throwable t2) {
                sendClickBlockToControllerMethod = null;
            }
        }
    }

    BlockBreakHelper(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    public void stopBreakingBlock() {
        WindowsMouse.releaseLeft();
        if (!isHoldingKeyAttack) {
            return;
        }
        isHoldingKeyAttack = false;
        try {
            if (ctx.minecraft() != null && ctx.minecraft().gameSettings != null) {
                KeyBinding key = ctx.minecraft().gameSettings.keyBindAttack;
                KeyBinding.setKeyBindState(key.getKeyCode(), false);
            }
        } catch (Throwable ignored) {}

        if (sendClickBlockToControllerMethod != null) {
            try {
                sendClickBlockToControllerMethod.invoke(ctx.minecraft(), false);
            } catch (Throwable ignored) {}
        }

        if (ctx.playerController() != null) {
            ctx.playerController().resetBlockRemoving();
        }
        didBreakLastTick = false;
    }

    public void tick(boolean isLeftClick) {
        // Multi-client background support: prevent game pause/ESC menu when alt-tabbed
        try {
            if (ctx.minecraft() != null && ctx.minecraft().gameSettings != null) {
                ctx.minecraft().gameSettings.pauseOnLostFocus = false;
            }
        } catch (Throwable ignored) {}

        // When chat, inventory or any GUI is open, genuine mouse input cannot break blocks in the world
        if (ctx.minecraft() != null && ctx.minecraft().currentScreen != null) {
            stopBreakingBlock();
            return;
        }

        if (!isLeftClick) {
            stopBreakingBlock();
            return;
        }

        // Genuine Windows hardware left click
        WindowsMouse.pressLeft();

        KeyBinding key = (ctx.minecraft() != null && ctx.minecraft().gameSettings != null)
                ? ctx.minecraft().gameSettings.keyBindAttack
                : null;
        if (key == null) {
            return;
        }

        if (!isHoldingKeyAttack) {
            KeyBinding.setKeyBindState(key.getKeyCode(), true);
            KeyBinding.onTick(key.getKeyCode());
            isHoldingKeyAttack = true;

            // Trigger initial clickMouse() once when pressing the button (vanilla mouse down event)
            if (clickMouseMethod != null) {
                try {
                    clickMouseMethod.invoke(ctx.minecraft());
                } catch (Throwable ignored) {}
            }
            didBreakLastTick = true;
        } else {
            // Keep holding key down continuously without releasing
            KeyBinding.setKeyBindState(key.getKeyCode(), true);
            KeyBinding.onTick(key.getKeyCode());

            // If game window is unfocused (alt-tabbed background), drive sendClickBlockToController
            if (ctx.minecraft() != null && !ctx.minecraft().inGameHasFocus && sendClickBlockToControllerMethod != null) {
                try {
                    sendClickBlockToControllerMethod.invoke(ctx.minecraft(), true);
                } catch (Throwable ignored) {}
            }
        }
    }
}
