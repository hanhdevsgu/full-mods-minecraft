package com.minclient.gui.component;

public abstract class Component {
    public abstract void renderComponent(int mouseX, int mouseY);
    public abstract boolean mouseClicked(int mouseX, int mouseY, int button);
    public void mouseReleased(int mouseX, int mouseY, int state) {}
    public void keyTyped(char typedChar, int keyCode) {}
    public abstract int getHeight();
    public abstract void setOffset(int offset);
}
