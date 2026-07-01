package io.github.proxsyi.bettertrackpad.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public final class BetterTrackpadConfigManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir().resolve("better-trackpad.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BetterTrackpadConfigManager() {}

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("enabled"))        BetterTrackpadConfig.enabled        = obj.get("enabled").getAsBoolean();
            if (obj.has("debug"))          BetterTrackpadConfig.debug          = obj.get("debug").getAsBoolean();
            if (obj.has("leftZoneMax"))    BetterTrackpadConfig.leftZoneMax    = obj.get("leftZoneMax").getAsFloat();
            if (obj.has("rightZoneMin"))   BetterTrackpadConfig.rightZoneMin   = obj.get("rightZoneMin").getAsFloat();
            if (obj.has("leftOneFinger"))  BetterTrackpadConfig.leftOneFinger  = TrackpadAction.valueOf(obj.get("leftOneFinger").getAsString());
            if (obj.has("rightOneFinger")) BetterTrackpadConfig.rightOneFinger = TrackpadAction.valueOf(obj.get("rightOneFinger").getAsString());
            if (obj.has("twoFinger"))      BetterTrackpadConfig.twoFinger      = TrackpadAction.valueOf(obj.get("twoFinger").getAsString());
            BetterTrackpadClient.debug("[better-trackpad] config loaded from {}", CONFIG_PATH);
        } catch (Exception e) {
            BetterTrackpadClient.LOGGER.warn("[better-trackpad] failed to load config: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled",        BetterTrackpadConfig.enabled);
            obj.addProperty("debug",          BetterTrackpadConfig.debug);
            obj.addProperty("leftZoneMax",     BetterTrackpadConfig.leftZoneMax);
            obj.addProperty("rightZoneMin",    BetterTrackpadConfig.rightZoneMin);
            obj.addProperty("leftOneFinger",   BetterTrackpadConfig.leftOneFinger.name());
            obj.addProperty("rightOneFinger",  BetterTrackpadConfig.rightOneFinger.name());
            obj.addProperty("twoFinger",       BetterTrackpadConfig.twoFinger.name());
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
            BetterTrackpadClient.debug("[better-trackpad] config saved to {}", CONFIG_PATH);
        } catch (Exception e) {
            BetterTrackpadClient.LOGGER.warn("[better-trackpad] failed to save config: {}", e.getMessage());
        }
    }
}
