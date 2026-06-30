package io.github.proxsyi.bettertrackpad.client;

public enum TrackpadAction {
    LEFT_CLICK,
    RIGHT_CLICK,
    MIDDLE_CLICK,
    NONE;

    @Override
    public String toString() {
        return switch (this) {
            case LEFT_CLICK -> "Left Click";
            case RIGHT_CLICK -> "Right Click";
            case MIDDLE_CLICK -> "Middle Click";
            case NONE -> "None";
        };
    }
}
