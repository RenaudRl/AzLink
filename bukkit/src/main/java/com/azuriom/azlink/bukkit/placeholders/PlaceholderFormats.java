package com.azuriom.azlink.bukkit.placeholders;

import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatting settings shared by the placeholder providers.
 *
 * <p>These used to come from PlaceholderAPI itself — {@code PlaceholderAPIPlugin.booleanTrue()},
 * {@code booleanFalse()} and {@code getDateFormat()}. MiniPlaceholders has no such global
 * settings, and borrowing them from another plugin was never a good idea anyway, so they are now
 * AzLink's own configuration.</p>
 */
public final class PlaceholderFormats {

    private PlaceholderFormats() {}

    static final DecimalFormat AMOUNT = createDecimalFormat();

    private static final String DEFAULT_DATE_FORMAT = "MM/dd/yy HH:mm:ss";
    private static final String DEFAULT_TRUE = "yes";
    private static final String DEFAULT_FALSE = "no";

    private static DateTimeFormatter dateTime = createDateTimeFormatter(DEFAULT_DATE_FORMAT);
    private static String booleanTrue = DEFAULT_TRUE;
    private static String booleanFalse = DEFAULT_FALSE;

    /** Reloads the formats from the {@code placeholders} section of the config. */
    public static void configure(ConfigurationSection config) {
        if (config == null) {
            return;
        }

        dateTime = createDateTimeFormatter(config.getString("date-format", DEFAULT_DATE_FORMAT));
        booleanTrue = config.getString("boolean-true", DEFAULT_TRUE);
        booleanFalse = config.getString("boolean-false", DEFAULT_FALSE);
    }

    public static DateTimeFormatter dateTime() {
        return dateTime;
    }

    public static String booleanValue(boolean value) {
        return value ? booleanTrue : booleanFalse;
    }

    private static DecimalFormat createDecimalFormat() {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        return new DecimalFormat("0.##", symbols);
    }

    private static DateTimeFormatter createDateTimeFormatter(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT).withZone(ZoneId.systemDefault());
        }
    }
}
