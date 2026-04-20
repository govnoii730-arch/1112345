package ru.mytheria.api.client.draggable;

import ru.mytheria.api.clientannotation.QuickApi;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import ru.mytheria.api.client.draggable.interfaces.DraggableApi;
import ru.mytheria.api.module.settings.Setting;
import ru.mytheria.api.util.animations.Animation;
import ru.mytheria.api.util.animations.Direction;
import ru.mytheria.api.util.animations.implement.DecelerateAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class Draggable implements DraggableApi, QuickApi {

    @Setter
    @NonNull
    Float x, y, width, height;

    @Setter
    Float prevX, prevY;

    @Setter
    @NonNull
    Supplier<Boolean> visible;

    @Setter
    @NonNull
    Float sizeScale = 1.0f;

    @Setter
    @NonFinal
    Boolean dragging = false;

    @Setter
    @NonFinal
    Boolean resizing = false;

    @Setter
    Float resizeStartMouseX, resizeStartMouseY, resizeStartScale;

    @NonFinal
    Boolean settingOpened = false;

    final List<Setting> settings = new ArrayList<>();

    final Animation animation = new DecelerateAnimation()
            .setMs(250)
            .setValue(1);

    final Animation settingAnimation = new DecelerateAnimation()
            .setMs(300)
            .setValue(1);

    public void toggleSetting() {
        this.settingOpened = !this.settingOpened;
        this.settingAnimation.setDirection(this.settingOpened ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    public void position(float x, float y) {
        this.prevX = this.x;
        this.prevY = this.y;
        this.x = x;
        this.y = y;
    }

    public void cycleSizeScale() {
        float[] steps = {0.85f, 1.0f, 1.15f, 1.3f};
        float current = this.sizeScale == null ? 1.0f : this.sizeScale;

        for (int i = 0; i < steps.length; i++) {
            if (java.lang.Math.abs(current - steps[i]) < 0.03f) {
                this.sizeScale = steps[(i + 1) % steps.length];
                return;
            }
        }

        this.sizeScale = 1.0f;
    }

    public void beginResize(float mouseX, float mouseY) {
        this.resizing = true;
        this.resizeStartMouseX = mouseX;
        this.resizeStartMouseY = mouseY;
        this.resizeStartScale = this.sizeScale == null ? 1.0f : this.sizeScale;
    }

    public boolean isResizeHandleHovered(double mouseX, double mouseY) {
        float handleSize = getResizeHandleSize();
        float handleX = getResizeHandleX();
        float handleY = y + height - handleSize - 2f;

        return mouseX >= handleX && mouseX <= handleX + handleSize
                && mouseY >= handleY && mouseY <= handleY + handleSize;
    }

    public boolean isResetHandleHovered(double mouseX, double mouseY) {
        float handleSize = getResizeHandleSize();
        float handleX = getResetHandleX();
        float handleY = y + height - handleSize - 2f;

        return mouseX >= handleX && mouseX <= handleX + handleSize
                && mouseY >= handleY && mouseY <= handleY + handleSize;
    }

    public float getResizeHandleSize() {
        return 7f;
    }

    public float getResizeHandleX() {
        float handleSize = getResizeHandleSize();
        return x + width - handleSize - 2f;
    }

    public float getResetHandleX() {
        float handleSize = getResizeHandleSize();
        return getResizeHandleX() - handleSize - 3f;
    }

    public void resetSizeScale() {
        this.sizeScale = 1.0f;
    }

    public boolean isDraggable() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    @Override
    public void tick() {}
}
