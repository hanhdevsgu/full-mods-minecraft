package com.zorotool.macrorecorder.gui;

import com.zorotool.macrorecorder.ConfigHelper;
import com.zorotool.macrorecorder.MacroRecorderMod;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI mo bang phim U: quan ly DANH SACH THU TU CAC TAI KHOAN dung de dieu
 * phoi nhieu nick (nhieu cua so game) chay CUNG LUC. Danh sach nay duoc luu
 * chung vao config/macrorecorder.json (field accountOrder) - xem
 * ConfigHelper.getAccountOrder()/setAccountOrder().
 *
 * Y NGHIA thu tu: khi den doan chuan bi #goto/shop (sau khi xong het cac
 * kho), tai khoan DUNG DAU danh sach LUON duoc chay ngay (khong cho gi ca).
 * Tai khoan thu 2 phai cho toi khi no cach tai khoan dau tien >= nguong
 * khoang cach (met). Tai khoan thu 3 tro di phai cho toi khi 2 tai khoan
 * NGAY LIEN TRUOC no (vi du: acc thu 3 cho acc#1 va acc#2 cach nhau du xa)
 * da cach nhau du nguong. Logic chi tiet xem MacroRecorderMod.canProceedToShop().
 *
 * Moi cua so game (moi nick) NEN duoc cau hinh CUNG 1 danh sach (cung thu
 * tu) de dong bo dung y muon.
 */
public class GuiAccountOrder extends GuiScreen {

    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ROWS = 8;

    // Danh sach dang chinh sua TRONG PHIEN GUI nay. TU DONG LUU xuong file
    // (xem persistOrder()/persistThreshold()) ngay sau moi lan them/xoa/doi
    // cho hoac go so - khong can nut "Luu" nua. Duoc nap lai tu ConfigHelper
    // moi lan GUI duoc MO LAN DAU (constructor), KHONG bi mat khi initGui()
    // chay lai (vd sau khi them/xoa/doi cho 1 dong - initGui() chi dung de
    // ve lai cac nut).
    private final List<String> pendingOrder;
    private float pendingThreshold;

    private GuiTextField newUsernameField;
    private GuiTextField thresholdField;

    private int scrollOffset = 0;

    private static final int ID_ADD = 1002;
    private static final int ID_SCROLL_UP = 1003;
    private static final int ID_SCROLL_DOWN = 1004;
    // Cac nut cua TUNG DONG (up / down / xoa) duoc danh id tu ROW_ID_BASE tro
    // len: id = ROW_ID_BASE + rowIndexTuyetDoi * 3 + (0 = len, 1 = xuong, 2 = xoa).
    private static final int ROW_ID_BASE = 2000;

    public GuiAccountOrder() {
        this.pendingOrder = new ArrayList<>(ConfigHelper.getAccountOrder());
        this.pendingThreshold = ConfigHelper.getQueueDistanceThreshold();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int top = this.height / 2 - 120;

        // O nhap username moi
        this.newUsernameField = new GuiTextField(0, this.fontRenderer, centerX - 150, top + 20, 180, 20);
        this.newUsernameField.setMaxStringLength(32);
        this.newUsernameField.setFocused(true);

        this.buttonList.add(new GuiButton(ID_ADD, centerX + 40, top + 20, 90, 20, "+ Thêm mới"));

        // Danh sach cac tai khoan hien co (co the cuon neu qua MAX_VISIBLE_ROWS dong)
        int listTop = top + 55;
        int visibleCount = Math.min(MAX_VISIBLE_ROWS, pendingOrder.size());
        clampScrollOffset();
        for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
            int row = scrollOffset + visibleIndex;
            int rowY = listTop + visibleIndex * ROW_HEIGHT;
            int baseId = ROW_ID_BASE + row * 3;

            GuiButton upBtn = new GuiButton(baseId, centerX + 60, rowY, 20, 20, "▲");
            GuiButton downBtn = new GuiButton(baseId + 1, centerX + 82, rowY, 20, 20, "▼");
            GuiButton removeBtn = new GuiButton(baseId + 2, centerX + 106, rowY, 44, 20, "Xóa");
            upBtn.enabled = row > 0;
            downBtn.enabled = row < pendingOrder.size() - 1;
            this.buttonList.add(upBtn);
            this.buttonList.add(downBtn);
            this.buttonList.add(removeBtn);
        }

        if (pendingOrder.size() > MAX_VISIBLE_ROWS) {
            this.buttonList.add(new GuiButton(ID_SCROLL_UP, centerX - 150, listTop, 20, 20, "▲"));
            this.buttonList.add(new GuiButton(ID_SCROLL_DOWN, centerX - 150, listTop + (MAX_VISIBLE_ROWS - 1) * ROW_HEIGHT, 20, 20, "▼"));
        }

        // O nhap nguong khoang cach (met)
        int bottomAreaY = top + 55 + MAX_VISIBLE_ROWS * ROW_HEIGHT + 20;
        this.thresholdField = new GuiTextField(1, this.fontRenderer, centerX - 60, bottomAreaY, 60, 20);
        this.thresholdField.setMaxStringLength(6);
        this.thresholdField.setText(formatNumber(pendingThreshold));
    }

    private void clampScrollOffset() {
        int maxOffset = Math.max(0, pendingOrder.size() - MAX_VISIBLE_ROWS);
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public void updateScreen() {
        this.newUsernameField.updateCursorCounter();
        this.thresholdField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Giu lai gia tri nguong dang go truoc khi xu ly, phong khi bam Enter
        // trong luc dang go o nay (khac o username).
        if (this.thresholdField.isFocused()) {
            this.thresholdField.textboxKeyTyped(typedChar, keyCode);
            // Tu luu ngay sau moi ky tu go vao o nguong (neu gia tri hop le).
            persistThreshold();
        } else {
            this.newUsernameField.textboxKeyTyped(typedChar, keyCode);
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.newUsernameField.isFocused() && !this.newUsernameField.getText().trim().isEmpty()) {
                addUsername();
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            // Khong can luu o day nua: moi thay doi (them/xoa/doi cho/sua nguong)
            // da duoc tu luu ngay khi xay ra. ESC chi don gian la dong GUI.
            this.mc.displayGuiScreen(null);
        }

        if (keyCode == Keyboard.KEY_TAB) {
            // Tab de chuyen qua lai giua 2 o nhap cho tien.
            boolean usernameWasFocused = this.newUsernameField.isFocused();
            this.newUsernameField.setFocused(!usernameWasFocused);
            this.thresholdField.setFocused(usernameWasFocused);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.newUsernameField.mouseClicked(mouseX, mouseY, mouseButton);
        this.thresholdField.mouseClicked(mouseX, mouseY, mouseButton);
        // Click vao 1 o thi o kia mat focus, tranh go nham.
        if (this.newUsernameField.isFocused()) {
            this.thresholdField.setFocused(false);
        } else if (this.thresholdField.isFocused()) {
            this.newUsernameField.setFocused(false);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0 && pendingOrder.size() > MAX_VISIBLE_ROWS) {
            scrollOffset -= Integer.signum(wheel);
            clampScrollOffset();
            initGui();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_ADD) {
            addUsername();
            return;
        }
        if (button.id == ID_SCROLL_UP) {
            scrollOffset--;
            clampScrollOffset();
            initGui();
            return;
        }
        if (button.id == ID_SCROLL_DOWN) {
            scrollOffset++;
            clampScrollOffset();
            initGui();
            return;
        }
        if (button.id >= ROW_ID_BASE) {
            int rel = button.id - ROW_ID_BASE;
            int row = rel / 3;
            int action = rel % 3;
            if (row < 0 || row >= pendingOrder.size()) return;

            if (action == 0) { // len
                if (row > 0) {
                    java.util.Collections.swap(pendingOrder, row, row - 1);
                }
            } else if (action == 1) { // xuong
                if (row < pendingOrder.size() - 1) {
                    java.util.Collections.swap(pendingOrder, row, row + 1);
                }
            } else { // xoa
                pendingOrder.remove(row);
            }
            persistOrder();
            initGui();
        }
    }

    private void addUsername() {
        String name = this.newUsernameField.getText().trim();
        if (name.isEmpty()) return;
        for (String existing : pendingOrder) {
            if (existing.equalsIgnoreCase(name)) {
                System.out.println("[MacroRecorder] Username \"" + name + "\" đã có trong danh sách rồi.");
                this.newUsernameField.setText("");
                return;
            }
        }
        pendingOrder.add(name);
        this.newUsernameField.setText("");
        // Cuon xuong cuoi danh sach de thay ngay dong vua them.
        scrollOffset = Math.max(0, pendingOrder.size() - MAX_VISIBLE_ROWS);
        persistOrder();
        initGui();
    }

    /**
     * Luu ngay danh sach thu tu tai khoan xuong config. Goi sau MOI lan
     * them/xoa/doi cho - khong can nut "Luu" nua, khong can dong GUI.
     */
    private void persistOrder() {
        ConfigHelper.setAccountOrder(pendingOrder);
        MacroRecorderMod.getInstance().reloadConfig();
        System.out.println("[MacroRecorder] Đã tự lưu danh sách thứ tự tài khoản: " + pendingOrder);
    }

    /**
     * Luu ngay nguong khoang cach xuong config, neu gia tri dang go la mot
     * so hop le. Goi sau MOI ky tu go vao o nguong. Neu gia tri dang go
     * chua hop le (vd dang go do "1." hoac rong), bo qua khong luu, gia tri
     * cu trong config van con nguyen cho toi khi go xong mot so hop le.
     */
    private void persistThreshold() {
        String text = this.thresholdField.getText().trim().replace(',', '.');
        if (text.isEmpty()) return;
        try {
            float threshold = Float.parseFloat(text);
            if (threshold < 0f) threshold = 0f;
            if (threshold > 256f) threshold = 256f;
            pendingThreshold = threshold;
            ConfigHelper.setQueueDistanceThreshold(pendingThreshold);
            MacroRecorderMod.getInstance().reloadConfig();
            System.out.println("[MacroRecorder] Đã tự lưu ngưỡng khoảng cách chờ: " + pendingThreshold + "m");
        } catch (NumberFormatException e) {
            // Dang go do, chua phai so hop le - bo qua, khong luu.
        }
    }

    private static String formatNumber(float value) {
        String s = String.valueOf(value);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int centerX = this.width / 2;
        int top = this.height / 2 - 120;

        this.drawCenteredString(this.fontRenderer, "THỨ TỰ TÀI KHOẢN (đa nick)", centerX, top, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Username mới:", centerX - 150, top + 9, 0xAAAAAA);
        this.newUsernameField.drawTextBox();

        int listTop = top + 55;
        this.drawString(this.fontRenderer, "Danh sách (thứ tự chạy trước -> sau):", centerX - 150, listTop - 12, 0xAAAAAA);

        int visibleCount = Math.min(MAX_VISIBLE_ROWS, pendingOrder.size());
        if (pendingOrder.isEmpty()) {
            this.drawString(this.fontRenderer, "(chưa có tài khoản nào, thêm ở trên)", centerX - 150, listTop + 4, 0x888888);
        }
        for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
            int row = scrollOffset + visibleIndex;
            int rowY = listTop + visibleIndex * ROW_HEIGHT;
            String label = (row + 1) + ". " + pendingOrder.get(row);
            this.drawString(this.fontRenderer, label, centerX - 150, rowY + 6, 0xFFFFFF);
        }

        int bottomAreaY = top + 55 + MAX_VISIBLE_ROWS * ROW_HEIGHT + 20;
        this.drawString(this.fontRenderer, "Khoảng cách chờ (mét):", centerX - 150, bottomAreaY + 6, 0xAAAAAA);
        this.thresholdField.drawTextBox();

        String help1 = "Đầu danh sách = chạy ngay | các nick sau phải chờ đủ khoảng cách";
        String help2 = "Mọi thay đổi tự lưu ngay | ESC = đóng | cuộn chuột để xem thêm";
        this.drawCenteredString(this.fontRenderer, help1, centerX, bottomAreaY + 60, 0x888888);
        this.drawCenteredString(this.fontRenderer, help2, centerX, bottomAreaY + 72, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
