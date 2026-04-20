package ru.mytheria.main.module.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import ru.mytheria.api.events.impl.EventPlayerTick;
import ru.mytheria.api.events.impl.KeyEvent;
import ru.mytheria.api.events.impl.MouseEvent;
import ru.mytheria.api.module.Category;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.module.settings.impl.BindSetting;
import ru.mytheria.api.module.settings.impl.BooleanSetting;
import ru.mytheria.api.module.settings.impl.ModeSetting;
import ru.mytheria.api.util.enviorement.InventoryHelper;

public class ItemSwap extends Module {
    private static final int INVENTORY_SWAP_OPEN_TICK = 1;
    private static final int INVENTORY_SWAP_CLICK_TICK = 4;
    private static final int INVENTORY_SWAP_FINISH_TICK = 8;
    private static final long SWAP_DELAY_MS = 300L;

    private final ModeSetting firstItemSetting = new ModeSetting(Text.of("Первый предмет"), null, () -> true)
            .set("Сфера", "Золотое яблоко", "Щит", "Тотем")
            .setDefault("Сфера");
    private final ModeSetting secondItemSetting = new ModeSetting(Text.of("Второй предмет"), null, () -> true)
            .set("Тотем", "Золотое яблоко", "Щит", "Сфера")
            .setDefault("Тотем");
    private final BindSetting bind = new BindSetting(Text.of("Кнопка"), Text.of("ЛКМ для выбора, потом клавиша или мышь"), () -> true)
            .set(-1);
    private final BooleanSetting swapRender = new BooleanSetting(Text.of("Показ свапа"), null, () -> true)
            .set(true);
    private final BooleanSetting onlyEnchanted = new BooleanSetting(Text.of("Только Чар. тотемы"), null, () -> true)
            .set(false);

    private boolean useFirstItem = true;
    private long lastSwapTime;
    private boolean pendingInventorySwap;
    private boolean inventorySwapPerformed;
    private boolean inventoryScreenOpenedBySwap;
    private int pendingInventorySlot = -1;
    private int pendingSwapTicks;
    private String pendingItemName = "";

    public ItemSwap() {
        super(Text.of("ItemSwap"), null, Category.MOVEMENT);
        addSettings(firstItemSetting, secondItemSetting, bind, swapRender, onlyEnchanted);
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (!canHandleBind() || event.getAction() != 1 || bind.getKey() < 8 || event.getKey() != bind.getKey()) {
            return;
        }

        triggerSwap();
    }

    @EventHandler
    public void onMouse(MouseEvent event) {
        if (!canHandleBind() || event.getAction() != 1 || bind.getKey() >= 8 || event.getButton() != bind.getKey()) {
            return;
        }

        triggerSwap();
    }

    @EventHandler
    public void onTick(EventPlayerTick event) {
        if (Module.fullNullCheck() || !pendingInventorySwap) {
            return;
        }

        pendingSwapTicks++;
        freezeMovement();

        if (pendingSwapTicks == INVENTORY_SWAP_OPEN_TICK && mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            inventoryScreenOpenedBySwap = true;
        }

        if (!inventorySwapPerformed && pendingSwapTicks >= INVENTORY_SWAP_CLICK_TICK) {
            int rawSlot = InventoryHelper.indexToSlot(pendingInventorySlot);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, rawSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, PlayerScreenHandler.OFFHAND_ID, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, rawSlot, 0, SlotActionType.PICKUP, mc.player);
            inventorySwapPerformed = true;
            notifyClient("ItemSwap - свапнул на " + pendingItemName);
        }

        if (pendingSwapTicks >= INVENTORY_SWAP_FINISH_TICK) {
            if (inventoryScreenOpenedBySwap && mc.currentScreen instanceof InventoryScreen) {
                mc.setScreen(null);
            }
            resetInventorySwapState();
        }
    }

    @Override
    public void deactivate() {
        useFirstItem = true;
        if (inventoryScreenOpenedBySwap && mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
        }
        resetInventorySwapState();
        super.deactivate();
    }

    private boolean canHandleBind() {
        return Boolean.TRUE.equals(getEnabled())
                && !Module.fullNullCheck()
                && mc.currentScreen == null
                && bind.getKey() != null
                && bind.getKey() != -1
                && System.currentTimeMillis() - lastSwapTime >= SWAP_DELAY_MS;
    }

    private void triggerSwap() {
        ItemType targetItem = ItemType.fromValue(useFirstItem ? firstItemSetting.getValue() : secondItemSetting.getValue());
        useFirstItem = !useFirstItem;
        lastSwapTime = System.currentTimeMillis();

        if (targetItem == null || mc.interactionManager == null) {
            return;
        }

        int slot = findItemSlot(targetItem);
        if (slot == -1) {
            notifyClient("Предмет " + targetItem.displayName + " не найден!");
            return;
        }

        pendingInventorySwap = true;
        inventorySwapPerformed = false;
        inventoryScreenOpenedBySwap = false;
        pendingInventorySlot = slot;
        pendingSwapTicks = 0;
        pendingItemName = targetItem.displayName;
    }

    private int findItemSlot(ItemType type) {
        for (int i = 35; i >= 0; i--) {
            if (matchesItem(mc.player.getInventory().getStack(i), type)) {
                return i;
            }
        }

        return -1;
    }

    private boolean matchesItem(ItemStack stack, ItemType type) {
        if (stack == null || stack.isEmpty() || type == null) {
            return false;
        }

        return switch (type) {
            case TOTEM -> stack.isOf(Items.TOTEM_OF_UNDYING) && (!onlyEnchanted.getEnabled() || stack.hasEnchantments());
            case SPHERE -> isSphere(stack);
            case GOLDEN_APPLE -> stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
            case SHIELD -> stack.isOf(Items.SHIELD);
        };
    }

    private boolean isSphere(ItemStack stack) {
        if (!stack.isOf(Items.PLAYER_HEAD)) {
            return false;
        }

        return stack.getCustomName() != null
                || stack.get(DataComponentTypes.LORE) != null
                || stack.get(DataComponentTypes.CUSTOM_MODEL_DATA) != null
                || stack.get(DataComponentTypes.CUSTOM_DATA) != null
                || stack.hasEnchantments()
                || stack.hasGlint();
    }

    private void notifyClient(String message) {
        if (!swapRender.getEnabled() || mc.inGameHud == null) {
            return;
        }

        mc.inGameHud.getChatHud().addMessage(Text.of(message));
    }

    private void freezeMovement() {
        mc.player.setSprinting(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);

        if (mc.player.input != null) {
            mc.player.input.movementForward = 0.0f;
            mc.player.input.movementSideways = 0.0f;
        }
    }

    private void resetInventorySwapState() {
        pendingInventorySwap = false;
        inventorySwapPerformed = false;
        inventoryScreenOpenedBySwap = false;
        pendingInventorySlot = -1;
        pendingSwapTicks = 0;
        pendingItemName = "";
    }

    private enum ItemType {
        TOTEM("Тотем"),
        GOLDEN_APPLE("Золотое яблоко"),
        SHIELD("Щит"),
        SPHERE("Сфера");

        private final String displayName;

        ItemType(String displayName) {
            this.displayName = displayName;
        }

        private static ItemType fromValue(String value) {
            if ("Тотем".equals(value)) {
                return TOTEM;
            }
            if ("Золотое яблоко".equals(value)) {
                return GOLDEN_APPLE;
            }
            if ("Щит".equals(value)) {
                return SHIELD;
            }
            if ("Сфера".equals(value)) {
                return SPHERE;
            }
            return null;
        }
    }
}
