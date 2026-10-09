package com.minclient.gui;

import com.minclient.gui.component.Frame;
import com.minclient.module.Module;
import com.minclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ImpactHudRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager moduleManager;

    public ImpactHudRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public void render(RenderGameOverlayEvent.Text event) {
        if (mc.gameSettings.showDebugInfo) return;

        FontRenderer fr = mc.fontRenderer;
        ScaledResolution sr = new ScaledResolution(mc);

        // 1. Watermark chuẩn Impact ở góc trên bên trái
        String watermark = "\u00A79Impact \u00A774.9.1";
        fr.drawStringWithShadow(watermark, 4, 4, 0xFFFFFFFF);

        // 2. Vẽ các Frame được Pinned trên màn hình
        for (Frame frame : ImpactClickGui.getFrames()) {
            if (frame.isPinned()) {
                frame.renderHUD();
            }
        }

        // 3. ArrayList (Active Modules) ở góc trên bên phải
        List<Module> enabledMods = new ArrayList<>();
        for (Module mod : moduleManager.getModules()) {
            if (mod.isEnabled()) {
                enabledMods.add(mod);
            }
        }

        // Sắp xếp theo chiều dài tên giảm dần
        enabledMods.sort(Comparator.comparingInt((Module m) -> fr.getStringWidth(m.getName())).reversed());

        int y = 4;
        int screenWidth = sr.getScaledWidth();

        for (Module mod : enabledMods) {
            String name = mod.getName();
            int strWidth = fr.getStringWidth(name);
            int x = screenWidth - strWidth - 6;

            // Nền tối mờ phía sau text
            Gui.drawRect(x - 2, y - 1, screenWidth, y + 9, 0x90000000);

            // Đường chỉ xanh Impact 1px bên phải
            Gui.drawRect(screenWidth - 2, y - 1, screenWidth, y + 9, 0xFF2979FF);

            // Tên module màu trắng
            fr.drawStringWithShadow(name, x, y, 0xFFFFFFFF);

            y += 10;
        }
    }
}
