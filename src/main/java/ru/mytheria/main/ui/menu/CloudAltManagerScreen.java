package ru.mytheria.main.ui.menu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class CloudAltManagerScreen extends Screen {
    private static final int BACKGROUND = 0xFF050505;
    private static final int PANEL = 0xFF111111;
    private static final int PANEL_HOVER = 0xFF1A1A1A;
    private static final int BORDER = 0xFFFFFFFF;
    private static final int BORDER_SOFT = 0x66FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFBEBEBE;
    private static final long DOUBLE_CLICK_MS = 450L;

    private final Screen parent;

    private boolean typing;
    private String typedName = "";
    private float scroll;
    private CloudAltEntry lastClickedAlt;
    private long lastClickTime;

    public CloudAltManagerScreen(Screen parent) {
        super(Text.of("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.of("Back"), button -> close())
                .dimensions(width / 2 - 90, height / 2 + 170, 85, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.of("Quit"), button -> client.scheduleStop())
                .dimensions(width / 2 + 5, height / 2 + 170, 85, 20)
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);

        float panelWidth = 680f;
        float panelHeight = 390f;
        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - panelHeight) / 2f;

        drawBox(context, panelX, panelY, panelWidth, panelHeight, PANEL, true);

        context.drawTextWithShadow(textRenderer, "Alt Manager", (int) panelX + 16, (int) panelY + 12, TEXT);
        context.drawTextWithShadow(textRenderer, "Current: " + client.getSession().getUsername(), (int) panelX + 16, (int) panelY + 30, TEXT_MUTED);

        drawList(context, mouseX, mouseY, panelX + 16f, panelY + 56f, 420f, 310f);
        drawControls(context, mouseX, mouseY, panelX + 452f, panelY + 56f, 212f, 310f);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawList(DrawContext context, int mouseX, int mouseY, float x, float y, float width, float height) {
        drawBox(context, x, y, width, height, PANEL, false);

        List<CloudAltEntry> entries = CloudAltStorage.entries();
        float rowHeight = 28f;
        float gap = 6f;
        float contentHeight = entries.size() * (rowHeight + gap);
        float minScroll = Math.min(0f, height - 12f - contentHeight);
        scroll = MathHelper.clamp(scroll, minScroll, 0f);

        context.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));
        for (int i = 0; i < entries.size(); i++) {
            CloudAltEntry entry = entries.get(i);
            float rowX = x + 8f;
            float rowY = y + 8f + scroll + i * (rowHeight + gap);
            if (rowY + rowHeight < y || rowY > y + height) {
                continue;
            }

            boolean hovered = isHovered(mouseX, mouseY, rowX, rowY, width - 16f, rowHeight);
            boolean active = client.getSession().getUsername().equalsIgnoreCase(entry.name());
            int fill = active ? 0xFF1E1E1E : hovered ? PANEL_HOVER : PANEL;
            drawBox(context, rowX, rowY, width - 16f, rowHeight, fill, false);

            context.drawTextWithShadow(textRenderer, entry.name(), (int) rowX + 8, (int) rowY + 10, TEXT);
            if (active) {
                String activeText = "ACTIVE";
                int activeWidth = textRenderer.getWidth(activeText);
                context.drawTextWithShadow(textRenderer, activeText, (int) (rowX + width - 16f - activeWidth - 8f), (int) rowY + 10, TEXT_MUTED);
            }
        }
        context.disableScissor();
    }

    private void drawControls(DrawContext context, int mouseX, int mouseY, float x, float y, float width, float height) {
        drawBox(context, x, y, width, height, PANEL, false);

        context.drawTextWithShadow(textRenderer, "CloudVisuals", (int) x + 12, (int) y + 10, TEXT);
        context.drawTextWithShadow(textRenderer, "LMB x2 = login | RMB = remove", (int) x + 12, (int) y + 28, TEXT_MUTED);

        float inputX = x + 12f;
        float inputY = y + 52f;
        float inputWidth = width - 24f;
        float inputHeight = 30f;
        drawBox(context, inputX, inputY, inputWidth, inputHeight, typing ? PANEL_HOVER : PANEL, false);

        String inputText = typedName.isEmpty() && !typing ? "\u0412\u0432\u0435\u0434\u0438 \u043d\u0438\u043a" : typedName + (typing && System.currentTimeMillis() % 1000L > 500L ? "_" : "");
        context.drawTextWithShadow(textRenderer, inputText, (int) inputX + 8, (int) inputY + 10, typedName.isEmpty() && !typing ? TEXT_MUTED : TEXT);

        drawButton(context, x + 12f, y + 96f, width - 24f, 28f, "Add", isHovered(mouseX, mouseY, x + 12f, y + 96f, width - 24f, 28f));
        drawButton(context, x + 12f, y + 132f, width - 24f, 28f, "Random", isHovered(mouseX, mouseY, x + 12f, y + 132f, width - 24f, 28f));
    }

    private void drawButton(DrawContext context, float x, float y, float width, float height, String label, boolean hovered) {
        drawBox(context, x, y, width, height, hovered ? PANEL_HOVER : PANEL, false);
        int textWidth = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, label, (int) (x + (width - textWidth) / 2f), (int) y + 10, TEXT);
    }

    private void drawBox(DrawContext context, float x, float y, float width, float height, int fillColor, boolean strongBorder) {
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), fillColor);
        context.fill((int) x, (int) y, (int) (x + width), (int) y + 1, strongBorder ? BORDER : BORDER_SOFT);
        context.fill((int) x, (int) (y + height - 1), (int) (x + width), (int) (y + height), strongBorder ? BORDER : BORDER_SOFT);
        context.fill((int) x, (int) y, (int) x + 1, (int) (y + height), strongBorder ? BORDER : BORDER_SOFT);
        context.fill((int) (x + width - 1), (int) y, (int) (x + width), (int) (y + height), strongBorder ? BORDER : BORDER_SOFT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float panelWidth = 680f;
        float panelHeight = 390f;
        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - panelHeight) / 2f;

        float inputX = panelX + 464f;
        float inputY = panelY + 108f;
        float inputWidth = 188f;
        float inputHeight = 30f;
        if (isHovered(mouseX, mouseY, inputX, inputY, inputWidth, inputHeight) && button == 0) {
            typing = true;
            return true;
        }

        typing = false;

        if (isHovered(mouseX, mouseY, panelX + 464f, panelY + 152f, 188f, 28f) && button == 0) {
            CloudAltStorage.add(typedName);
            typedName = "";
            return true;
        }

        if (isHovered(mouseX, mouseY, panelX + 464f, panelY + 188f, 188f, 28f) && button == 0) {
            CloudAltStorage.add(randomName());
            return true;
        }

        List<CloudAltEntry> entries = CloudAltStorage.entries();
        float rowHeight = 28f;
        float gap = 6f;
        for (int i = 0; i < entries.size(); i++) {
            float rowX = panelX + 24f;
            float rowY = panelY + 64f + scroll + i * (rowHeight + gap);
            if (!isHovered(mouseX, mouseY, rowX, rowY, 404f, rowHeight)) {
                continue;
            }

            CloudAltEntry entry = entries.get(i);
            if (button == 1) {
                CloudAltStorage.remove(entry);
                return true;
            }

            if (button == 0) {
                long now = System.currentTimeMillis();
                if (lastClickedAlt != null && lastClickedAlt.name().equalsIgnoreCase(entry.name()) && now - lastClickTime <= DOUBLE_CLICK_MS) {
                    CloudAltStorage.use(entry);
                    lastClickedAlt = null;
                } else {
                    lastClickedAlt = entry;
                    lastClickTime = now;
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float panelWidth = 680f;
        float panelHeight = 390f;
        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - panelHeight) / 2f;
        if (isHovered(mouseX, mouseY, panelX + 16f, panelY + 56f, 420f, 310f)) {
            scroll += (float) verticalAmount * 14f;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (typing) {
            if (!Character.isISOControl(chr) && typedName.length() < 20) {
                typedName += chr;
            }
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (typing) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                String clipboard = client.keyboard.getClipboard();
                if (clipboard != null) {
                    typedName += clipboard;
                    if (typedName.length() > 20) {
                        typedName = typedName.substring(0, 20);
                    }
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !typedName.isEmpty()) {
                typedName = typedName.substring(0, typedName.length() - 1);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                CloudAltStorage.add(typedName);
                typedName = "";
                typing = false;
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String randomName() {
        String[] first = {"Cloud", "Night", "Void", "White", "Black", "Ghost"};
        String[] second = {"Visual", "Player", "Hunter", "Walker", "Motion", "Shadow"};
        Random random = new Random();
        return first[random.nextInt(first.length)] + second[random.nextInt(second.length)] + random.nextInt(900);
    }

    private boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
