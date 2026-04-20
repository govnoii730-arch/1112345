package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BooleanSetting;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.module.settings.impl.SliderSetting;

@Getter
public class CustomFog extends Module {
    private final SliderSetting distance = new SliderSetting(Text.of("Длина"), null, () -> true)
            .set(2.0f, 256.0f, 1.0f)
            .set(48.0f);
    private final ColorSetting color = new ColorSetting(Text.of("Цвет"), null, () -> true)
            .set(0xFFD8C6FF);
    private final BooleanSetting removeClouds = new BooleanSetting(Text.of("Убирать облака"), null, () -> true)
            .set(false);

    public CustomFog() {
        super(Text.of("CustomFog"), Category.RENDER);
        addSettings(distance, color, removeClouds);
    }
}
