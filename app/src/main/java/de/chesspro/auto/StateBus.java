package de.chesspro.auto;

public final class StateBus {
    private StateBus() {}
    public static volatile String status = "Bereit. Erlaubnis erteilen und Training starten.";
    public static volatile String detail = "";
    public static volatile boolean running = false;
    // 0=automatic (player owns bottom pieces), +1=white, -1=black.
    public static volatile int playerColor = 0;
    // 0=infer from last move or tracking, +1=white, -1=black.
    public static volatile int turnOverride = 0;
}
