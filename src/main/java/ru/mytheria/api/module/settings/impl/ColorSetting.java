package ru.mytheria.api.module.settings.impl;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.settings.Setting;

import java.awt.*;
import java.util.function.Supplier;

import static net.minecraft.util.math.ColorHelper.*;

@Getter
public class ColorSetting extends Setting {

    float hsb, saturation, brightness = 0.0f, alpha = 1.0f;

    public ColorSetting(Text name, Text description, Supplier<Boolean> visible) {
        super(name, description, visible);
    }

    public ColorSetting set(Integer color) {
        float[] hsbValue = Color.RGBtoHSB(getRed(color), getGreen(color), getBlue(color), new float[3]);

        this.hsb = hsbValue[0];
        this.saturation = hsbValue[1];
        this.brightness = hsbValue[2];
        this.alpha = color >> 24 & 0xFF;

        return this;
    }

    public ColorSetting set(float hsb, float saturation, float brightness, float alpha) {
        this.hsb = hsb;
        this.saturation = saturation;
        this.brightness = brightness;
        this.alpha = alpha;

        return this;
    }


    @Override
    public ColorSetting collection(Collection collection) {
        collection.put(this);

        return this;
    }

    public int getColor() {
        int rgb = Color.HSBtoRGB(hsb, saturation, brightness);
        int alphaValue = alpha <= 1.0f ? Math.round(alpha * 255.0f) : Math.round(alpha);
        alphaValue = Math.max(0, Math.min(255, alphaValue));

        return getArgb(alphaValue, getRed(rgb), getGreen(rgb), getBlue(rgb));
    }
}
