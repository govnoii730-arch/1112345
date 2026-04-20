package ru.mytheria.mixin;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.main.module.render.InterfaceStyle;
import ru.mytheria.main.module.render.ShullkerPreview;

@Mixin(HandledScreen.class)
public class HandledScreenShullkerPreviewMixin {
    private static final float PREVIEW_Z_LAYER = 500.0f;

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void mytheria$renderShullkerPreview(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ShullkerPreview.isEnabledPreview()) {
            return;
        }

        if (focusedSlot == null || !focusedSlot.hasStack()) {
            return;
        }

        ItemStack hovered = focusedSlot.getStack();
        if (!(hovered.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
            return;
        }

        ci.cancel();

        ContainerComponent container = hovered.get(DataComponentTypes.CONTAINER);
        if (container == null) {
            return;
        }

        DefaultedList<ItemStack> previewItems = DefaultedList.ofSize(27, ItemStack.EMPTY);
        container.copyTo(previewItems);
        if (previewItems.stream().allMatch(ItemStack::isEmpty)) {
            return;
        }

        int previewWidth = 170;
        int previewHeight = 60;
        int drawX = mouseX + 12;
        int drawY = mouseY - 8;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        if (drawX + previewWidth > screenWidth - 6) {
            drawX = mouseX - previewWidth - 12;
        }
        if (drawY + previewHeight > screenHeight - 6) {
            drawY = screenHeight - previewHeight - 6;
        }
        if (drawY < 6) {
            drawY = 6;
        }

        int bg = InterfaceStyle.hudPrimaryColorWithAlpha(0xB0181818);
        int border = InterfaceStyle.hudPrimaryColorWithAlpha(0xE6FFFFFF);
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, PREVIEW_Z_LAYER);
        context.fill(drawX, drawY, drawX + previewWidth, drawY + previewHeight, bg);
        context.fill(drawX, drawY, drawX + previewWidth, drawY + 1, border);
        context.fill(drawX, drawY + previewHeight - 1, drawX + previewWidth, drawY + previewHeight, border);
        context.fill(drawX, drawY, drawX + 1, drawY + previewHeight, border);
        context.fill(drawX + previewWidth - 1, drawY, drawX + previewWidth, drawY + previewHeight, border);

        int startX = drawX + 6;
        int startY = drawY + 6;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                int x = startX + col * 18;
                int y = startY + row * 18;

                context.fill(x, y, x + 16, y + 16, 0x22FFFFFF);
                ItemStack stack = previewItems.get(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                context.drawItem(stack, x, y);
                context.drawStackOverlay(mc.textRenderer, stack, x, y);
            }
        }
        context.getMatrices().pop();
    }
}
