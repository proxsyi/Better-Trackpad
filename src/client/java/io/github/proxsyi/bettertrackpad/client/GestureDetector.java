package io.github.proxsyi.bettertrackpad.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public final class GestureDetector {
    private GestureDetector() {
    }

    private static final long CONTEXT_WINDOW_NANOS = 300_000_000L;
    private static final long TWO_FINGER_CONTEXT_NANOS = 550_000_000L;
    private static final long FIRE_COOLDOWN_NANOS = 60_000_000L;
    private static final long GESTURE_GAP_NANOS = 200_000_000L;
    private static final long MAX_HOLD_TICKS = 200L;

    private static final ConcurrentHashMap<Long, double[]> activeTouches = new ConcurrentHashMap<>();
    private static volatile int peakFingers = 0;
    private static volatile double lastTouchX = 0.5;
    private static volatile long lastTouchNanos = 0L;

    private static long lastFireNanos = 0L;
    private static long tickCounter = 0L;

    private static InputConstants.Key heldKey = null;
    private static long heldReleaseTick = 0L;

    private static Field keyField = null;

    public static void onTouch(TouchEvent event) {
        long now = System.nanoTime();
        long sinceLast = now - lastTouchNanos;
        lastTouchNanos = now;

        switch (event.phase) {
            case CANCELLED -> {
                activeTouches.clear();
                peakFingers = 0;
            }
            case BEGAN -> {
                if (activeTouches.isEmpty() || sinceLast >= GESTURE_GAP_NANOS) {
                    activeTouches.clear();
                    peakFingers = 0;
                }
                activeTouches.put(event.touchId, new double[] { event.x, event.y });
                peakFingers = Math.max(peakFingers, activeTouches.size());
                if (activeTouches.size() == 1) {
                    lastTouchX = event.x;
                }
            }
            case MOVED -> {
                activeTouches.put(event.touchId, new double[] { event.x, event.y });
                if (activeTouches.size() == 1) {
                    lastTouchX = event.x;
                }
            }
            case ENDED -> {
                activeTouches.remove(event.touchId);
                if (activeTouches.isEmpty()) {
                    lastTouchX = event.x;
                }
            }
        }
    }

    public static boolean onOsButton(boolean press) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        long now = System.nanoTime();
        long sinceTouch = now - lastTouchNanos;

        if (!press) {
            boolean wasHolding = heldKey != null;
            BetterTrackpadClient.LOGGER.info("[better-trackpad] osbtn release wasHolding={} peak={} active={} sinceTouchMs={}",
                    wasHolding, peakFingers, activeTouches.size(), sinceTouch / 1_000_000L);
            releaseHeld();
            activeTouches.clear();
            peakFingers = 0;
            return wasHolding;
        }

        boolean gameReady = mc.level != null && mc.screen == null && mc.mouseHandler.isMouseGrabbed();
        long window = peakFingers >= 2 ? TWO_FINGER_CONTEXT_NANOS : CONTEXT_WINDOW_NANOS;
        boolean trackpadOrigin = !activeTouches.isEmpty() || sinceTouch < window;
        int fingers = peakFingers > 0 ? peakFingers : 1;
        boolean hold = !activeTouches.isEmpty();

        BetterTrackpadClient.LOGGER.info("[better-trackpad] osbtn press gameReady={} origin={} peak={} active={} lastX={} sinceTouchMs={}",
                gameReady, trackpadOrigin, peakFingers, activeTouches.size(), String.format("%.3f", lastTouchX), sinceTouch / 1_000_000L);

        if (!gameReady) return false;
        if (!trackpadOrigin) return false;

        fire(mc, lastTouchX, fingers, hold);
        return true;
    }

    public static void tick() {
        tickCounter++;
        if (heldKey != null && tickCounter >= heldReleaseTick) {
            KeyMapping.set(heldKey, false);
            heldKey = null;
        }
    }

    private static void fire(Minecraft mc, double x, int fingers, boolean hold) {
        if (!BetterTrackpadConfig.enabled) return;
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

        InputConstants.Key key = resolveKey(target);
        if (key == null || key == InputConstants.UNKNOWN) return;

        BetterTrackpadClient.LOGGER.info("[better-trackpad] fire fingers={} x={} mode={} -> action={}",
                fingers, String.format("%.3f", x), hold ? "hold" : "tap", action);

        if (heldKey != null && heldKey != key) {
            KeyMapping.set(heldKey, false);
            heldKey = null;
        }

        if (hold) {
            KeyMapping.set(key, true);
            KeyMapping.click(key);
            heldKey = key;
            heldReleaseTick = tickCounter + MAX_HOLD_TICKS;
        } else {
            KeyMapping.click(key);
        }
    }

    private static void releaseHeld() {
        if (heldKey == null) return;
        KeyMapping.set(heldKey, false);
        heldKey = null;
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
            if (keyField == null) {
                keyField = KeyMapping.class.getDeclaredField("key");
                keyField.setAccessible(true);
            }
            return (InputConstants.Key) keyField.get(mapping);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
