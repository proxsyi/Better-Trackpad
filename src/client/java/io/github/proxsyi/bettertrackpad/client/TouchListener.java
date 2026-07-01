package io.github.proxsyi.bettertrackpad.client;

public interface TouchListener {
    void onTouch(TouchEvent event);

    boolean onButton(boolean press);
}
