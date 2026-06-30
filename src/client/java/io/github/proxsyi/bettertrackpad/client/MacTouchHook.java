package io.github.proxsyi.bettertrackpad.client;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeCocoa;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MacTouchHook {
    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private static final Function MSG_SEND          = OBJC.getFunction("objc_msgSend");
    private static final Function SEL_REGISTER_NAME = OBJC.getFunction("sel_registerName");
    private static final Function CLASS_REPLACE_METHOD = OBJC.getFunction("class_replaceMethod");
    private static final Function OBJECT_GET_CLASS  = OBJC.getFunction("object_getClass");

    private static final long NS_TOUCH_TYPE_MASK_INDIRECT = 1L << 1;
    private static final long NS_TOUCH_PHASE_BEGAN     = 0x1;
    private static final long NS_TOUCH_PHASE_ENDED     = 0x8;
    private static final long NS_TOUCH_PHASE_CANCELLED = 0x10;
    private static final double TAP_MAX_DELTA = 0.08;

    private static final Map<Long, double[]> activeTouches = new ConcurrentHashMap<>();

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

    private static void replaceMethod(Pointer viewClass, String selName, TouchHandler impl) {
        CLASS_REPLACE_METHOD.invokePointer(new Object[]{ viewClass, sel(selName), impl, "v@:@" });
    }

    private static void processTouches(Pointer event, long phase, boolean ended, boolean cancelled) {
        if (cancelled) {
            activeTouches.clear();
            return;
        }

        Pointer touchSet = msgPtr(event, "touchesMatchingPhase:inView:", phase, (Pointer) null);
        if (touchSet == null) return;
        long count = msgLong(touchSet, "count");
        if (count == 0) return;

        Pointer enumerator = msgPtr(touchSet, "objectEnumerator");
        if (enumerator == null) return;

        int tapCount = 0;
        double sumX  = 0;
        int fingerCount = (int) count;

        Pointer touch;
        while ((touch = msgPtr(enumerator, "nextObject")) != null) {
            long key = Pointer.nativeValue(touch);
            CGPoint.ByValue pos = touchPos(touch);
            if (pos == null) continue;

            if (!ended) {
                activeTouches.put(key, new double[]{ pos.x, pos.y });
            } else {
                double[] start = activeTouches.remove(key);
                if (start == null) continue;
                double dx = Math.abs(pos.x - start[0]);
                double dy = Math.abs(pos.y - start[1]);
                if (dx < TAP_MAX_DELTA && dy < TAP_MAX_DELTA) {
                    tapCount++;
                    sumX += pos.x;
                }
            }
        }

        if (ended && tapCount == fingerCount && fingerCount > 0) {
            fireTap(sumX / tapCount, fingerCount);
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
            return;
        }

        if (action == TrackpadAction.NONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        TrackpadAction finalAction = action;
        mc.execute(() -> {
            switch (finalAction) {
                case LEFT_CLICK   -> KeyMapping.click(resolveKey(mc.options.keyAttack));
                case RIGHT_CLICK  -> KeyMapping.click(resolveKey(mc.options.keyUse));
                case MIDDLE_CLICK -> KeyMapping.click(resolveKey(mc.options.keyPickItem));
                default -> {}
            }
        });
    }

    public static synchronized boolean install(long glfwWindow) {
        if (installed) return true;

        long nsWindowPtr = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
        if (nsWindowPtr == 0L) return false;

        Pointer nsWindow   = new Pointer(nsWindowPtr);
        Pointer contentView = msgPtr(nsWindow, "contentView");
        if (contentView == null) return false;

        // Enable indirect (trackpad) touch events on the view
        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setAcceptsTouchEvents:"),   (byte) 1 });
        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setAllowedTouchTypes:"),    NS_TOUCH_TYPE_MASK_INDIRECT });
        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setWantsRestingTouches:"),  (byte) 0 });
        // Make content view first responder so it receives touch events
        MSG_SEND.invokeVoid(new Object[]{ nsWindow,    sel("makeFirstResponder:"),      contentView });

        beganHandler     = (self, cmd, event) -> processTouches(event, NS_TOUCH_PHASE_BEGAN,     false, false);
        movedHandler     = (self, cmd, event) -> {};
        endedHandler     = (self, cmd, event) -> processTouches(event, NS_TOUCH_PHASE_ENDED,     true,  false);
        cancelledHandler = (self, cmd, event) -> processTouches(event, NS_TOUCH_PHASE_CANCELLED, false, true);

        // Use class_replaceMethod — works even if the view class already has these methods
        Pointer viewClass = OBJECT_GET_CLASS.invokePointer(new Object[]{ contentView });
        replaceMethod(viewClass, "touchesBeganWithEvent:",     beganHandler);
        replaceMethod(viewClass, "touchesMovedWithEvent:",     movedHandler);
        replaceMethod(viewClass, "touchesEndedWithEvent:",     endedHandler);
        replaceMethod(viewClass, "touchesCancelledWithEvent:", cancelledHandler);

        installed = true;
        return true;
    }
}
