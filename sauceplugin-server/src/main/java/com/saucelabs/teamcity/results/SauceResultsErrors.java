package com.saucelabs.teamcity.results;

import com.saucelabs.saucerest.SauceException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates failures from the Sauce REST API into short messages for the Sauce Labs Results tab. The details of
 * each failure only go to the server log.
 *
 * Only the responses the Sauce API documents for the endpoints the tab calls get their own message
 * (https://docs.saucelabs.com/dev/api/): 401, 403, 404, 429 and 5xx. saucerest reports them as
 * {@link SauceException.NotAuthorized} for 401, {@link SauceException.NotFound} for 404, and a plain
 * {@link RuntimeException} embedding the status for the rest (429 and 5xx only after its own retries).
 * Network failures surface as {@link IOException}s. Anything else falls back to a generic message, so the tab
 * never crashes.
 */
public final class SauceResultsErrors {

    static final String CREDENTIALS_REJECTED = "Sauce Labs didn't accept the username and access key. Check them in the Sauce Labs build feature.";
    static final String NO_ACCESS = "Your Sauce Labs account doesn't have access to these results.";
    static final String NOT_FOUND = "These Sauce Labs results are no longer available.";
    static final String BUSY = "Sauce Labs is busy right now. Try again in a few minutes.";
    static final String UNAVAILABLE = "Sauce Labs is temporarily unavailable. Try again in a few minutes.";
    static final String UNREACHABLE = "Couldn't connect to Sauce Labs. Try again later.";
    static final String UNEXPECTED = "Something went wrong while loading Sauce Labs results.";

    // saucerest: throw new RuntimeException("Unexpected code " + response), e.g. "Response{..., code=503, ...}"
    private static final Pattern HTTP_CODE = Pattern.compile("Unexpected code .*\\bcode=(\\d{3})\\b");

    private SauceResultsErrors() {
    }

    /**
     * @param e the failure raised while talking to Sauce Labs
     * @return a message that is safe to show on the Results tab
     */
    public static String toMessage(Throwable e) {
        if (e instanceof SauceException.NotAuthorized) {
            return CREDENTIALS_REJECTED;
        }
        if (e instanceof SauceException.NotFound) {
            return NOT_FOUND;
        }

        Integer httpCode = httpCode(e);
        if (httpCode != null) {
            if (httpCode == 403) {
                return NO_ACCESS;
            }
            if (httpCode == 429) {
                return BUSY;
            }
            if (httpCode >= 500) {
                return UNAVAILABLE;
            }
            return UNEXPECTED;
        }

        // DNS failures, refused connections, timeouts and other network problems
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return UNREACHABLE;
            }
        }
        return UNEXPECTED;
    }

    static Integer httpCode(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                Matcher matcher = HTTP_CODE.matcher(cause.getMessage());
                if (matcher.find()) {
                    return Integer.valueOf(matcher.group(1));
                }
            }
        }
        return null;
    }
}
