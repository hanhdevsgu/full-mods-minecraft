package com.zorotool.macrorecorder.gui;

import com.zorotool.macrorecorder.ConfigHelper;
import com.zorotool.macrorecorder.MacroRecorderMod;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * GUI F8 - giu day du cac tuy chinh quan trong cua ban PTD/FILE,
 * nhung KHONG co che do Dung chung/Dung rieng.
 *
 * ===== LAYOUT "AO" (design canvas) - fix loi "xich lai/de len nhau" khi thu nho man hinh =====
 * Toan bo vi tri (label/o nhap/nut) duoc tinh SAN theo 1 khung toa do CO DINH
 * (DESIGN_W x DESIGN_H), KHONG phu thuoc this.width/this.height. Khi ve
 * (drawScreen) va khi bat click (mouseClicked), ca khung nay duoc CO GIAN
 * (scale) + CAN GIUA (offset) theo kich thuoc man hinh THAT te qua
 * computeLayout(): scale <= 1.0 nen man hinh cang nho thi GUI cang thu nho
 * theo CUNG 1 ty le - KHONG con truong hop tung phan tu tu clamp rieng le
 * (truoc day moi nut/nhan Math.min() rieng, man hinh nho la cac hang de/xich
 * vao nhau nhin roi). Man hinh du lon thi scale = 1.0 (dung y het kich thuoc
 * cu, khong bi phong to qua muc).
 */
public class GuiMacroConfig extends GuiScreen {

    // ===== Khung toa do "ao" - moi vi tri ben duoi deu tinh theo khung nay =====
    // Tat ca moc doc (Y) deu la HANG SO tinh SAN 1 lan (khong con tu tinh rai
    // rac o ca initGui() lan drawScreen() - do la nguyen nhan gay lech/de
    // hang truoc day). MAX_ROWS phai khop voi so hang NHIEU HON trong 2 cot
    // (leftRows con 8 hang sau khi bo o "Delay trước #goto tới shop" khong con
    // dung nua, rightRows van 9 hang -> MAX_ROWS = max(8,9) = 9).
    private static final int FIELD_W = 210;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 30; // ~FIELD_H(20) + khoang cach du de label KHONG de len o phia truoc
    private static final int LEFT_X = 18;
    private static final int RIGHT_X = LEFT_X + FIELD_W + 44; // = 272
    private static final int FIRST_FIELD_Y = 40;
    private static final int MAX_ROWS = 8;
    private static final int FIELDS_BOTTOM_Y = FIRST_FIELD_Y + MAX_ROWS * ROW_H;
    private static final int BUTTON_Y = FIELDS_BOTTOM_Y + 12;
    private static final int FOOTER_Y1 = BUTTON_Y + 26;
    private static final int FOOTER_Y2 = FOOTER_Y1 + 14;
    private static final int FOOTER_Y3 = FOOTER_Y2 + 14;

    private static final int DESIGN_W = RIGHT_X + FIELD_W + 20; // le phai ~20px sau cot phai
    private static final int DESIGN_H = FOOTER_Y3 + 22; // le duoi ~22px sau dong footer cuoi

    // scale toi thieu - KHONG cho GUI nho hon muc nay du man hinh nho co nao,
    // de chu luon doc duoc (truoc de 0.35 la qua nho, mo len khong thay chu).
    private static final float MIN_SCALE = 0.6f;

    // Ty le + do lech (tinh lai moi frame trong computeLayout(), dua tren
    // this.width/this.height THAT su cua cua so game luc do).
    private float scale = 1.0f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    private GuiTextField farmItemIdField;
    private GuiTextField vaultCountField;
    private GuiTextField chestPvDelayField;
    private GuiTextField moveDelayField;
    private GuiTextField queueDistanceThresholdField;
    private GuiTextField nextChestGotoField;
    private GuiTextField sethomeCommandField;
    private GuiTextField homeMiaCommandField;

