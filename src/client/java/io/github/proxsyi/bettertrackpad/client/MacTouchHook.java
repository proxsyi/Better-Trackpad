package io.github.proxsyi.bettertrackpad.client;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWNativeCocoa;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MacTouchHook {
    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private static final Function MSG_SEND             = OBJC.getFunction("objc_msgSend");
    private static final Function SEL_REGISTER_NAME    = OBJC.getFunction("sel_registerName");
    private static final Function CLASS_REPLACE_METHOD = OBJC.getFunction("class_replaceMethod");
    private static final Function OBJECT_GET_CLASS     = OBJC.getFunction("object_getClass");

    private static final long NS_TOUCH_TYPE_MASK_INDIRECT = 1L << 1;
    private static final long NS_TOUCH_PHASE_BEGAN        = 0x1;
    private static final long NS_TOUCH_PHASE_ENDED        = 0x8;
    private static final long NS_TOUCH_PHASE_CANCELLED    = 0x10;
    private static final double TAP_MAX_DELTA = 0.08;

    // key = Pointer.nativeValue(touch.identity) — stable across events for same finger
    private static final Map<Long, double[]> activeTouches = new ConcurrentHashMap<>();

    // Set by fireTap(); consumed by the GLFW mouse button intercept
    private static volatile TrackpadAction pendingTapAction = null;

    // Holds the previous GLFW mouse button callback so we can chain to it
    private static GLFWMouseButtonCallbackI prevGlfwCallback = null;

    private static boolean installed = false;
    private static TouchHandler beganHandler;
    private static TouchHandler movedHandler;
    private static TouchHandler endedHandler;
    private static TouchHandler cancelledHandler;

    public interface TouchHandler extends Callback {
        void invoke(Pointer self, Pointer cmd, Pointer event);
    }

    public static class CGPoint extends Structure {
        public double x;
        public double y;

        public static class ByValue extends CGPoint implements Structure.ByValue {}

        @Override
        protected List<String> getFieldOrder() {
            return List.of("x", "y");
        }
    }

    private MacTouchHook() {}

    private static Pointer sel(String name) {
        return SEL_REGISTER_NAME.invokePointer(new Object[]{ name });
    }

    private static Pointer msgPtr(Pointer receiver, String selName, Object... args) {
        Object[] full = new Object[2 + args.length];
        full[0] = receiver;
        full[1] = sel(selName);
        System.arraycopy(args, 0, full, 2, args.length);
        return MSG_SEND.invokePointer(full);
    }

    private static long msgLong(Pointer receiver, String selName, Object... args) {
        Object[] full = new Object[2 + args.length];
        full[0] = receiver;
        full[1] = sel(selName);
        System.arraycopy(args, 0, full, 2, args.length);
        return MSG_SEND.invokeLong(full);
    }

    private static CGPoint.ByValue touchPos(Pointer touch) {
        return (CGPoint.ByValue) MSG_SEND.invoke(
            CGPoint.ByValue.class,
            new Object[]{ touch, sel("normalizedPosition") }
        );
    }

    // Returns a stable identity key for a touch — consistent across began/ended events
    private static long touchKey(Pointer touch) {
        Pointer identity = msgPtr(touch, "identity");
        return identity != null ? Pointer.nativeValue(identity) : Pointer.nativeValue(touch);
    }

    private static void replaceMethod(Pointer viewClass, String selName, TouchHandler impl) {
        CLASS_REPLACE_METHOD.invokePointer(new Object[]{ viewClass, sel(selName), impl, "v@:@" });
    }

    private static void processTouches(Pointer event, long phase, boolean ended, boolean cancelled) {
        if (cancelled) {
            BetterTrackpadClient.LOGGER.info("[better-trackpad] cancelled, clearing {} active", activeTouches.size());
            activeTouches.clear();
            return;
        }

        Pointer touchSet = msgPtr(event, "touchesMatchingPhase:inView:", phase, (Pointer) null);
        if (touchSet == null) return;
        long count = msgLong(touchSet, "count");
        if (count == 0) return;

        Pointer enumerator = msgPtr(touchSet, "objectEnumerator");
        if (enumerator == null) return;

        int tapCount   = 0;
        double sumX    = 0;
        int fingerCount = (int) count;

        Pointer touch;
        while ((touch = msgPtr(enumerator, "nextObject")) != null) {
            long key = touchKey(touch);
            CGPoint.ByValue pos = touchPos(touch);
            if (pos == null) continue;

            BetterTrackpadClient.LOGGER.info("[better-trackpad] phase={} ended={} key={} x={} y={} active={}",
                phase, ended, key, String.format("%.3f", pos.x), String.format("%.3f", pos.y), activeTouches.size());

            if (!ended) {
                activeTouches.put(key, new double[]{ pos.x, pos.y });
            } else {
                double[] start = activeTouches.remove(key);
                if (start == null) {
                    BetterTrackpadClient.LOGGER.info("[better-trackpad] no start for key={}", key);
                    continue;
                }
                double dx = Math.abs(pos.x - start[0]);
                double dy = Math.abs(pos.y - start[1]);
                BetterTrackpadClient.LOGGER.info("[better-trackpad] tap check dx={} dy={}", String.format("%.3f", dx), String.format("%.3f", dy));
                if (dx < TAP_MAX_DELTA && dy < TAP_MAX_DELTA) {
                    tapCount++;
                    sumX += pos.x;
                }
            }
        }

        if (ended) {
            // Use total concurrent fingers = tapping now + still active
            int totalFingers = tapCount + activeTouches.size();
            BetterTrackpadClient.LOGGER.info("[better-trackpad] ended tapCount={} totalFingers={}", tapCount, totalFingers);
            if (tapCount > 0) {
                fireTap(sumX / tapCount, totalFingers > 0 ? totalFingers : fingerCount);
            }
        }
    }

    private static InputConstants.Key resolveKey(KeyMapping mapping) {
        try {
            java.lang.reflect.Field f = KeyMapping.class.getDeclaredField("key");
            f.setAccessible(true);
            return (InputConstants.Key) f.get(mapping);
        } catch (Exception e) {
            return InputConstants.UNKNOWN;
        }
    }

    private static void fireTap(double x, int fingers) {
        if (!BetterTrackpadConfig.enabled) return;

        TrackpadAction action;
        if (fingers >= 2) {
            action = BetterTrackpadConfig.twoFinger;
        } else if (x < BetterTrackpadConfig.leftZoneMax) {
            action = BetterTrackpadConfig.leftOneFinger;
        } else if (x > BetterTrackpadConfig.rightZoneMin) {
            action = BetterTrackpadConfig.rightOneFinger;
        } else {
            BetterTrackpadClient.LOGGER.info("[better-trackpad] deadzone x={}", String.format("%.3f", x));
            return;
        }

        BetterTrackpadClient.LOGGER.info("[better-trackpad] fireTap fingers={} x={} -> action={}", fingers, String.format("%.3f", x), action);

        if (action == TrackpadAction.LEFT_CLICK) {
            // macOS tap-to-click already fires a left click — let the GLFW intercept pass it through
            pendingTapAction = TrackpadAction.LEFT_CLICK;
        } else if (action == TrackpadAction.NONE) {
            // Suppress whatever system click comes next
            pendingTapAction = TrackpadAction.NONE;
        } else {
            // RIGHT_CLICK or MIDDLE_CLICK — intercept and redirect the system click
            pendingTapAction = action;
        }
    }

    public static synchronized boolean install(long glfwWindow) {
        if (installed) return true;

        long nsWindowPtr = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
        BetterTrackpadClient.LOGGER.info("[better-trackpad] nsWindowPtr={}", nsWindowPtr);
        if (nsWindowPtr == 0L) return false;

        Pointer nsWindow    = new Pointer(nsWindowPtr);
        Pointer contentView = msgPtr(nsWindow, "contentView");
        if (contentView == null) return false;

        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setAcceptsTouchEvents:"),  (byte) 1 });
        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setAllowedTouchTypes:"),   NS_TOUCH_TYPE_MASK_INDIRECT });
        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setWantsRestingTouches:"), (byte) 0 });
        MSG_SEND.invokeVoid(new Object[]{ nsWindow,    sel("makeFirstResponder:"),     contentView });

        // Intercept GLFW mouse button events to redirect tap-to-click into the correct action
        prevGlfwCallback = GLFW.glfwSetMouseButtonCallback(glfwWindow, (win, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                TrackpadAction pending = pendingTapAction;

                // 1-finger right-zone tap: system fires LEFT click, redirect to RIGHT click
                if (pending == TrackpadAction.RIGHT_CLICK && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    pendingTapAction = null;
                    BetterTrackpadClient.LOGGER.info("[better-trackpad] intercept: LEFT -> RIGHT");
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) mc.execute(() -> KeyMapping.click(resolveKey(mc.options.keyUse)));
                    return; // suppress system left click
                }

                // 2-finger tap: system fires RIGHT click (macOS 2-finger = right click), redirect to MIDDLE
                if (pending == TrackpadAction.MIDDLE_CLICK && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    pendingTapAction = null;
                    BetterTrackpadClient.LOGGER.info("[better-trackpad] intercept: RIGHT -> MIDDLE");
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) mc.execute(() -> KeyMapping.click(resolveKey(mc.options.keyPickItem)));
                    return; // suppress system right click
                }

                // NONE action: suppress whatever comes
                if (pending == TrackpadAction.NONE &&
                    (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                    pendingTapAction = null;
                    BetterTrackpadClient.LOGGER.info("[better-trackpad] intercept: suppressed button={}", button);
                    return;
                }

                // LEFT_CLICK or unmatched: clear pending and pass through normally
                if (pending != null) {
                    pendingTapAction = null;
                    BetterTrackpadClient.LOGGER.info("[better-trackpad] intercept: pass-through button={} pending={}", button, pending);
                }
            }
            if (prevGlfwCallback != null) prevGlfwCallback.invoke(win, button, action, mods);
        });

        BetterTrackpadClient.LOGGER.info("[better-trackpad] GLFW callback hooked, prev={}", prevGlfwCallback);

        beganHandler     = (self, cmd, event) -> processTouches(event, NS_TOUCH_PHASE_BEGAN,     false, false);
        movedHandler     = (self, cmd, event) -> {};
        endedHandler     = (self, cmd, event) -> processTouches(event, NS_TOUCH_PHASE_ENDED,     true,  false);
        cancelledHandler = (self, cmd, event) -> processTouches(event, NS_TOUCH_PHASE_CANCELLED, false, true);

        Pointer viewClass = OBJECT_GET_CLASS.invokePointer(new Object[]{ contentView });
        replaceMethod(viewClass, "touchesBeganWithEvent:",     beganHandler);
        replaceMethod(viewClass, "touchesMovedWithEvent:",     movedHandler);
        replaceMethod(viewClass, "touchesEndedWithEvent:",     endedHandler);
        replaceMethod(viewClass, "touchesCancelledWithEvent:", cancelledHandler);

        installed = true;
        BetterTrackpadClient.LOGGER.info("[better-trackpad] install complete");
        return true;
    }
}
