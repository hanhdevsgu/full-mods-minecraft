package com.minclient.pathfinding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class PathRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void render(RenderWorldLastEvent event, MotorController motorController) {
        if (!motorController.isRunning()) {
            return;
        }

        List<BetterBlockPos> path = motorController.getCurrentPath();
        if (path == null || path.isEmpty()) {
            return;
        }

        double renderX = mc.getRenderManager().viewerPosX;
        double renderY = mc.getRenderManager().viewerPosY;
        double renderZ = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(3.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // 1. Vẽ đường line kết nối các node (Màu xanh neon)
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Nối từ chân người chơi tới node tiếp theo
        Vec3d playerPos = mc.player.getPositionVector();
        buffer.pos(playerPos.x - renderX, playerPos.y - renderY + 0.1, playerPos.z - renderZ)
                .color(0.0F, 1.0F, 0.5F, 0.9F).endVertex();

        int startIndex = Math.max(0, motorController.getCurrentStepIndex());
        for (int i = startIndex; i < path.size(); i++) {
            BetterBlockPos p = path.get(i);
            buffer.pos((p.x + 0.5) - renderX, (p.y + 0.1) - renderY, (p.z + 0.5) - renderZ)
                    .color(0.2F, 0.8F, 1.0F, 0.85F).endVertex();
        }
        tessellator.draw();

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
