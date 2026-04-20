package ru.mytheria.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mytheria.Mytheria;
import ru.mytheria.api.client.command.ClientCommandManager;
import ru.mytheria.api.client.draggable.data.DraggableRepository;
import ru.mytheria.api.events.EventManager;
import ru.mytheria.api.events.impl.RenderEvent;
import ru.mytheria.api.util.shader.common.trasparent.Builder;

import java.util.List;

import static ru.mytheria.api.clientannotation.QuickImport.mc;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected TextFieldWidget chatField;
    @Shadow ChatInputSuggestor chatInputSuggestor;

    private static boolean visible = true;
    private String lastClientCommandInput = "";
    private String lastAppliedSuggestion = "";
    private int clientSuggestionIndex = 0;

    protected ChatScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mytheria$renderChatOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        DraggableRepository draggableRepository = Mytheria.getInstance().getDraggableRepository();
        draggableRepository.update(context, delta, mouseX, mouseY);
        draggableRepository.render(context, mc.getRenderTickCounter(), mouseX, mouseY);

        if (visible) {
            renderHints(context);
        }

        if (chatField != null) {
            String input = chatField.getText();
            boolean clientCommand = ClientCommandManager.isClientCommand(input);

            if (!input.equals(lastClientCommandInput) && !input.equals(lastAppliedSuggestion)) {
                clientSuggestionIndex = 0;
            }

            if (clientCommand) {
                List<ClientCommandManager.CommandSuggestion> suggestions = ClientCommandManager.getSuggestions(input);
                if (clientSuggestionIndex >= suggestions.size()) {
                    clientSuggestionIndex = 0;
                }

                chatField.setSuggestion(ClientCommandManager.getSuggestionSuffix(input, clientSuggestionIndex));

                if (chatInputSuggestor != null) {
                    chatInputSuggestor.clearWindow();
                    chatInputSuggestor.setWindowActive(false);
                }
            } else {
                chatField.setSuggestion(null);
                clientSuggestionIndex = 0;
                lastAppliedSuggestion = "";
                if (chatInputSuggestor != null) {
                    chatInputSuggestor.setWindowActive(true);
                }
            }

            lastClientCommandInput = input;
        }

        EventManager.call(new RenderEvent.AfterChat(context, mouseX, mouseY, delta));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mytheria$renderClientCommandSuggestions(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (chatField == null) {
            return;
        }

        List<ClientCommandManager.CommandSuggestion> suggestions = ClientCommandManager.getSuggestions(chatField.getText());
        if (suggestions.isEmpty()) {
            return;
        }

        int visibleSuggestions = Math.min(6, suggestions.size());
        int lineHeight = 12;
        int maxWidth = 120;
        int selectedIndex = Math.min(clientSuggestionIndex, visibleSuggestions - 1);

        for (int i = 0; i < visibleSuggestions; i++) {
            ClientCommandManager.CommandSuggestion suggestion = suggestions.get(i);
            int width = mc.textRenderer.getWidth(suggestion.value()) + mc.textRenderer.getWidth(suggestion.hint()) + 20;
            maxWidth = Math.max(maxWidth, width);
        }

        int boxX = chatField.getX();
        int boxY = chatField.getY() - (visibleSuggestions * lineHeight) - 8;
        int boxBottom = chatField.getY() - 4;

        context.fill(boxX - 4, boxY - 4, boxX + maxWidth, boxBottom, 0xC0101010);
        context.fill(boxX - 4, boxY - 4, boxX + maxWidth, boxY - 3, 0xFFFFFFFF);

        for (int i = 0; i < visibleSuggestions; i++) {
            ClientCommandManager.CommandSuggestion suggestion = suggestions.get(i);
            int lineY = boxY + i * lineHeight;
            if (i == selectedIndex) {
                context.fill(boxX - 2, lineY - 1, boxX + maxWidth - 2, lineY + lineHeight - 2, 0x35FFFFFF);
            }
            context.drawTextWithShadow(mc.textRenderer, suggestion.value(), boxX + 2, lineY, 0xFFFFFFFF);
            context.drawTextWithShadow(
                    mc.textRenderer,
                    suggestion.hint(),
                    boxX + maxWidth - 6 - mc.textRenderer.getWidth(suggestion.hint()),
                    lineY,
                    0xFFBFBFBF
            );
        }
    }

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void mytheria$handleClientCommands(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (chatText == null || !chatText.trim().startsWith(".")) {
            return;
        }

        if (addToHistory && mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addToMessageHistory(chatText);
        }

        ClientCommandManager.execute(chatText);
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void mytheria$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Mytheria.getInstance().getDraggableRepository().mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Mytheria.getInstance().getDraggableRepository().mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mytheria$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (Mytheria.getInstance().getDraggableRepository().keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(false);
            return;
        }

        if (chatField != null && ClientCommandManager.isClientCommand(chatField.getText()) && keyCode == GLFW.GLFW_KEY_TAB) {
            List<ClientCommandManager.CommandSuggestion> suggestions = ClientCommandManager.getSuggestions(chatField.getText());
            if (!suggestions.isEmpty()) {
                boolean backwards = Screen.hasShiftDown() || (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
                if (chatField.getText().equals(lastAppliedSuggestion)) {
                    clientSuggestionIndex = Math.floorMod(clientSuggestionIndex + (backwards ? -1 : 1), suggestions.size());
                } else {
                    clientSuggestionIndex = backwards ? suggestions.size() - 1 : 0;
                }

                String completed = suggestions.get(clientSuggestionIndex).value();
                chatField.setText(completed);
                chatField.setCursorToEnd(false);
                chatField.setSuggestion(ClientCommandManager.getSuggestionSuffix(completed, clientSuggestionIndex));
                lastAppliedSuggestion = completed;
                lastClientCommandInput = completed;
                cir.setReturnValue(true);
                return;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_B && (Screen.hasControlDown() || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)) {
            visible = !visible;
            cir.setReturnValue(true);
        }
    }

    private void renderHints(DrawContext context) {
        renderHint(context, "Нажмите ALT что-бы заблокировать по Y", 30);
        renderHint(context, "Нажмите CTRL + ALT что-бы включить сетку", 42);
        renderHint(context, "Нажмите CTRL + B что-бы скрыть подсказки", 54);
    }

    private void renderHint(DrawContext context, String text, float y) {
        Builder.TEXT_BUILDER
                .text(text)
                .size(8)
                .color(0xFFFFFFFF)
                .font(Builder.SF_SEMIBOLD.get())
                .thickness(0.1f)
                .build()
                .render(
                        context.getMatrices().peek().getPositionMatrix(),
                        ((float) mc.getWindow().getScaledWidth() / 2) - Builder.INTER.get().getWidth(text, 8) / 2,
                        y
                );
    }
}
