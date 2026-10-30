package com.saucelabs.teamcity.results;

/**
 * Raised when the Sauce Labs Results tab cannot show any jobs, carrying the message to display on the tab.
 *
 * A notice is an expected outcome rather than a failure (e.g. no Sauce build was reported for this TeamCity
 * build yet), and is shown less prominently than an error.
 */
public class SauceResultsException extends Exception {

    private final boolean notice;

    private SauceResultsException(String message, boolean notice) {
        super(message);
        this.notice = notice;
    }

    public static SauceResultsException error(String message) {
        return new SauceResultsException(message, false);
    }

    public static SauceResultsException notice(String message) {
        return new SauceResultsException(message, true);
    }

    public boolean isNotice() {
        return notice;
    }
}
