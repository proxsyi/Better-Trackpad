package io.github.proxsyi.bettertrackpad.client;

import java.util.concurrent.ConcurrentHashMap;

public final class GestureDetector implements TouchListener {
    private static final long CONTEXT_WINDOW_NANOS = 300_000_000L;
    private static final long TWO_FINGER_CONTEXT_NANOS = 550_000_000L;
    private static final long FIRE_COOLDOWN_NANOS = 60_000_000L;
    private static final long GESTURE_GAP_NANOS = 200_000_000L;
    private static final long HOLD_THRESHOLD_TICKS = 5L;

    private final TrackpadActuator actuator;

    private final ConcurrentHashMap<Long, double[]> activeTouches = new ConcurrentHashMap<>();
    private volatile int peakFingers = 0;
    private volatile double lastTouchX = 0.5;
    private volatile long lastTouchNanos = 0L;

    private long lastFireNanos = 0L;
    private long tickCounter = 0L;

    private boolean buttonDown = false;
    private long buttonDownTick = 0L;
    private TrackpadAction pendingAction = null;
    private boolean escalated = false;

    public GestureDetector(TrackpadActuator actuator) {
        this.actuator = actuator;
    }

    @Override
    public void onTouch(TouchEvent event) {
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

    @Override
    public boolean onButton(boolean press) {
        if (!actuator.isReady()) {
            if (!press) {
                clearGesture();
                actuator.releaseHold();
            }
            return false;
        }

        long now = System.nanoTime();
        long sinceTouch = now - lastTouchNanos;

        if (!press) {
            BetterTrackpadClient.LOGGER.info("[better-trackpad] osbtn release escalated={} peak={} active={} sinceTouchMs={}",
                    escalated, peakFingers, activeTouches.size(), sinceTouch / 1_000_000L);
            clearGesture();
            actuator.releaseHold();
            activeTouches.clear();
            peakFingers = 0;
            return true;
        }

        long window = peakFingers >= 2 ? TWO_FINGER_CONTEXT_NANOS : CONTEXT_WINDOW_NANOS;
        boolean trackpadOrigin = !activeTouches.isEmpty() || sinceTouch < window;
        int fingers = peakFingers > 0 ? peakFingers : 1;

        BetterTrackpadClient.LOGGER.info("[better-trackpad] osbtn press origin={} peak={} active={} lastX={} sinceTouchMs={}",
                trackpadOrigin, peakFingers, activeTouches.size(), String.format("%.3f", lastTouchX), sinceTouch / 1_000_000L);

        if (trackpadOrigin) {
            fireClick(lastTouchX, fingers);
        }
        return true;
    }

    public void tick() {
        tickCounter++;
        actuator.tick();
        if (buttonDown && !escalated && pendingAction != null
                && tickCounter - buttonDownTick >= HOLD_THRESHOLD_TICKS) {
            actuator.beginHold(pendingAction);
            escalated = true;
            BetterTrackpadClient.LOGGER.info("[better-trackpad] escalate hold action={}", pendingAction);
        }
    }

    private void fireClick(double x, int fingers) {
        if (!BetterTrackpadConfig.enabled) return;

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

        BetterTrackpadClient.LOGGER.info("[better-trackpad] fire fingers={} x={} mode=click -> action={}",
                fingers, String.format("%.3f", x), action);
        actuator.click(action);

        pendingAction = action;
        buttonDown = true;
        buttonDownTick = tickCounter;
        escalated = false;
    }

    private void clearGesture() {
        buttonDown = false;
        pendingAction = null;
        escalated = false;
    }

    private static TrackpadAction resolveAction(double x, int fingers) {
        if (fingers >= 2) return BetterTrackpadConfig.twoFinger;
        if (x < BetterTrackpadConfig.leftZoneMax) return BetterTrackpadConfig.leftOneFinger;
        if (x > BetterTrackpadConfig.rightZoneMin) return BetterTrackpadConfig.rightOneFinger;
        return null;
    }
}
