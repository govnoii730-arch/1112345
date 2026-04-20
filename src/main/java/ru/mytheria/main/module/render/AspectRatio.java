package ru.mytheria.main.module.render;

import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.ModeSetting;

public class AspectRatio extends Module {
    private final ModeSetting ratio = new ModeSetting(Text.of("Формат"), null, () -> true)
            .set("4:3", "21:9", "16:9")
            .setDefault("16:9");

    public AspectRatio() {
        super(Text.of("AspectRatio"), null, Category.RENDER);
        addSettings(ratio);
    }

    public float targetRatio() {
        String value = ratio.getValue();
        if ("4:3".equals(value)) {
            return 4.0f / 3.0f;
        }
        if ("21:9".equals(value)) {
            return 21.0f / 9.0f;
        }
        return 16.0f / 9.0f;
    }
}
