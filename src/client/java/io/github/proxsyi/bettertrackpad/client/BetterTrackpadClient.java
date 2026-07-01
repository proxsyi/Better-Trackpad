package io.github.proxsyi.bettertrackpad.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterTrackpadClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("better-trackpad");

    private final GestureDetector detector = new GestureDetector(new MinecraftActuator());
    private final PlatformTouchHook hook = new MacTouchHook(detector);
    private boolean hookAttempted = false;

    @Override
    public void onInitializeClient() {
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        if (!isMac) return;

        BetterTrackpadConfigManager.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!hookAttempted) {
                long handle = GLFW.glfwGetCurrentContext();
                if (handle != 0L) {
                    hookAttempted = true;
                    boolean ok = hook.install(handle);
                    LOGGER.info("[better-trackpad] touch hook install: {}", ok);
                }
            }
            detector.tick();
        });
    }
}
