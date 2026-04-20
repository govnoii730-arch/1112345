package ru.mytheria.main.ui.clickGui.components.settings.colorsetting;

import com.google.common.base.Suppliers;
import net.minecraft.client.gui.DrawContext;
import ru.mytheria.Mytheria;
import ru.mytheria.api.clientannotation.QuickApi;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.util.color.ColorUtil;
import ru.mytheria.api.util.fonts.main.MsdfUtil;
import ru.mytheria.api.util.math.Math;
import ru.mytheria.api.util.shader.common.states.ColorState;
import ru.mytheria.api.util.shader.common.states.RadiusState;
import ru.mytheria.api.util.shader.common.states.SizeState;
import ru.mytheria.api.util.window.WindowLayer;
import ru.mytheria.api.util.window.WindowRepository;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.ui.clickGui.components.settings.SettingComponent;
import ru.mytheria.main.ui.clickGui.components.settings.colorsetting.window.ColorPickerWindowComponent;

import java.util.function.Supplier;

public class ColorPickerComponent extends SettingComponent {

    private final Supplier<ColorSetting> colorSetting = Suppliers.memoize(() -> (ColorSetting) getSettingLayer());
    private final WindowLayer windowLayer;

    public ColorPickerComponent(ColorSetting settingLayer) {
        super(settingLayer);
        this.windowLayer = new ColorPickerWindowComponent(settingLayer);
    }

    @Override
    public void init() {
        String descriptionText = MsdfUtil.cutString(getSettingLayer().getDescription().getString(), 6, getTextColumnWidth());

        windowLayer.init();
        windowLayer.position(getX() + getWidth() - windowLayer.getWidth(), getY() + getHeight() / 2f);

        float moduleNameHeight = QuickApi.inter().getHeight(getSettingLayer().getName().getString(), 7);
        float descriptionHeight = QuickApi.inter().getHeight(descriptionText, 6);
        size(SETTING_WIDTH, moduleNameHeight + 5 + descriptionHeight);
    }

    @Override
    public ColorPickerComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        String descriptionText = MsdfUtil.cutString(getSettingLayer().getDescription().getString(), 6, getTextColumnWidth());
        ColorSetting setting = colorSetting.get();
        int color = InterfaceStyle.hudPrimaryColor(setting.getColor());
        String hexValue = String.format("#%06X", color & 0x00FFFFFF);

        QuickApi.text()
                .size(7)
                .color(ColorUtil.applyOpacity(0xFFFFFFFF, 95))
                .text(getSettingLayer().getName().getString())
                .font(QuickApi.inter())
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY() - 1);

        if (!descriptionText.isEmpty()) {
            QuickApi.text()
                    .size(6)
                    .color(ColorUtil.applyOpacity(0xFFFFFFFF, 50))
                    .text(descriptionText)
                    .font(QuickApi.inter())
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(), getX(), getY() + QuickApi.inter().getHeight(getSettingLayer().getName().getString(), 7) + 4);
        }

        float valueX = getValueColumnX();
        float previewSize = 9f;
        float hexWidth = QuickApi.inter().getWidth(hexValue, 6);

        QuickApi.rectangle()
                .radius(new RadiusState(2))
                .size(new SizeState(VALUE_COLUMN_WIDTH, previewSize))
                .color(new ColorState(ColorUtil.applyOpacity(0xFFFFFFFF, 10)))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), valueX, getY());

        QuickApi.rectangle()
                .radius(new RadiusState(2))
                .size(new SizeState(previewSize - 2f, previewSize - 2f))
                .color(new ColorState(color))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), valueX + 1f, getY() + 1f);

        QuickApi.text()
                .size(6)
                .color(ColorUtil.applyOpacity(0xFFFFFFFF, 100))
                .text(hexValue)
                .font(QuickApi.inter())
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), valueX + VALUE_COLUMN_WIDTH - hexWidth - 3f, getY() + .5f);

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        WindowRepository windowRepository = Mytheria.getInstance().getClickGuiScreen().getWindowRepository();

        if (Math.isHover(mouseX, mouseY, getX(), getY(), getWidth(), getHeight()) && !windowRepository.contains(windowLayer)) {
            windowRepository.push(windowLayer);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
