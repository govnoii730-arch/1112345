package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BooleanSetting;
import ru.mytheria.api.module.settings.impl.ColorSetting;

@Getter
public class Inventory extends Module {
    private final ColorSetting textColor = new ColorSetting(Text.of("Текст"), null, () -> true).set(0xFFFFFFFF);
    private final ColorSetting backgroundColor = new ColorSetting(Text.of("Фон"), null, () -> true).set(0x66464646);
    private final BooleanSetting transparent = new BooleanSetting(Text.of("Прозрачный"), null, () -> true).set(false);

    public Inventory() {
        super(Text.of("Inventory"), null, Category.PLAYER);
        addSettings(textColor, backgroundColor, transparent);
    }
}