    private GuiTextField beforeShopDelayField;
    private GuiTextField shopToSethomeDelayField;
    private GuiTextField shopGotoField;
    private GuiTextField shopLookField;
    private GuiTextField afterShopClickDelayField;
    private GuiTextField afterShopGotoField;
    private GuiTextField afterShopLookField;

    private GuiButton saveButton;
    private GuiButton cancelButton;
    private GuiButton startButton;

    // Cac hang trong 2 cot, dung de ve label + dat vi tri o nhap - GOM CHUNG
    // 1 cho de them/bot 1 dong la tu dong xep lai, khong con phai tu tay sua
    // tung "row * N" rai rac nhu truoc (nguyen nhan chinh gay de/xich hang).
    private Row[] leftRows;
    private Row[] rightRows;

    private static final class Row {
        final String label;
        final GuiTextField field;
        Row(String label, GuiTextField field) {
            this.label = label;
            this.field = field;
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        ConfigHelper.reloadConfig();

        farmItemIdField = makeField(0, String.valueOf(ConfigHelper.getFarmItemId()), 6);
        vaultCountField = makeField(1, String.valueOf(ConfigHelper.getVaults()), 4);
        chestPvDelayField = makeField(2, String.valueOf(ConfigHelper.getChestPvDelayMs()), 6);
        moveDelayField = makeField(3, formatDelay(ConfigHelper.getMoveDelaySeconds()), 8);
        queueDistanceThresholdField = makeField(6, formatDelay(ConfigHelper.getQueueDistanceThreshold()), 8);
        nextChestGotoField = makeField(16, ConfigHelper.getNextChestGotoCommand(), 64);
        sethomeCommandField = makeField(17, ConfigHelper.getSethomeCommand(), 32);
        homeMiaCommandField = makeField(18, ConfigHelper.getHomeMiaCommand(), 32);

        beforeShopDelayField = makeField(12, formatDelay(ConfigHelper.getBeforeShopDelaySeconds()), 8);
        shopToSethomeDelayField = makeField(19, formatDelay(ConfigHelper.getShopToSethomeDelaySeconds()), 8);
        shopGotoField = makeField(10, ConfigHelper.getShopGotoCommand(), 64);
        shopLookField = makeField(11, ConfigHelper.getShopLookCommand(), 64);
        afterShopClickDelayField = makeField(5, formatDelay(ConfigHelper.getAfterShopClickDelaySeconds()), 8);
        afterShopGotoField = makeField(13, ConfigHelper.getAfterShopGotoCommand(), 64);
        afterShopLookField = makeField(15, ConfigHelper.getAfterShopLookCommand(), 64);

        leftRows = new Row[] {
                new Row("ID vật phẩm farm (F3+H):", farmItemIdField),
                new Row("Số kho /pv cần cất:", vaultCountField),
                new Row("Delay tắt GUI chest + mở /pv (ms):", chestPvDelayField),
                new Row("Delay di chuyển #goto (giây):", moveDelayField),
                new Row("Khoảng cách chờ hàng đợi (mét):", queueDistanceThresholdField),
                new Row("#goto rương kế tiếp:", nextChestGotoField),
                new Row("Lệnh /sethome:", sethomeCommandField),
                new Row("Lệnh về nhà (home):", homeMiaCommandField),
        };
        rightRows = new Row[] {
                new Row("Delay TRƯỚC /shop (giây):", beforeShopDelayField),
                new Row("Delay /shop -> /sethome (giây):", shopToSethomeDelayField),
                new Row("#goto tới NPC shop:", shopGotoField),
                new Row(".look NPC shop:", shopLookField),
                new Row("Delay sau click NPC shop (giây):", afterShopClickDelayField),
                new Row("#goto sau khi thoát shop:", afterShopGotoField),
                new Row(".look sau khi thoát shop:", afterShopLookField),
        };

        for (int i = 0; i < leftRows.length; i++) {
            leftRows[i].field.x = LEFT_X;
            leftRows[i].field.y = FIRST_FIELD_Y + i * ROW_H;
        }
        for (int i = 0; i < rightRows.length; i++) {
            rightRows[i].field.x = RIGHT_X;
            rightRows[i].field.y = FIRST_FIELD_Y + i * ROW_H;
        }

        int buttonY = BUTTON_Y;
        int centerX = DESIGN_W / 2;
        saveButton = new GuiButton(0, centerX - 100, buttonY, 60, 20, "Lưu");
        startButton = new GuiButton(2, centerX - 35, buttonY, 60, 20, "Bắt đầu");
        cancelButton = new GuiButton(1, centerX + 30, buttonY, 70, 20, "Hủy");

        buttonList.add(saveButton);
        buttonList.add(startButton);
        buttonList.add(cancelButton);
    }

