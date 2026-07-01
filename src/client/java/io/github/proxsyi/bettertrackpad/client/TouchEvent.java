package io.github.proxsyi.bettertrackpad.client;

public final class TouchEvent {
    public enum Phase { BEGAN, MOVED, ENDED, CANCELLED }
    public enum Pressure { TOUCH, LIGHT, NORMAL, HARD }

    public final long touchId;
    public final double x;
    public final double y;
    public final Phase phase;
    public final Pressure pressure;

    public TouchEvent(long touchId, double x, double y, Phase phase, Pressure pressure) {
        this.touchId = touchId;
        this.x = x;
        this.y = y;
        this.phase = phase;
        this.pressure = pressure;
    }
}
