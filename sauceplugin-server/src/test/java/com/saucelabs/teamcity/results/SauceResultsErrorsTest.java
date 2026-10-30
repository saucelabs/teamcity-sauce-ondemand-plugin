package com.saucelabs.teamcity.results;

import com.google.gson.JsonSyntaxException;
import com.saucelabs.saucerest.SauceException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.saucelabs.teamcity.results.SauceResultsErrors.*;

public class SauceResultsErrorsTest {

    private static String message(Throwable e) {
        return SauceResultsErrors.toMessage(e);
    }

    // How saucerest reports non-2xx responses it has no dedicated exception for
    private static RuntimeException unexpectedCode(int code) {
        return new RuntimeException("Unexpected code Response{protocol=h2, code=" + code
                + ", message=, url=https://api.eu-central-1.saucelabs.com/v2/builds/vdc/}");
    }

    // How saucerest wraps network failures (AbstractEndpoint.makeRequest)
    private static IOException networkFailure(Throwable cause) {
        return new IOException("Error executing request: " + cause.getMessage(), cause);
    }

    @Test
    public void testCredentialsRejected() {
        Assert.assertEquals(message(new SauceException.NotAuthorized("Not Authorized. Access Key ending with 'abc'")), CREDENTIALS_REJECTED);
    }

    @Test
    public void testDocumentedStatuses() {
        Assert.assertEquals(message(unexpectedCode(403)), NO_ACCESS);
        Assert.assertEquals(message(new SauceException.NotFound()), NOT_FOUND);
        Assert.assertEquals(message(unexpectedCode(429)), BUSY);
        for (int code : new int[]{500, 502, 503, 504}) {
            Assert.assertEquals(message(unexpectedCode(code)), UNAVAILABLE);
        }
    }

    @Test
    public void testUndocumentedStatusesGetGenericMessage() {
        for (int code : new int[]{400, 407, 422}) {
            Assert.assertEquals(message(unexpectedCode(code)), UNEXPECTED);
        }
    }

    @Test
    public void testNetworkFailures() {
        Assert.assertEquals(message(networkFailure(new UnknownHostException("api.eu-central-1.saucelabs.com"))), UNREACHABLE);
        Assert.assertEquals(message(networkFailure(new ConnectException("Connection refused"))), UNREACHABLE);
        Assert.assertEquals(message(networkFailure(new SocketTimeoutException("timeout"))), UNREACHABLE);
    }

    @Test
    public void testUnknownErrorsGetGenericMessage() {
        Assert.assertEquals(message(new NullPointerException()), UNEXPECTED);
        Assert.assertEquals(message(new JsonSyntaxException("Expected BEGIN_OBJECT")), UNEXPECTED);
    }

}
