package ru.mytheria.api.client.managers;

import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import ru.mytheria.Mytheria;
import ru.mytheria.api.clientannotation.QuickImport;
import ru.mytheria.api.events.impl.KeyEvent;
import ru.mytheria.api.events.impl.ModuleEvent;
import ru.mytheria.api.module.Module;
import ru.mytheria.main.module.movement.FTHelper;
import ru.mytheria.main.module.movement.ItemSwap;
import ru.mytheria.main.module.movement.LockSlot;
import ru.mytheria.main.module.movement.Sprint;
import ru.mytheria.main.module.render.Armor;
import ru.mytheria.main.module.render.ArmorInvise;
import ru.mytheria.main.module.render.Animations;
import ru.mytheria.main.module.render.AspectRatio;
import ru.mytheria.main.module.render.Binds;
import ru.mytheria.main.module.render.BlockOverlay;
import ru.mytheria.main.module.render.Cooldowns;
import ru.mytheria.main.module.render.CustomFog;
import ru.mytheria.main.module.render.DiscordRTC;
import ru.mytheria.main.module.render.FullBright;
import ru.mytheria.main.module.render.Interface;
import ru.mytheria.main.module.render.Inventory;
import ru.mytheria.main.module.render.ItemPhysic;
import ru.mytheria.main.module.render.Particles;
import ru.mytheria.main.module.render.Potions;
import ru.mytheria.main.module.render.Removals;
import ru.mytheria.main.module.render.TargetESP;
import ru.mytheria.main.module.render.TargetHUD;
import ru.mytheria.main.module.render.Wmark;
import ru.mytheria.main.module.render.ShullkerPreview;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Getter
public final class ModuleManager implements QuickImport {

    List<Module> moduleLayers = new ArrayList<>();
    private long lastLicenseWarnAt;

    public ModuleManager() {
        Mytheria.getInstance().getEventProvider().subscribe(this);
    }

    public void init() {
        moduleLayers.addAll(
                List.of(
                        new Interface(),
                        new Wmark(),
                        new Binds(),
                        new Cooldowns(),
                        new Potions(),
                        new Inventory(),
                        new Armor(),
                        new TargetHUD(),
                        new Removals(),
                        new TargetESP(),
                        new CustomFog(),
                        new Animations(),
                        new BlockOverlay(),
                        new ShullkerPreview(),
                        new AspectRatio(),
                        new Particles(),
                        new ArmorInvise(),
                        new ItemPhysic(),
                        new FullBright(),
                        new DiscordRTC(),
                        new ItemSwap(),
                        new FTHelper(),
                        new LockSlot(),
                        new Sprint()
                )
        );

        moduleLayers.forEach(Mytheria.getInstance().getEventProvider()::subscribe);
        moduleLayers.stream()
                .filter(module -> module instanceof Interface
                        || module instanceof Wmark
                        || module instanceof Binds
                        || module instanceof Cooldowns
                        || module instanceof Potions
                        || module instanceof Inventory
                        || module instanceof Armor
                        || module instanceof TargetHUD
                        || module instanceof DiscordRTC)
                .forEach(module -> module.setEnabled(true));
    }

    public Module find(Class<? extends Module> clazz) {
        return moduleLayers.stream()
                .filter(e -> e.getClass().equals(clazz))
                .findFirst()
                .orElse(null);
    }

    public List<Module> filter(Predicate<Module> predicate) {
        return moduleLayers.stream()
                .filter(predicate)
                .toList();
    }

    public void forEach(Consumer<Module> action) {
        moduleLayers.forEach(action);
    }

    @EventHandler
    private void keyEventListener(KeyEvent keyEvent) {
        moduleLayers.forEach(module -> {
            if (keyEvent.getKey() == module.getKey() && keyEvent.getAction() == 1 && mc.currentScreen == null) {
                if (!canUseModule(module)) {
                    notifyLicenseBlocked();
                    return;
                }
                module.toggleEnabled();
                Mytheria.getInstance().getConfigurationService().save("autosave");
            }
        });
    }

    @EventHandler
    private void toggleEventListener(ModuleEvent.ToggleEvent toggleEvent) {
        moduleLayers.forEach(module -> {
            if (toggleEvent.getModuleLayer().equals(module)) {
                if (!canUseModule(module)) {
                    notifyLicenseBlocked();
                    return;
                }
                module.toggleEnabled();
                Mytheria.getInstance().getConfigurationService().save("autosave");
            }
        });
    }

    public void enforceLicenseState() {
        if (Mytheria.getInstance().getLicenseService() == null || Mytheria.getInstance().getLicenseService().canUsePremiumFeatures()) {
            return;
        }

        moduleLayers.stream()
                .filter(module -> !isAlwaysAllowed(module) && Boolean.TRUE.equals(module.getEnabled()))
                .forEach(module -> module.setEnabled(false));
    }

    private boolean canUseModule(Module module) {
        if (isAlwaysAllowed(module)) {
            return true;
        }

        return Mytheria.getInstance().getLicenseService() != null
                && Mytheria.getInstance().getLicenseService().canUsePremiumFeatures();
    }

    private boolean isAlwaysAllowed(Module module) {
        return module instanceof Interface || module instanceof Wmark;
    }

    private void notifyLicenseBlocked() {
        long now = System.currentTimeMillis();
        if (now - lastLicenseWarnAt < 1500L) {
            return;
        }

        lastLicenseWarnAt = now;
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.inGameHud == null) {
            return;
        }

        minecraft.inGameHud.getChatHud().addMessage(Text.of("Лицензия не активна. Открой Main Menu -> License"));
    }
}
