package ru.mytheria.main.ui.clickGui.components.settings;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.mytheria.api.module.settings.Setting;
import ru.mytheria.main.ui.clickGui.Component;

@Getter
@RequiredArgsConstructor()
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class SettingComponent extends Component {

    protected static final float SETTING_WIDTH = 240f / 2 - 10f;
    protected static final float VALUE_COLUMN_WIDTH = 42f;
    protected static final float VALUE_COLUMN_GAP = 6f;

    Setting settingLayer;

    protected float getValueColumnX() {
        return getX() + getWidth() - VALUE_COLUMN_WIDTH;
    }

    protected float getTextColumnWidth() {
        return SETTING_WIDTH - VALUE_COLUMN_WIDTH - VALUE_COLUMN_GAP;
    }
}
