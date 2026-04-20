package ru.mytheria.main.module.render;

import lombok.Getter;
import net.minecraft.text.Text;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BooleanSetting;

@Getter
public class Removals extends Module {

    private static Removals instance;

    private final BooleanSetting fire = new BooleanSetting(Text.of("Огонь"), null, () -> true).set(false);
    private final BooleanSetting totemAnimation = new BooleanSetting(Text.of("Анимация тотема"), null, () -> true).set(false);
    private final BooleanSetting fireworkParticles = new BooleanSetting(Text.of("Частицы фейерверка"), null, () -> true).set(false);
    private final BooleanSetting scoreboard = new BooleanSetting(Text.of("Скорборд"), null, () -> true).set(false);
    private final BooleanSetting bossBar = new BooleanSetting(Text.of("Боссбар"), null, () -> true).set(false);
    private final BooleanSetting weather = new BooleanSetting(Text.of("Убирать погоду"), null, () -> true).set(false);

    public Removals() {
        super(Text.of("Removals"), null, Category.RENDER);
        instance = this;
        addSettings(fire, totemAnimation, fireworkParticles, scoreboard, bossBar, weather);
    }

    public static boolean hideFire() {
        return isEnabled(f -> f.fire.getEnabled());
    }

    public static boolean hideTotemAnimation() {
        return isEnabled(f -> f.totemAnimation.getEnabled());
    }

    public static boolean hideFireworkParticles() {
        return isEnabled(f -> f.fireworkParticles.getEnabled());
    }

    public static boolean hideScoreboard() {
        return isEnabled(f -> f.scoreboard.getEnabled());
    }

    public static boolean hideBossBar() {
        return isEnabled(f -> f.bossBar.getEnabled());
    }

    public static boolean hideWeather() {
        return isEnabled(f -> f.weather.getEnabled());
    }

    private static boolean isEnabled(java.util.function.Predicate<Removals> predicate) {
        return instance != null && instance.getEnabled() && predicate.test(instance);
    }
}
