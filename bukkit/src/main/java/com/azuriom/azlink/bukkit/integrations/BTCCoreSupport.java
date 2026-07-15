package com.azuriom.azlink.bukkit.integrations;

import com.azuriom.azlink.common.logger.LoggerAdapter;
import dev.btc.core.api.BTCCoreAPI;
import org.bukkit.entity.Player;

/**
 * Native support for the BTC-CORE server fork (based on AdvancedSlimePaper 26.2).
 *
 * <p>BTC-CORE exposes {@link BTCCoreAPI} through the Adventure services SPI, which
 * provides server metrics and player states that are otherwise unavailable, or only
 * available through more expensive Bukkit calls.</p>
 *
 * <p>This class is only ever loaded once BTC-CORE has been detected, so that servers
 * running vanilla Paper, Folia or Spigot never resolve the {@code dev.btc.core.api}
 * classes.</p>
 */
public final class BTCCoreSupport {

    private static final double MAX_TPS = 20.0;
    private static final double MILLIS_PER_SECOND = 1000.0;

    /**
     * The MSPT above which the server is considered overloaded, matching the threshold used
     * by the BTC forks: a healthy server ticks in 50ms or less, and BTCVelocity stops
     * sending players to a backend above 60ms.
     */
    private static final double OVERLOADED_MSPT = 60.0;

    private final BTCCoreAPI api;

    private BTCCoreSupport(BTCCoreAPI api) {
        this.api = api;
    }

    /**
     * Detects BTC-CORE and creates the support instance.
     *
     * @param logger the logger used to report the detection result
     * @return the support instance, or {@code null} when not running on BTC-CORE
     */
    public static BTCCoreSupport detect(LoggerAdapter logger) {
        try {
            Class.forName("dev.btc.core.api.BTCCoreAPI");
        } catch (ClassNotFoundException e) {
            return null; // Not running on BTC-CORE, use the standard Bukkit APIs
        }

        try {
            BTCCoreSupport support = new BTCCoreSupport(BTCCoreAPI.instance());

            logger.info("BTC-CORE support enabled successfully.");

            return support;
        } catch (IllegalStateException | LinkageError e) {
            logger.warn("The BTC-CORE API is present but no implementation was found, "
                    + "falling back to the standard Bukkit APIs.");

            return null;
        }
    }

    /**
     * Gets the server TPS, derived from the MSPT tracked natively by BTC-CORE.
     *
     * <p>This avoids running a plugin-side task on every tick just to measure the TPS.</p>
     *
     * @return the current TPS, capped at 20
     */
    public double getTps() {
        double mspt = getCurrentMspt();

        if (mspt <= 0) {
            return MAX_TPS;
        }

        return Math.min(MAX_TPS, MILLIS_PER_SECOND / mspt);
    }

    /**
     * Gets the average milliseconds per tick over the last 5 seconds.
     *
     * @return the current MSPT
     */
    private double getCurrentMspt() {
        return this.api.getCurrentMspt();
    }

    /**
     * Checks whether the server is currently overloaded, so that callers can skip
     * non-essential work instead of adding to the pressure.
     *
     * @return {@code true} when the server is under heavy load
     */
    public boolean isOverloaded() {
        return getCurrentMspt() > OVERLOADED_MSPT;
    }

    /**
     * Checks whether a player is vanished, using the native vanish state instead of
     * the {@code vanished} metadata convention.
     *
     * @param player the player to check
     * @return {@code true} when the player is vanished
     */
    public boolean isVanished(Player player) {
        return this.api.isVanished(player);
    }
}
