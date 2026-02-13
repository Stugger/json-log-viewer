package com.stugger.logviewer.model;

/**
 * Preset date range options for loading log files.
 * <p>
 * Used by the UI to expand a selected day to include previous days while keeping
 * the query logic simple and predictable.
 *
 * @author Jake
 * @since January 27, 2026
 */
public enum DayRange {

    ZERO_DAYS("N/A", 0),
    ONE_DAY("1 DAY", 1),
    TWO_DAYS("2 DAYS", 2),
    FIVE_DAYS("5 DAYS", 5),
    SEVEN_DAYS("7 DAYS", 7),
    TEN_DAYS("10 DAYS", 10),
    ;

    private final String label;
    public final int days;

    DayRange(String label, int days) {
        this.label = label;
        this.days = days;
    }

    @Override
    public String toString() {
        return label;
    }
}
