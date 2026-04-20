package ru.mytheria.api.client.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import ru.mytheria.Mytheria;
import ru.mytheria.api.module.Module;
import ru.mytheria.api.util.keyboard.KeyBoardUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClientCommandManager {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Map<String, Integer> KEY_CODES = createKeyCodes();
    private static final List<String> COMMON_KEYS = List.of(
            "R", "G", "V", "B", "C", "X", "Z", "F", "H", "J", "K", "L",
            "TAB", "SPACE", "ENTER", "RSHIFT", "LSHIFT", "RCTRL", "LCTRL", "RALT", "LALT",
            "UP", "DOWN", "LEFT", "RIGHT", "INSERT", "DELETE", "HOME", "END",
            "PAGE_UP", "PAGE_DOWN", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
    );

    private ClientCommandManager() {
    }

    public static void execute(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (!trimmed.startsWith(".")) {
            return;
        }

        List<String> tokens = tokenize(trimmed.substring(1));
        if (tokens.isEmpty()) {
            print(".bind, .rct");
            closeChat();
            return;
        }

        String command = tokens.get(0).toLowerCase(Locale.ROOT);
        switch (command) {
            case "bind" -> executeBind(tokens);
            case "cfg" -> executeCfg(tokens);
            case "rct" -> executeReconnect();
            default -> {
                print("Неизвестная команда: ." + command);
                print("Доступно: .bind, .cfg, .rct");
                closeChat();
            }
        }
    }

    public static List<CommandSuggestion> getSuggestions(String input) {
        if (input == null || !input.startsWith(".")) {
            return List.of();
        }

        String trimmed = input.trim();
        boolean endsWithSpace = input.endsWith(" ");
        List<String> tokens = tokenize(trimmed.substring(1));

        if (tokens.isEmpty()) {
            return List.of(
                    new CommandSuggestion(".bind", "Управление биндами"),
                    new CommandSuggestion(".cfg", "Управление конфигами"),
                    new CommandSuggestion(".rct", "Переподключиться")
            );
        }

        String command = tokens.get(0).toLowerCase(Locale.ROOT);
        if (tokens.size() == 1 && !endsWithSpace) {
            return filterSuggestions(
                    List.of(
                            new CommandSuggestion(".bind", "Управление биндами"),
                            new CommandSuggestion(".cfg", "Управление конфигами"),
                            new CommandSuggestion(".rct", "Переподключиться")
                    ),
                    "." + command
            );
        }

        if (command.equals("bind")) {
            return getBindSuggestions(tokens, endsWithSpace);
        }

        if (command.equals("cfg")) {
            return getCfgSuggestions(tokens, endsWithSpace);
        }

        if (command.equals("rct")) {
            return List.of(new CommandSuggestion(".rct", "Переподключиться"));
        }

        return List.of();
    }

    public static boolean isClientCommand(String input) {
        return input != null && input.trim().startsWith(".");
    }

    public static String getSuggestionValue(String input, int index) {
        List<CommandSuggestion> suggestions = getSuggestions(input);
        if (suggestions.isEmpty()) {
            return input == null ? "" : input;
        }

        int safeIndex = Math.floorMod(index, suggestions.size());
        return suggestions.get(safeIndex).value();
    }

    public static String getSuggestionSuffix(String input, int index) {
        String source = input == null ? "" : input;
        String suggestion = getSuggestionValue(source, index);
        if (suggestion.length() <= source.length() || !suggestion.startsWith(source)) {
            return "";
        }

        return suggestion.substring(source.length());
    }

    private static List<CommandSuggestion> getBindSuggestions(List<String> tokens, boolean endsWithSpace) {
        List<CommandSuggestion> subcommands = List.of(
                new CommandSuggestion(".bind add", "Добавить бинд"),
                new CommandSuggestion(".bind list", "Показать бинды"),
                new CommandSuggestion(".bind clear", "Очистить бинды")
        );

        if (tokens.size() == 1 && endsWithSpace) {
            return subcommands;
        }

        if (tokens.size() == 2 && !endsWithSpace) {
            return filterSuggestions(subcommands, ".bind " + tokens.get(1));
        }

        if (tokens.size() >= 2) {
            String subcommand = tokens.get(1).toLowerCase(Locale.ROOT);
            if (subcommand.equals("add")) {
                if (tokens.size() == 2 && endsWithSpace) {
                    return moduleSuggestions(".bind add ", "");
                }

                if (tokens.size() == 3 && !endsWithSpace) {
                    return moduleSuggestions(".bind add ", tokens.get(2));
                }

                if (tokens.size() == 3 && endsWithSpace) {
                    return keySuggestions(".bind add " + tokens.get(2) + " ", "");
                }

                if (tokens.size() >= 4) {
                    return keySuggestions(".bind add " + tokens.get(2) + " ", tokens.get(tokens.size() - 1));
                }
            }

            if (subcommand.equals("clear")) {
                List<CommandSuggestion> clearSuggestions = new ArrayList<>();
                clearSuggestions.add(new CommandSuggestion(".bind clear all", "Очистить все бинды"));
                clearSuggestions.addAll(moduleSuggestions(".bind clear ", ""));

                if (tokens.size() == 2 && endsWithSpace) {
                    return clearSuggestions;
                }

                if (tokens.size() == 3 && !endsWithSpace) {
                    return filterSuggestions(clearSuggestions, ".bind clear " + tokens.get(2));
                }
            }
        }

        return List.of();
    }

    private static List<CommandSuggestion> getCfgSuggestions(List<String> tokens, boolean endsWithSpace) {
        List<CommandSuggestion> subcommands = List.of(
                new CommandSuggestion(".cfg save", "Сохранить конфиг"),
                new CommandSuggestion(".cfg load", "Загрузить конфиг"),
                new CommandSuggestion(".cfg dir", "Открыть папку конфигов")
        );

        if (tokens.size() == 1 && endsWithSpace) {
            return subcommands;
        }

        if (tokens.size() == 2 && !endsWithSpace) {
            return filterSuggestions(subcommands, ".cfg " + tokens.get(1));
        }

        if (tokens.size() >= 2) {
            String subcommand = tokens.get(1).toLowerCase(Locale.ROOT);
            if (subcommand.equals("save") || subcommand.equals("load")) {
                String prefix = ".cfg " + subcommand + " ";
                if (tokens.size() == 2 && endsWithSpace) {
                    return configSuggestions(prefix, "");
                }

                if (tokens.size() >= 3) {
                    String query = tokens.get(tokens.size() - 1);
                    return configSuggestions(prefix, query);
                }
            }
        }

        return List.of();
    }

    private static List<CommandSuggestion> moduleSuggestions(String prefix, String query) {
        String normalizedQuery = normalize(query);
        return Mytheria.getInstance().getModuleManager().getModuleLayers().stream()
                .map(module -> module.getModuleName().getString())
                .filter(name -> normalizedQuery.isEmpty() || normalize(name).contains(normalizedQuery))
                .map(name -> new CommandSuggestion(prefix + name, "Функция"))
                .toList();
    }

    private static List<CommandSuggestion> keySuggestions(String prefix, String query) {
        String normalizedQuery = normalize(query);
        List<CommandSuggestion> suggestions = new ArrayList<>();

        for (String key : COMMON_KEYS) {
            if (!KEY_CODES.containsKey(normalize(key))) {
                continue;
            }

            if (!normalizedQuery.isEmpty() && !normalize(key).contains(normalizedQuery)) {
                continue;
            }

            suggestions.add(new CommandSuggestion(prefix + key, "Клавиша"));
        }

        return suggestions;
    }

    private static List<CommandSuggestion> configSuggestions(String prefix, String query) {
        String normalizedQuery = normalize(query);
        return Mytheria.getInstance().getConfigurationService().asList().stream()
                .filter(name -> normalizedQuery.isEmpty() || normalize(name).contains(normalizedQuery))
                .map(name -> new CommandSuggestion(prefix + name, "Конфиг"))
                .toList();
    }

    private static List<CommandSuggestion> filterSuggestions(List<CommandSuggestion> suggestions, String query) {
        String normalizedQuery = normalize(query);
        return suggestions.stream()
                .filter(suggestion -> normalize(suggestion.value()).contains(normalizedQuery))
                .toList();
    }

    private static void executeBind(List<String> tokens) {
        if (tokens.size() < 2) {
            print(".bind add <функция> <клавиша>");
            print(".bind list");
            print(".bind clear all/<функция>");
            closeChat();
            return;
        }

        String subcommand = tokens.get(1).toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "add" -> executeBindAdd(tokens);
            case "list" -> executeBindList();
            case "clear" -> executeBindClear(tokens);
            default -> {
                print("Неизвестная подкоманда .bind: " + subcommand);
                print("Доступно: add, list, clear");
                closeChat();
            }
        }
    }

    private static void executeBindAdd(List<String> tokens) {
        if (tokens.size() < 4) {
            print("Использование: .bind add <функция> <клавиша>");
            closeChat();
            return;
        }

        String moduleName = joinTokens(tokens, 2, tokens.size() - 1);
        String keyName = tokens.get(tokens.size() - 1);

        Module module = findModule(moduleName);
        if (module == null) {
            print("Функция не найдена: " + moduleName);
            closeChat();
            return;
        }

        Integer keyCode = KEY_CODES.get(normalize(keyName));
        if (keyCode == null) {
            print("Клавиша не найдена: " + keyName);
            closeChat();
            return;
        }

        module.setKey(keyCode);
        print("Бинд добавлен: " + module.getModuleName().getString() + " -> " + KeyBoardUtil.translate(keyCode));
        Mytheria.getInstance().getConfigurationService().save("autosave");
        closeChat();
    }

    private static void executeBindList() {
        List<Module> boundModules = Mytheria.getInstance().getModuleManager().getModuleLayers().stream()
                .filter(module -> module.getKey() != null && module.getKey() != -1)
                .toList();

        if (boundModules.isEmpty()) {
            print("Бинды пустые.");
            closeChat();
            return;
        }

        print("Список биндов:");
        boundModules.forEach(module ->
                print(module.getModuleName().getString() + " -> " + KeyBoardUtil.translate(module.getKey()))
        );
        closeChat();
    }

    private static void executeBindClear(List<String> tokens) {
        if (tokens.size() < 3) {
            print("Использование: .bind clear all/<функция>");
            closeChat();
            return;
        }

        String target = joinTokens(tokens, 2, tokens.size());
        if (target.equalsIgnoreCase("all")) {
            Mytheria.getInstance().getModuleManager().getModuleLayers()
                    .forEach(module -> module.setKey(-1));
            print("Все бинды очищены.");
            Mytheria.getInstance().getConfigurationService().save("autosave");
            closeChat();
            return;
        }

        Module module = findModule(target);
        if (module == null) {
            print("Функция не найдена: " + target);
            closeChat();
            return;
        }

        module.setKey(-1);
        print("Бинд очищен: " + module.getModuleName().getString());
        Mytheria.getInstance().getConfigurationService().save("autosave");
        closeChat();
    }

    private static void executeCfg(List<String> tokens) {
        if (tokens.size() < 2) {
            print(".cfg save <название>");
            print(".cfg load <название>");
            print(".cfg dir");
            closeChat();
            return;
        }

        String subcommand = tokens.get(1).toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "save" -> executeCfgSave(tokens);
            case "load" -> executeCfgLoad(tokens);
            case "dir" -> executeCfgDir();
            default -> {
                print("Неизвестная подкоманда .cfg: " + subcommand);
                print("Доступно: save, load, dir");
                closeChat();
            }
        }
    }

    private static void executeCfgSave(List<String> tokens) {
        if (tokens.size() < 3) {
            print("Использование: .cfg save <название>");
            closeChat();
            return;
        }

        String name = joinTokens(tokens, 2, tokens.size());
        Mytheria.getInstance().getConfigurationService().save(name);
        print("Конфиг сохранен: " + name);
        closeChat();
    }

    private static void executeCfgLoad(List<String> tokens) {
        if (tokens.size() < 3) {
            print("Использование: .cfg load <название>");
            closeChat();
            return;
        }

        String name = joinTokens(tokens, 2, tokens.size());
        String normalizedName = stripJson(name);
        if (!Mytheria.getInstance().getConfigurationService().asList().stream().anyMatch(cfg -> cfg.equalsIgnoreCase(normalizedName))) {
            print("Конфиг не найден: " + name);
            closeChat();
            return;
        }

        Mytheria.getInstance().getConfigurationService().load(name);
        print("Конфиг загружен: " + name);
        closeChat();
    }

    private static void executeCfgDir() {
        File dir = new File(MC.runDirectory.getAbsolutePath() + "/mytheria/configs/");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        Util.getOperatingSystem().open(dir);
        print("Открыта папка конфигов: " + dir.getAbsolutePath());
        closeChat();
    }

    private static void executeReconnect() {
        ServerInfo currentServer = MC.getCurrentServerEntry();
        if (currentServer == null) {
            print("Сейчас нет сервера для переподключения.");
            closeChat();
            return;
        }

        ServerInfo reconnectServer = new ServerInfo(currentServer.name, currentServer.address, currentServer.getServerType());
        reconnectServer.copyWithSettingsFrom(currentServer);

        MC.disconnect(new MultiplayerScreen(new TitleScreen()));
        ConnectScreen.connect(
                new MultiplayerScreen(new TitleScreen()),
                MC,
                ServerAddress.parse(reconnectServer.address),
                reconnectServer,
                false,
                null
        );
    }

    private static Module findModule(String inputName) {
        String normalizedInput = normalize(inputName);
        return Mytheria.getInstance().getModuleManager().getModuleLayers().stream()
                .filter(module -> normalize(module.getModuleName().getString()).equals(normalizedInput))
                .findFirst()
                .orElse(null);
    }

    private static String joinTokens(List<String> tokens, int from, int to) {
        return String.join(" ", tokens.subList(from, to));
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < input.length(); i++) {
            char symbol = input.charAt(i);

            if (symbol == '\'' || symbol == '"') {
                quoted = !quoted;
                continue;
            }

            if (Character.isWhitespace(symbol) && !quoted) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(symbol);
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static Map<String, Integer> createKeyCodes() {
        Map<String, Integer> keyCodes = new LinkedHashMap<>();
        for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_MENU; code++) {
            String translated = KeyBoardUtil.translate(code);
            if ("N/A".equalsIgnoreCase(translated) || translated.startsWith("UNKNOWN")) {
                continue;
            }

            keyCodes.put(normalize(translated), code);
        }

        keyCodes.put("PAGEUP", GLFW.GLFW_KEY_PAGE_UP);
        keyCodes.put("PAGEDOWN", GLFW.GLFW_KEY_PAGE_DOWN);
        keyCodes.put("RIGHTSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        keyCodes.put("LEFTSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        keyCodes.put("RIGHTCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        keyCodes.put("LEFTCTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        keyCodes.put("RIGHTALT", GLFW.GLFW_KEY_RIGHT_ALT);
        keyCodes.put("LEFTALT", GLFW.GLFW_KEY_LEFT_ALT);

        return keyCodes;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.toUpperCase(Locale.ROOT)
                .replace("KEYBOARD", "")
                .replace("MOUSE", "MOUSE")
                .replace(".", "")
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
    }

    private static String stripJson(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT).endsWith(".json")
                ? value.substring(0, value.length() - 5)
                : value;
    }

    private static void print(String message) {
        if (MC.inGameHud == null) {
            return;
        }

        MC.inGameHud.getChatHud().addMessage(Text.of(message));
    }

    private static void closeChat() {
        MC.setScreen(null);
    }

    public record CommandSuggestion(String value, String hint) {
    }
}
