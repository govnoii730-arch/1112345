package ru.mytheria.main.module.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import ru.mytheria.api.events.impl.EventPlayerTick;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.util.enviorement.PlayerUtil;

public class Sprint extends Module {
    public Sprint() {
        super(Text.of("Sprint"), null, Category.MOVEMENT);
    }

    @EventHandler
    public void onTick(EventPlayerTick e) {
        if (mc.player == null || mc.world == null || mc.player.input == null) {
            return;
        }

        boolean shouldSprint = PlayerUtil.isMovingForward()
                && !mc.player.horizontalCollision
                && !mc.player.isSneaking()
                && !mc.player.isUsingItem()
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && !PlayerUtil.flyingOnElytra();

        mc.player.setSprinting(shouldSprint);
    }
}