    /**
     * Tinh scale + offset de KHUNG AO (DESIGN_W x DESIGN_H) vua khit va CAN
     * GIUA trong cua so game THAT su (this.width x this.height). scale luon
     * <= 1.0 (khong phong to qua kich thuoc thiet ke goc), nhung co the nho
     * hon 1.0 rat nhieu neu cua so/GUI scale cua nguoi choi qua nho - dam bao
     * LUON thay duoc DU cac nut Luu/Bat dau/Huy o duoi cung, khong bi cat mat.
     */
    private void computeLayout() {
        float sx = (float) this.width / DESIGN_W;
        float sy = (float) this.height / DESIGN_H;
        scale = Math.min(1.0f, Math.min(sx, sy));
        if (scale < MIN_SCALE) scale = MIN_SCALE;
        offsetX = (this.width - DESIGN_W * scale) / 2f;
        offsetY = (this.height - DESIGN_H * scale) / 2f;
    }

    /** Doi toa do man hinh THAT (raw mouseX/Y) sang toa do trong khung AO, de so voi x/y cua field/button. */
    private int toVirtualX(int realX) {
        return (int) ((realX - offsetX) / scale);
    }

    private int toVirtualY(int realY) {
        return (int) ((realY - offsetY) / scale);
    }

    private GuiTextField makeField(int id, String text, int maxLength) {
        GuiTextField field = new GuiTextField(id, this.fontRenderer, 0, 0, FIELD_W, FIELD_H);
        field.setText(text == null ? "" : text);
        field.setMaxStringLength(maxLength);
        return field;
    }

    private GuiTextField[] allFields() {
        return new GuiTextField[] {
                farmItemIdField, vaultCountField, chestPvDelayField, moveDelayField,
                queueDistanceThresholdField, nextChestGotoField, sethomeCommandField, homeMiaCommandField,
                beforeShopDelayField, shopToSethomeDelayField,
                shopGotoField, shopLookField, afterShopClickDelayField,
                afterShopGotoField, afterShopLookField
        };
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : allFields()) field.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (GuiTextField field : allFields()) field.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveConfig();
            this.mc.displayGuiScreen(null);
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        computeLayout();
        int vx = toVirtualX(mouseX);
        int vy = toVirtualY(mouseY);
        super.mouseClicked(vx, vy, mouseButton); // xu ly buttonList (toa do nut la toa do "ao")
        for (GuiTextField field : allFields()) field.mouseClicked(vx, vy, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            saveConfig();
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(null);
        } else if (button.id == 2) {
            saveConfig();
            this.mc.displayGuiScreen(null);
            MacroRecorderMod.getInstance().startMacro();
        }
    }

