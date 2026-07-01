package io.github.proxsyi.bettertrackpad.client;

public final class BetterTrackpadConfig {
    public static boolean enabled = true;
    public static boolean debug = false;
    public static float leftZoneMax = 0.45f;
    public static float rightZoneMin = 0.55f;

    public static TrackpadAction leftOneFinger = TrackpadAction.LEFT_CLICK;
    public static TrackpadAction rightOneFinger = TrackpadAction.RIGHT_CLICK;
    public static TrackpadAction twoFinger = TrackpadAction.MIDDLE_CLICK;

    private BetterTrackpadConfig() {}
}
