package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BooleanSetting;
import ru.mytheria.api.module.settings.impl.ColorSetting;

@Getter
public class Cooldowns extends Module {
    private final ColorSetting textColor = new ColorSetting(Text.of("\u0422\u0435\u043a\u0441\u0442"), null, () -> true).set(0xFFFFFFFF);
    private final ColorSetting backgroundColor = new ColorSetting(Text.of("\u0424\u043e\u043d"), null, () -> true).set(0x66202020);
    private final BooleanSetting transparent = new BooleanSetting(Text.of("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u044b\u0439"), null, () -> true).set(false);
    private final BooleanSetting ignoreWeapons = new BooleanSetting(Text.of("\u0418\u0433\u043d\u043e\u0440\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u043e\u0440\u0443\u0436\u0438\u0435"), null, () -> true).set(false);

    public Cooldowns() {
        super(Text.of("Cooldowns"), null, Category.PLAYER);
        addSettings(textColor, backgroundColor, transparent, ignoreWeapons);
    }
}