    private void saveConfig() {
        try {
            int farmItemId = clampInt(Integer.parseInt(farmItemIdField.getText().trim()), 1, 32767);
            int vaults = clampInt(Integer.parseInt(vaultCountField.getText().trim()), 1, 9999);
            float moveDelay = clampFloat(parseFloat(moveDelayField), 0f, 10f);
            float afterShopClickDelay = clampFloat(parseFloat(afterShopClickDelayField), 0f, 60f);
            float queueDistance = clampFloat(parseFloat(queueDistanceThresholdField), 0f, 256f);
            float beforeShopDelay = clampFloat(parseFloat(beforeShopDelayField), 0f, 60f);
            float shopToSethomeDelay = clampFloat(parseFloat(shopToSethomeDelayField), 0f, 60f);

            ConfigHelper.setFarmItemId(farmItemId);
            int itemTransferDelayMs = clampInt(Integer.parseInt(chestPvDelayField.getText().trim()), 0, 60000);
            ConfigHelper.setChestPvDelayMs(itemTransferDelayMs);
            ConfigHelper.setConfig(vaults, moveDelay, afterShopClickDelay);
            ConfigHelper.setQueueDistanceThreshold(queueDistance);

            ConfigHelper.setGotoLookConfig(
                    shopGotoField.getText().trim(),
                    shopLookField.getText().trim(),
                    beforeShopDelay,
                    shopToSethomeDelay,
                    0f,
                    afterShopGotoField.getText().trim(),
                    afterShopLookField.getText().trim()
            );
            ConfigHelper.setNextChestGotoCommand(nextChestGotoField.getText().trim());
            ConfigHelper.setHomeConfig(
                    sethomeCommandField.getText().trim(),
                    homeMiaCommandField.getText().trim()
            );

            MacroRecorderMod.getInstance().reloadConfig();
            System.out.println("[MacroRecorder] F8 CONFIG SAVED: ID=" + farmItemId
                    + " | vaults=" + vaults
                    + " | goto=" + shopGotoField.getText().trim()
                    + " | look=" + shopLookField.getText().trim()
                    + " | queue=" + queueDistance + "m"
                    + " | sethome=" + sethomeCommandField.getText().trim()
                    + " | shop->sethome=" + shopToSethomeDelay + "s"
                    + " | home=" + homeMiaCommandField.getText().trim());
        } catch (NumberFormatException e) {
            System.out.println("[MacroRecorder] Lỗi nhập số trong GUI F8, giữ nguyên config cũ: " + e.getMessage());
        }
    }

    private float parseFloat(GuiTextField field) {
        return Float.parseFloat(field.getText().trim().replace(',', '.'));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String formatDelay(float value) {
        String s = String.valueOf(value);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Nen mo toan man hinh THAT (khong qua scale) de phan letterbox quanh
        // GUI thu nho cung duoc lam toi, khong de trong/sang loa.
        this.drawDefaultBackground();

        computeLayout();
        int vx = toVirtualX(mouseX);
        int vy = toVirtualY(mouseY);

        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0f);
        GlStateManager.scale(scale, scale, 1f);

        int centerX = DESIGN_W / 2;

        this.drawCenteredString(this.fontRenderer, "CẤU HÌNH MACRO", centerX, 8, 0xFFFFFF);

        for (Row r : leftRows) {
            drawLabel(r.label, r.field.x, r.field.y - 12);
            r.field.drawTextBox();
        }
        for (Row r : rightRows) {
            drawLabel(r.label, r.field.x, r.field.y - 12);
            r.field.drawTextBox();
        }

        for (GuiButton b : this.buttonList) {
            b.drawButton(this.mc, vx, vy, partialTicks);
        }

        this.drawString(this.fontRenderer, "Mía = ID 338 / minecraft:reeds", LEFT_X, FOOTER_Y1, 0x888888);
        this.drawCenteredString(this.fontRenderer, "ENTER = Lưu & Đóng | ESC = Hủy", centerX, FOOTER_Y2, 0x888888);
        this.drawCenteredString(this.fontRenderer,
                "ID " + ConfigHelper.getFarmItemId() + " | " + ConfigHelper.getVaults() + " kho | transfer "
                        + ConfigHelper.getChestPvDelayMs() + "ms",
                centerX, FOOTER_Y3, 0x00FF00);

        GlStateManager.popMatrix();
    }

    private void drawLabel(String text, int x, int y) {
        this.drawString(this.fontRenderer, text, x, y, 0xFFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
