package ru.mytheria.main.module.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.mytheria.Mytheria;
import ru.mytheria.api.events.impl.RenderEvent;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.ModeSetting;

public class LockSlot extends Module {
    private static final String MESSAGE = "Слот заблокирован !";
    private static long messageEndAt;

    private final ModeSetting lockSlot = new ModeSetting(Text.of("Блокировать"), null, () -> true)
            .set("1", "2", "3", "4", "5", "6", "7", "8", "9")
            .setDefault("1");

    public LockSlot() {
        super(Text.of("LockSlot"), null, Category.MOVEMENT);
        addSettings(lockSlot);
    }

    public static boolean shouldCancelDrop() {
        if (fullNullCheck()) {
            return false;
        }

        LockSlot module = getInstance();
        if (module == null || !Boolean.TRUE.equals(module.getEnabled())) {
            return false;
        }

        return mc.player.getInventory().selectedSlot == module.getLockedSlotIndex();
    }

    public static void notifyBlockedDrop() {
        messageEndAt = System.currentTimeMillis() + 1200L;
    }

    @EventHandler
    public void onRender(RenderEvent.AfterHud event) {
        if (System.currentTimeMillis() > messageEndAt) {
            return;
        }

        DrawContext context = event.getContext();
        int width = mc.textRenderer.getWidth(MESSAGE);
        int x = (mc.getWindow().getScaledWidth() - width) / 2;
        int y = mc.getWindow().getScaledHeight() - 56;
        context.drawTextWithShadow(mc.textRenderer, MESSAGE, x, y, 0xFFFFFFFF);
    }

    private int getLockedSlotIndex() {
        try {
            return Integer.parseInt(lockSlot.getValue()) - 1;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static LockSlot getInstance() {
        return (LockSlot) Mytheria.getInstance().getModuleManager().find(LockSlot.class);
    }
}
