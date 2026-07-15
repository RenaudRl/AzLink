package com.azuriom.azlink.common.platform;

public enum PlatformType {

    BUKKIT("Bukkit"),
    BUNGEE("BungeeCord"),
    SPONGE("Sponge"),
    VELOCITY("Velocity"),
    NUKKIT("Nukkit"),
    HYTALE("Hytale"),
    BTC_CORE("BTC-CORE"),
    BTC_VELOCITY("BTCVelocity");

    private final String name;

    PlatformType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
