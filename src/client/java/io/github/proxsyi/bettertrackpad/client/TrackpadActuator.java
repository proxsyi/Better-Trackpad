package io.github.proxsyi.bettertrackpad.client;

public interface TrackpadActuator {
    boolean isReady();

    void click(TrackpadAction action);

    void beginHold(TrackpadAction action);

    void releaseHold();

    void tick();
}
