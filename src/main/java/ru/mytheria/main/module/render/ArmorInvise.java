package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.SliderSetting;

@Getter
public class ArmorInvise extends Module {
    private final SliderSetting armorOpacity = new SliderSetting(Text.of("Прозрачность брони"), null, () -> true)
            .set(0.0f, 255.0f, 1.0f)
            .set(120.0f);

    public ArmorInvise() {
        super(Text.of("ModelInvise"), null, Category.RENDER);
        addSettings(armorOpacity);
    }
}
