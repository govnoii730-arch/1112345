package ru.mytheria.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mytheria.main.ui.menu.CloudAltManagerScreen;
import ru.mytheria.main.ui.menu.CloudLicenseScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mytheria$addAltManagerButton(CallbackInfo ci) {
        addDrawableChild(ButtonWidget.builder(
                        Text.of("Alt Manager"),
                        button -> {
                            if (client != null) {
                                client.setScreen(new CloudAltManagerScreen((Screen) (Object) this));
                            }
                        })
                .dimensions(width - 108, 8, 100, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(
                        Text.of("License"),
                        button -> {
                            if (client != null) {
                                client.setScreen(new CloudLicenseScreen((Screen) (Object) this));
                            }
                        })
                .dimensions(width - 108, 32, 100, 20)
                .build());
    }
}
