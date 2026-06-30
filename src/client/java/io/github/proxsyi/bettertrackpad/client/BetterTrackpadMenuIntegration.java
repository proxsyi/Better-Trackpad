package io.github.proxsyi.bettertrackpad.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public final class BetterTrackpadMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Better Trackpad"))
                .setSavingRunnable(BetterTrackpadConfigManager::save);

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Tab 1: Bindings
            ConfigCategory bindings = builder.getOrCreateCategory(Component.literal("Bindings"));

            bindings.addEntry(entryBuilder
                .startEnumSelector(Component.literal("1-Finger Left"), TrackpadAction.class, BetterTrackpadConfig.leftOneFinger)
                .setDefaultValue(TrackpadAction.LEFT_CLICK)
                .setSaveConsumer(val -> BetterTrackpadConfig.leftOneFinger = val)
                .build());

            bindings.addEntry(entryBuilder
                .startEnumSelector(Component.literal("1-Finger Right"), TrackpadAction.class, BetterTrackpadConfig.rightOneFinger)
                .setDefaultValue(TrackpadAction.RIGHT_CLICK)
                .setSaveConsumer(val -> BetterTrackpadConfig.rightOneFinger = val)
                .build());

            bindings.addEntry(entryBuilder
                .startEnumSelector(Component.literal("2-Finger Tap"), TrackpadAction.class, BetterTrackpadConfig.twoFinger)
                .setDefaultValue(TrackpadAction.MIDDLE_CLICK)
                .setSaveConsumer(val -> BetterTrackpadConfig.twoFinger = val)
                .build());

            // Tab 2: Configuration
            ConfigCategory config = builder.getOrCreateCategory(Component.literal("Configuration"));

            config.addEntry(entryBuilder
                .startBooleanToggle(Component.literal("Enabled"), BetterTrackpadConfig.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(val -> BetterTrackpadConfig.enabled = val)
                .build());

            config.addEntry(entryBuilder
                .startFloatField(Component.literal("Left Zone Max (0-1)"), BetterTrackpadConfig.leftZoneMax)
                .setDefaultValue(0.45f)
                .setMin(0.0f)
                .setMax(1.0f)
                .setSaveConsumer(val -> BetterTrackpadConfig.leftZoneMax = val)
                .build());

            config.addEntry(entryBuilder
                .startFloatField(Component.literal("Right Zone Min (0-1)"), BetterTrackpadConfig.rightZoneMin)
                .setDefaultValue(0.55f)
                .setMin(0.0f)
                .setMax(1.0f)
                .setSaveConsumer(val -> BetterTrackpadConfig.rightZoneMin = val)
                .build());

            return builder.build();
        };
    }
}
