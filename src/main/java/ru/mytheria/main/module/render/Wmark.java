package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BooleanSetting;
import ru.mytheria.api.module.settings.impl.ColorSetting;
import ru.mytheria.api.module.settings.impl.ModeSetting;

@Getter
public class Wmark extends Module {
    private final ColorSetting textColor = new ColorSetting(Text.of("Текст"), null, () -> true).set(0xFFFFFFFF);
    private final ColorSetting backgroundColor = new ColorSetting(Text.of("Фон"), null, () -> true).set(0x66464646);
    private final BooleanSetting transparent = new BooleanSetting(Text.of("Прозрачный"), null, () -> true).set(false);
    private final BooleanSetting musicBar = new BooleanSetting(Text.of("Включить музыкальный бар"), null, () -> true).set(true);
    private final ModeSetting musicBarMode = new ModeSetting(Text.of("Режим бара"), null, () -> musicBar.getEnabled())
            .set("С обложкой", "Без обложки")
            .setDefault("С обложкой");

    public Wmark() {
        super(Text.of("Watermark"), null, Category.PLAYER);
        addSettings(textColor, backgroundColor, transparent, musicBar, musicBarMode);
    }
}
