package ru.mytheria.main.module.render;

import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;

public class ShullkerPreview extends Module {
    private static ShullkerPreview instance;

    public ShullkerPreview() {
        super(Text.of("ShullkerPreview"), null, Category.RENDER);
        instance = this;
    }

    public static boolean isEnabledPreview() {
        return instance != null && Boolean.TRUE.equals(instance.getEnabled());
    }
}
