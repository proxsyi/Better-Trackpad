package io.github.proxsyi.bettertrackpad.client;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWNativeCocoa;

import java.util.List;

public final class MacTouchHook implements PlatformTouchHook {
    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private static final Function MSG_SEND             = OBJC.getFunction("objc_msgSend");
    private static final Function SEL_REGISTER_NAME    = OBJC.getFunction("sel_registerName");
    private static final Function CLASS_REPLACE_METHOD = OBJC.getFunction("class_replaceMethod");
    private static final Function OBJECT_GET_CLASS     = OBJC.getFunction("object_getClass");

    private static final long NS_TOUCH_TYPE_MASK_INDIRECT = 1L << 1;
    private static final long NS_TOUCH_PHASE_BEGAN        = 0x1;
    private static final long NS_TOUCH_PHASE_MOVED        = 0x2;
    private static final long NS_TOUCH_PHASE_ENDED        = 0x8;
    private static final long NS_TOUCH_PHASE_CANCELLED    = 0x10;
    private static final int MAX_TOUCHES_PER_EVENT = 32;

    private final TouchListener listener;

    private volatile boolean disabled = false;
    private boolean installed = false;

    private GLFWMouseButtonCallbackI prevGlfwCallback = null;

    private TouchHandler beganHandler;
    private TouchHandler movedHandler;
    private TouchHandler endedHandler;
    private TouchHandler cancelledHandler;

    public MacTouchHook(TouchListener listener) {
        this.listener = listener;
    }

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

    public boolean isDisabled() {
        return disabled;
    }

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

    private static long touchKey(Pointer touch) {
        Pointer identity = msgPtr(touch, "identity");
        return identity != null ? Pointer.nativeValue(identity) : Pointer.nativeValue(touch);
    }

    private static void replaceMethod(Pointer viewClass, String selName, TouchHandler impl) {
        CLASS_REPLACE_METHOD.invokePointer(new Object[]{ viewClass, sel(selName), impl, "v@:@" });
    }

    private void safeEmit(Pointer event, long nsPhase, TouchEvent.Phase phase) {
        if (disabled) return;
        try {
            emitTouches(event, nsPhase, phase);
        } catch (Throwable t) {
            disabled = true;
            BetterTrackpadClient.LOGGER.error("[better-trackpad] touch handler error, disabling hook", t);
        }
    }

    private void emitTouches(Pointer event, long nsPhase, TouchEvent.Phase phase) {
        Pointer touchSet = msgPtr(event, "touchesMatchingPhase:inView:", nsPhase, (Pointer) null);
        if (touchSet == null) return;
        long count = msgLong(touchSet, "count");
        if (count == 0) return;

        Pointer enumerator = msgPtr(touchSet, "objectEnumerator");
        if (enumerator == null) return;

        int guard = 0;
        Pointer touch;
        while ((touch = msgPtr(enumerator, "nextObject")) != null) {
            if (++guard > MAX_TOUCHES_PER_EVENT) {
                disabled = true;
                BetterTrackpadClient.LOGGER.error("[better-trackpad] enumerator guard tripped, disabling hook");
                return;
            }
            long key = touchKey(touch);
            CGPoint.ByValue pos = touchPos(touch);
            if (pos == null) continue;
            listener.onTouch(new TouchEvent(key, pos.x, pos.y, phase, TouchEvent.Pressure.NORMAL));
        }
    }

    @Override
    public synchronized boolean install(long glfwWindow) {
        if (installed) return true;

        long nsWindowPtr = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
        BetterTrackpadClient.LOGGER.info("[better-trackpad] nsWindowPtr={}", nsWindowPtr);
        if (nsWindowPtr == 0L) return false;

        Pointer nsWindow    = new Pointer(nsWindowPtr);
        Pointer contentView = msgPtr(nsWindow, "contentView");
        if (contentView == null) return false;

        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setAcceptsTouchEvents:"),  (byte) 1 });
        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setAllowedTouchTypes:"),   NS_TOUCH_TYPE_MASK_INDIRECT });
        MSG_SEND.invokeVoid(new Object[]{ contentView, sel("setWantsRestingTouches:"), (byte) 1 });
        MSG_SEND.invokeVoid(new Object[]{ nsWindow,    sel("makeFirstResponder:"),     contentView });

        prevGlfwCallback = GLFW.glfwSetMouseButtonCallback(glfwWindow, (win, button, action, mods) -> {
            try {
                boolean trackpadButton = button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                if (!disabled && trackpadButton && listener.onButton(action == GLFW.GLFW_PRESS)) {
                    return;
                }
            } catch (Throwable t) {
                disabled = true;
                BetterTrackpadClient.LOGGER.error("[better-trackpad] mouse callback error, disabling hook", t);
            }
            if (prevGlfwCallback != null) prevGlfwCallback.invoke(win, button, action, mods);
        });

        beganHandler     = (self, cmd, event) -> safeEmit(event, NS_TOUCH_PHASE_BEGAN,     TouchEvent.Phase.BEGAN);
        movedHandler     = (self, cmd, event) -> safeEmit(event, NS_TOUCH_PHASE_MOVED,     TouchEvent.Phase.MOVED);
        endedHandler     = (self, cmd, event) -> safeEmit(event, NS_TOUCH_PHASE_ENDED,     TouchEvent.Phase.ENDED);
        cancelledHandler = (self, cmd, event) -> safeEmit(event, NS_TOUCH_PHASE_CANCELLED, TouchEvent.Phase.CANCELLED);

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
