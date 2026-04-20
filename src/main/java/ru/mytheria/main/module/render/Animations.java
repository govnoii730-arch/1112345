package ru.mytheria.main.module.render;

import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.ModeListSetting;

public class Animations extends Module {
    private static Animations instance;

    private static final String TAB = "Анимировать таб";
    private static final String CHAT = "Анимировать чат";

    private final ModeListSetting targets = new ModeListSetting(Text.of("Анимации"), null, () -> true)
            .set(TAB, CHAT);

    public Animations() {
        super(Text.of("Animations"), null, Category.RENDER);
        instance = this;
        addSettings(targets);
    }

    public static boolean animateHotbar() {
        return false;
    }

    public static boolean animateTab() {
        return enabled(TAB);
    }

    public static boolean animateChat() {
        return enabled(CHAT);
    }

    private static boolean enabled(String name) {
        return instance != null
                && Boolean.TRUE.equals(instance.getEnabled())
                && instance.targets.get(name) != null
                && instance.targets.get(name).getEnabled();
    }
}
