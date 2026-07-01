package io.github.proxsyi.bettertrackpad.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GestureDetector {
    private static final long CONTEXT_WINDOW_NANOS = 300_000_000L;
    private static final long FIRE_COOLDOWN_NANOS = 60_000_000L;
    private static final long MAX_HOLD_TICKS = 200L;

    private static final Map<Long, double[]> activeTouches = new ConcurrentHashMap<>();

    private static volatile int peakFingers = 0;
    private static volatile double lastTouchX = 0.5;
    private static volatile long lastTouchNanos = 0L;

    private static long lastFireNanos = 0L;
    private static long tickCounter = 0L;

    private static InputConstants.Key heldKey = null;
    private static long heldReleaseTick = 0L;

    private GestureDetector() {}

    public static void onTouch(TouchEvent e) {
        lastTouchNanos = System.nanoTime();
        switch (e.phase) {
            case CANCELLED -> activeTouches.clear();
            case BEGAN -> {
                if (activeTouches.isEmpty()) peakFingers = 0;
                activeTouches.put(e.touchId, new double[]{ e.x, e.y });
                peakFingers = Math.max(peakFingers, activeTouches.size());
                if (activeTouches.size() == 1) lastTouchX = e.x;
            }
            case MOVED -> {
                double[] pos = activeTouches.get(e.touchId);
                if (pos != null) { pos[0] = e.x; pos[1] = e.y; }
                if (activeTouches.size() == 1) lastTouchX = e.x;
            }
            case ENDED -> {
                activeTouches.remove(e.touchId);
                if (activeTouches.isEmpty()) lastTouchX = e.x;
            }
        }
    }

    public static boolean onOsButton(boolean press) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        if (mc.level == null || mc.screen != null || !mc.mouseHandler.isMouseGrabbed()) return false;

        long now = System.nanoTime();
        boolean trackpadOrigin = !activeTouches.isEmpty() || (now - lastTouchNanos) < CONTEXT_WINDOW_NANOS;
        if (!trackpadOrigin) return false;

        if (press) {
            int fingers = peakFingers > 0 ? peakFingers : 1;
            fire(lastTouchX, fingers);
        } else {
            releaseHeld(mc);
        }
        return true;
    }

    public static void tick() {
        tickCounter++;
        if (heldKey != null && tickCounter >= heldReleaseTick) {
            KeyMapping.set(heldKey, false);
            heldKey = null;
        }
    }

    private static TrackpadAction resolveAction(double x, int fingers) {
        if (fingers >= 2) return BetterTrackpadConfig.twoFinger;
        if (x < BetterTrackpadConfig.leftZoneMax) return BetterTrackpadConfig.leftOneFinger;
        if (x > BetterTrackpadConfig.rightZoneMin) return BetterTrackpadConfig.rightOneFinger;
        return null;
    }

    private static KeyMapping keyForAction(Minecraft mc, TrackpadAction action) {
        return switch (action) {
            case LEFT_CLICK -> mc.options.keyAttack;
            case RIGHT_CLICK -> mc.options.keyUse;
            case MIDDLE_CLICK -> mc.options.keyPickItem;
            case NONE -> null;
        };
    }

    private static InputConstants.Key resolveKey(KeyMapping mapping) {
        try {
            java.lang.reflect.Field f = KeyMapping.class.getDeclaredField("key");
            f.setAccessible(true);
            return (InputConstants.Key) f.get(mapping);
        } catch (Exception ex) {
            return InputConstants.UNKNOWN;
        }
    }

    private static void releaseHeld(Minecraft mc) {
        InputConstants.Key key = heldKey;
        if (key == null) return;
        heldKey = null;
        mc.execute(() -> KeyMapping.set(key, false));
    }

    private static void fire(double x, int fingers) {
        if (!BetterTrackpadConfig.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.level == null || mc.screen != null) return;

        TrackpadAction action = resolveAction(x, fingers);
        if (action == null) {
            BetterTrackpadClient.LOGGER.info("[better-trackpad] deadzone x={}", String.format("%.3f", x));
            return;
        }

        long now = System.nanoTime();
        if (now - lastFireNanos < FIRE_COOLDOWN_NANOS) {
            BetterTrackpadClient.LOGGER.info("[better-trackpad] debounced fingers={} action={}", fingers, action);
            return;
        }
        lastFireNanos = now;

        if (action == TrackpadAction.NONE) {
            BetterTrackpadClient.LOGGER.info("[better-trackpad] suppress fingers={}", fingers);
            return;
        }

        KeyMapping target = keyForAction(mc, action);
        if (target == null) return;

        BetterTrackpadClient.LOGGER.info("[better-trackpad] fire fingers={} x={} -> action={}", fingers, String.format("%.3f", x), action);
        long deadline = tickCounter + MAX_HOLD_TICKS;
        mc.execute(() -> {
            InputConstants.Key key = resolveKey(target);
            if (key == null || key == InputConstants.UNKNOWN) return;
            if (heldKey != null && heldKey != key) {
                KeyMapping.set(heldKey, false);
            }
            KeyMapping.set(key, true);
            KeyMapping.click(key);
            heldKey = key;
            heldReleaseTick = deadline;
        });
    }
}
