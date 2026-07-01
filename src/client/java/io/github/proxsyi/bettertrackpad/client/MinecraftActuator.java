package io.github.proxsyi.bettertrackpad.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public final class MinecraftActuator implements TrackpadActuator {
    private static final long MAX_HOLD_TICKS = 200L;

    private long tickCounter = 0L;
    private InputConstants.Key heldKey = null;
    private long heldReleaseTick = 0L;
    private Field keyField = null;

    @Override
    public boolean isReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.screen == null && mc.mouseHandler.isMouseGrabbed();
    }

    @Override
    public void click(TrackpadAction action) {
        InputConstants.Key key = keyFor(action);
        if (key == null) return;
        if (heldKey != null && heldKey != key) {
            KeyMapping.set(heldKey, false);
            heldKey = null;
        }
        KeyMapping.click(key);
    }

    @Override
    public void beginHold(TrackpadAction action) {
        InputConstants.Key key = keyFor(action);
        if (key == null) return;
        if (heldKey != null && heldKey != key) {
            KeyMapping.set(heldKey, false);
        }
        KeyMapping.set(key, true);
        heldKey = key;
        heldReleaseTick = tickCounter + MAX_HOLD_TICKS;
    }

    @Override
    public void releaseHold() {
        if (heldKey == null) return;
        KeyMapping.set(heldKey, false);
        heldKey = null;
    }

    @Override
    public void tick() {
        tickCounter++;
        if (heldKey != null && (tickCounter >= heldReleaseTick || !isReady())) {
            KeyMapping.set(heldKey, false);
            heldKey = null;
        }
    }

    private InputConstants.Key keyFor(TrackpadAction action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        KeyMapping mapping = switch (action) {
            case LEFT_CLICK -> mc.options.keyAttack;
            case RIGHT_CLICK -> mc.options.keyUse;
            case MIDDLE_CLICK -> mc.options.keyPickItem;
            case NONE -> null;
        };
        if (mapping == null) return null;
        InputConstants.Key key = resolveKey(mapping);
        return (key == null || key == InputConstants.UNKNOWN) ? null : key;
    }

    private InputConstants.Key resolveKey(KeyMapping mapping) {
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
