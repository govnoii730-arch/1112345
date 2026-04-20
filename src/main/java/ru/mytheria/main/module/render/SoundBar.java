package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BooleanSetting;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.module.settings.impl.ModeSetting;

@Getter
public class SoundBar extends Module {
    private final ModeSetting mode = new ModeSetting(Text.of("Режим"), null, () -> true)
            .set("С обложкой", "Без обложки")
            .setDefault("С обложкой");
    private final ColorSetting textColor = new ColorSetting(Text.of("Текст"), null, () -> true).set(0xFFFFFFFF);
    private final ColorSetting accentColor = new ColorSetting(Text.of("Цвет"), null, () -> true).set(0xFFFFFFFF);
    private final ColorSetting backgroundColor = new ColorSetting(Text.of("Фон"), null, () -> true).set(0x66464646);
    private final BooleanSetting transparent = new BooleanSetting(Text.of("Прозрачный"), null, () -> true).set(false);

    public SoundBar() {
        super(Text.of("SoundBar"), null, Category.PLAYER);
        addSettings(mode, textColor, accentColor, backgroundColor, transparent);
    }
}
