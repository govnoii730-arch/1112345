package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.ModeSetting;
import ru.mytheria.api.module.settings.impl.SliderSetting;

@Getter
public class Interface extends Module {
    private final ModeSetting mode = new ModeSetting(Text.of("Режим"), null, () -> true)
            .set("Стекло", "Блюр")
            .setDefault("Блюр");
    private final SliderSetting rounding = new SliderSetting(Text.of("Скругление"), null, () -> true)
            .set(0.0f, 100.0f, 1.0f)
            .set(100.0f);
    private final ModeSetting hudPalette = new ModeSetting(Text.of("Цвет HUD"), null, () -> true)
            .set("Золото", "Малина", "Зелёный", "Красный", "Синий", "Белый");

    public Interface() {
        super(Text.of("Interface"), null, Category.PLAYER);
        addSettings(mode, rounding, hudPalette);
    }
}
