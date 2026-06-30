package io.github.proxsyi.bettertrackpad.client;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import org.lwjgl.glfw.GLFWNativeCocoa;

public final class MacTouchHook {
    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private static final Function MSG_SEND = OBJC.getFunction("objc_msgSend");
    private static final Function SEL_REGISTER_NAME = OBJC.getFunction("sel_registerName");
    private static final Function CLASS_ADD_METHOD = OBJC.getFunction("class_addMethod");
    private static final Function OBJECT_GET_CLASS = OBJC.getFunction("object_getClass");

    private static final long NS_TOUCH_TYPE_MASK_INDIRECT = 1L << 1;
    private static final long NS_TOUCH_PHASE_TOUCHING = 0x1 | 0x2 | 0x4;

    private static boolean installed = false;
    private static TouchHandler handler;

    public interface TouchHandler extends Callback {
        void invoke(Pointer self, Pointer cmd, Pointer event);
    }

    private MacTouchHook() {}

    private static Pointer sel(String name) {
        return SEL_REGISTER_NAME.invokePointer(new Object[]{ name });
    }

    private static Object[] msg(Pointer receiver, Pointer selector, Object... extra) {
        Object[] args = new Object[2 + extra.length];
        args[0] = receiver;
        args[1] = selector;
        System.arraycopy(extra, 0, args, 2, extra.length);
        return args;
    }

    public static synchronized boolean install(long glfwWindow) {
        if (installed) return true;

        long nsWindowPtr = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
        if (nsWindowPtr == 0L) return false;

        Pointer nsWindow = new Pointer(nsWindowPtr);
        Pointer contentView = MSG_SEND.invokePointer(msg(nsWindow, sel("contentView")));
        if (contentView == null) return false;

        MSG_SEND.invokeVoid(msg(contentView, sel("setAllowedTouchTypes:"), NS_TOUCH_TYPE_MASK_INDIRECT));
        MSG_SEND.invokeVoid(msg(contentView, sel("setWantsRestingTouches:"), (byte) 1));

        handler = (self, cmd, event) -> {
            Pointer touches = MSG_SEND.invokePointer(
                msg(event, sel("touchesMatchingPhase:inView:"), NS_TOUCH_PHASE_TOUCHING, self));
            long count = (touches == null) ? 0 : MSG_SEND.invokeLong(msg(touches, sel("count")));
            BetterTrackpadClient.LOGGER.info("[touch] fingers={}", count);
        };

        Pointer viewClass = OBJECT_GET_CLASS.invokePointer(new Object[]{ contentView });
        String types = "v@:@";
        CLASS_ADD_METHOD.invokeInt(new Object[]{ viewClass, sel("touchesBeganWithEvent:"), handler, types });
        CLASS_ADD_METHOD.invokeInt(new Object[]{ viewClass, sel("touchesMovedWithEvent:"), handler, types });
        CLASS_ADD_METHOD.invokeInt(new Object[]{ viewClass, sel("touchesEndedWithEvent:"), handler, types });
        CLASS_ADD_METHOD.invokeInt(new Object[]{ viewClass, sel("touchesCancelledWithEvent:"), handler, types });

        installed = true;
        return true;
    }
}
