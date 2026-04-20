package ru.mytheria.main.ui.menu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.license.LicenseService;

public class CloudLicenseScreen extends Screen {
    private static final int BACKGROUND = 0xFF050505;
    private static final int PANEL = 0xFF111111;
    private static final int BORDER = 0xFFFFFFFF;
    private static final int BORDER_SOFT = 0x66FFFFFF;
    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFBEBEBE;

    private final Screen parent;
    private TextFieldWidget keyField;

    public CloudLicenseScreen(Screen parent) {
        super(Text.of("License"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LicenseService license = Mytheria.getInstance().getLicenseService();

        int panelX = width / 2 - 180;
        int panelY = height / 2 - 98;

        keyField = new TextFieldWidget(textRenderer, panelX + 16, panelY + 52, 328, 20, Text.of("Ключ"));
        keyField.setMaxLength(128);
        keyField.setText(license == null ? "" : license.getLicenseKey());
        addSelectableChild(keyField);
        setInitialFocus(keyField);

        addDrawableChild(ButtonWidget.builder(Text.of("Сохранить и проверить"), button -> {
                    if (license != null) {
                        license.setLicenseKey(keyField.getText());
                    }
                })
                .dimensions(panelX + 16, panelY + 82, 328, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.of("Проверить"), button -> {
                    if (license != null) {
                        license.verifyAsync();
                    }
                })
                .dimensions(panelX + 16, panelY + 106, 328, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.of("DEV ключ (14д)"), button -> {
                    if (license != null && license.isDevelopmentLicenseModeEnabled()) {
                        String generated = license.generateDevelopmentKey();
                        keyField.setText(generated);
                        license.setLicenseKey(generated);
                    }
                })
                .dimensions(panelX + 16, panelY + 130, 328, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.of("Назад"), button -> close())
                .dimensions(panelX + 16, panelY + 154, 328, 20)
                .build());

        super.init();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);

        int panelX = width / 2 - 180;
        int panelY = height / 2 - 90;
        int panelW = 360;
        int panelH = 196;

        drawBox(context, panelX, panelY, panelW, panelH, PANEL, true);
        context.drawTextWithShadow(textRenderer, "CloudVisuals License", panelX + 16, panelY + 14, TEXT_MAIN);

        LicenseService license = Mytheria.getInstance().getLicenseService();
        String hwid = license == null ? "n/a" : license.getHwid();
        String hwidLine = hwid.length() > 22 ? hwid.substring(0, 22) + "..." : hwid;
        String status = license == null ? "Сервис недоступен" : license.getStatusText();
        int statusColor = license != null && license.isLicensed() ? 0xFF7CFF7C : 0xFFFF8E8E;
        boolean devMode = license != null && license.isDevelopmentLicenseModeEnabled();

        context.drawTextWithShadow(textRenderer, "HWID: " + hwidLine, panelX + 16, panelY + 34, TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, "Режим DEV: " + (devMode ? "ON" : "OFF"), panelX + 16, panelY + 162, devMode ? 0xFF8DFF8D : TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, "Статус: " + status, panelX + 16, panelY + 174, statusColor);

        super.render(context, mouseX, mouseY, delta);
        keyField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return keyField != null && keyField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (keyField != null && keyField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawBox(DrawContext context, int x, int y, int width, int height, int fillColor, boolean strongBorder) {
        context.fill(x, y, x + width, y + height, fillColor);
        context.fill(x, y, x + width, y + 1, strongBorder ? BORDER : BORDER_SOFT);
        context.fill(x, y + height - 1, x + width, y + height, strongBorder ? BORDER : BORDER_SOFT);
        context.fill(x, y, x + 1, y + height, strongBorder ? BORDER : BORDER_SOFT);
        context.fill(x + width - 1, y, x + width, y + height, strongBorder ? BORDER : BORDER_SOFT);
    }
}
