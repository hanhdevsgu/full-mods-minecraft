package com.minclient.gui;

import com.minclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public class WorldRenderHelper {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void renderWorld(RenderWorldLastEvent event, ModuleManager moduleManager) {
        if (mc.player == null || mc.world == null) return;

        double viewX = mc.getRenderManager().viewerPosX;
        double viewY = mc.getRenderManager().viewerPosY;
        double viewZ = mc.getRenderManager().viewerPosZ;

        // Render ESP & Tracers
        boolean espEnabled = moduleManager.getEspModule().isEnabled();
        boolean tracersEnabled = moduleManager.getTracersModule().isEnabled();

        if (espEnabled || tracersEnabled) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.5F);

            for (Entity entity : mc.world.loadedEntityList) {
                if (entity == mc.player) continue;

                boolean isPlayer = entity instanceof EntityPlayer;
                boolean isMob = entity instanceof IMob;
                boolean isItem = entity instanceof EntityItem;

                if (isPlayer && !moduleManager.getEspModule().isPlayers() && !moduleManager.getTracersModule().isPlayers()) continue;
                if (isMob && !moduleManager.getEspModule().isMobs() && !moduleManager.getTracersModule().isMobs()) continue;
                if (isItem && !moduleManager.getEspModule().isItems()) continue;

                float r = 0.2F, g = 0.6F, b = 1.0F; // Mặc định xanh Impact
                if (isPlayer) {
                    r = 1.0F; g = 0.3F; b = 0.3F; // Đỏ cho Player
                } else if (isMob) {
                    r = 1.0F; g = 0.6F; b = 0.0F; // Cam cho Mob
                } else if (isItem) {
                    r = 1.0F; g = 1.0F; b = 0.2F; // Vàng cho Item
                }

                double interpX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * event.getPartialTicks();
                double interpY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * event.getPartialTicks();
                double interpZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * event.getPartialTicks();

                // Vẽ ESP Box
                if (espEnabled) {
                    AxisAlignedBB bb = entity.getEntityBoundingBox();
                    AxisAlignedBB renderBB = new AxisAlignedBB(
                            bb.minX - entity.posX + interpX - viewX,
                            bb.minY - entity.posY + interpY - viewY,
                            bb.minZ - entity.posZ + interpZ - viewZ,
                            bb.maxX - entity.posX + interpX - viewX,
                            bb.maxY - entity.posY + interpY - viewY,
                            bb.maxZ - entity.posZ + interpZ - viewZ
                    );

                    RenderGlobal.drawSelectionBoundingBox(renderBB, r, g, b, 0.8F);
                }

                // Vẽ Tracers
                if (tracersEnabled && (isPlayer || isMob)) {
                    Vec3d eyeVector = new Vec3d(0, 0, 1)
                            .rotatePitch((float) -Math.toRadians(mc.player.rotationPitch))
                            .rotateYaw((float) -Math.toRadians(mc.player.rotationYaw));

                    GL11.glColor4f(r, g, b, 0.8F);
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex3d(eyeVector.x, eyeVector.y + mc.player.getEyeHeight(), eyeVector.z);
                    GL11.glVertex3d(interpX - viewX, interpY - viewY + (entity.height / 2.0), interpZ - viewZ);
                    GL11.glEnd();
                }
            }

            // Storage ESP
            if (moduleManager.getStorageEspModule().isEnabled()) {
                for (TileEntity te : mc.world.loadedTileEntityList) {
                    if (te instanceof TileEntityChest && moduleManager.getStorageEspModule().isChests()) {
                        AxisAlignedBB box = new AxisAlignedBB(
                                te.getPos().getX() - viewX,
                                te.getPos().getY() - viewY,
                                te.getPos().getZ() - viewZ,
                                te.getPos().getX() + 1 - viewX,
                                te.getPos().getY() + 1 - viewY,
                                te.getPos().getZ() + 1 - viewZ
                        );
                        RenderGlobal.drawSelectionBoundingBox(box, 1.0F, 0.6F, 0.0F, 0.7F);
                    } else if (te instanceof TileEntityShulkerBox && moduleManager.getStorageEspModule().isShulkers()) {
                        AxisAlignedBB box = new AxisAlignedBB(
                                te.getPos().getX() - viewX,
                                te.getPos().getY() - viewY,
                                te.getPos().getZ() - viewZ,
                                te.getPos().getX() + 1 - viewX,
                                te.getPos().getY() + 1 - viewY,
                                te.getPos().getZ() + 1 - viewZ
                        );
                        RenderGlobal.drawSelectionBoundingBox(box, 0.8F, 0.2F, 0.8F, 0.7F);
                    }
                }
            }

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }
}
