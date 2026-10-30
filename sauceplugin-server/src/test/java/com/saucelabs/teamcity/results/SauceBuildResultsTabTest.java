package com.saucelabs.teamcity.results;

import com.saucelabs.ci.JobInformation;
import com.saucelabs.teamcity.Constants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills the Sauce Labs Results tab's model for a build, with a local HTTP server standing in for the Sauce API
 * (answering with the statuses the real API returns or documents), and checks what the tab would show.
 */
public class SauceBuildResultsTabTest {

    private static final String API_URL_PROPERTY = "saucerest-java.base_api_url";

    private static final String BUILDS = "{\"builds\":[{\"id\":\"build-1\"}]}";
    private static final String JOBS_IN_BUILD = "{\"jobs\":[{\"id\":\"job-1\"}]}";
    private static final String JOB = "{\"id\":\"job-1\",\"name\":\"login test\",\"passed\":true}";

    private HttpServer server;
    private Map<String, String> featureParameters;

    // What the fake Sauce API answers for each of the three calls the tab makes
    private int buildsStatus;
    private String buildsBody;
    private int jobsInBuildStatus;
    private String jobsInBuildBody;
    private int jobStatus;
    private String jobBody;

    @BeforeMethod
    public void beforeEach() throws IOException {
        featureParameters = new HashMap<String, String>();
        featureParameters.put(Constants.SAUCE_USER_ID_KEY, "sauce-user");
        featureParameters.put(Constants.SAUCE_PLUGIN_ACCESS_KEY, "fake-access-key");
        featureParameters.put(Constants.SAUCE_PLUGIN_DATA_CENTER, "US");

        buildsStatus = 200;
        buildsBody = BUILDS;
        jobsInBuildStatus = 200;
        jobsInBuildBody = JOBS_IN_BUILD;
        jobStatus = 200;
        jobBody = JOB;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        System.setProperty(API_URL_PROPERTY, "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterMethod(alwaysRun = true)
    public void afterEach() {
        server.stop(0);
        System.clearProperty(API_URL_PROPERTY);
    }

    @Test
    public void testShowsJobsWhenSauceBuildIsFound() {
        Map<String, Object> model = fillModel();
        List<?> jobs = (List<?>) model.get("jobs");
        Assert.assertEquals(jobs.size(), 1);
        Assert.assertEquals(((JobInformation) jobs.get(0)).getJobId(), "job-1");
        Assert.assertNull(model.get("sauceError"));
        Assert.assertNull(model.get("sauceNotice"));
    }

    @Test
    public void testShowsErrorWhenUsernameIsEmpty() {
        featureParameters.put(Constants.SAUCE_USER_ID_KEY, "");
        assertError(fillModel(), "Add your Sauce Labs username and access key in the Sauce Labs build feature.");
    }

    @Test
    public void testShowsErrorWhenAccessKeyIsEmpty() {
        featureParameters.put(Constants.SAUCE_PLUGIN_ACCESS_KEY, "");
        assertError(fillModel(), "Add your Sauce Labs username and access key in the Sauce Labs build feature.");
    }

    @Test
    public void testShowsNoticeWhenNoSauceBuildIsFound() {
        buildsBody = "{\"builds\":[]}";
        assertNotice(fillModel(), SauceBuildResultsTab.NO_TESTS);
    }

    @Test
    public void testShowsNoticeWhenSauceBuildHasNoJobs() {
        jobsInBuildBody = "{\"jobs\":[]}";
        assertNotice(fillModel(), SauceBuildResultsTab.NO_TESTS);
    }

    @Test
    public void testShowsErrorWhenCredentialsAreRejected() {
        buildsStatus = 401;
        assertError(fillModel(), SauceResultsErrors.CREDENTIALS_REJECTED);
    }

    @Test
    public void testShowsErrorWhenAccessIsDenied() {
        jobsInBuildStatus = 403;
        assertError(fillModel(), SauceResultsErrors.NO_ACCESS);
    }

    @Test
    public void testShowsErrorWhenSauceBuildIsNotFound() {
        jobsInBuildStatus = 404;
        assertError(fillModel(), SauceResultsErrors.NOT_FOUND);
    }

    @Test
    public void testShowsErrorWhenJobIsNotFound() {
        jobStatus = 404;
        assertError(fillModel(), SauceResultsErrors.NOT_FOUND);
    }

    @Test
    public void testShowsErrorWhenRateLimited() {
        buildsStatus = 429;
        assertError(fillModel(), SauceResultsErrors.BUSY);
    }

    @Test
    public void testShowsErrorWhenSauceIsUnavailable() {
        buildsStatus = 500;
        assertError(fillModel(), SauceResultsErrors.UNAVAILABLE);
    }

    @Test
    public void testShowsErrorWhenConnectionIsRefused() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            System.setProperty(API_URL_PROPERTY, "http://127.0.0.1:" + socket.getLocalPort() + "/");
        }
        assertError(fillModel(), SauceResultsErrors.UNREACHABLE);
    }

    @Test
    public void testShowsErrorWhenHostIsUnknown() {
        System.setProperty(API_URL_PROPERTY, "http://sauce-api.invalid/");
        assertError(fillModel(), SauceResultsErrors.UNREACHABLE);
    }

    @Test
    public void testShowsGenericErrorOnUnexpectedFailure() {
        featureParameters.remove(Constants.SAUCE_USER_ID_KEY);
        assertError(fillModel(), SauceResultsErrors.UNEXPECTED);
    }

    private Map<String, Object> fillModel() {
        Map<String, Object> model = new HashMap<String, Object>();
        newTab().fillModel(model, build());
        // The JSP fails to render without "jobs", whatever happened
        Assert.assertNotNull(model.get("jobs"));
        return model;
    }

    private static void assertError(Map<String, Object> model, String expected) {
        Assert.assertEquals(model.get("sauceError"), expected);
        Assert.assertNull(model.get("sauceNotice"));
        Assert.assertTrue(((List<?>) model.get("jobs")).isEmpty());
    }

    private static void assertNotice(Map<String, Object> model, String expected) {
        Assert.assertEquals(model.get("sauceNotice"), expected);
        Assert.assertNull(model.get("sauceError"));
        Assert.assertTrue(((List<?>) model.get("jobs")).isEmpty());
    }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int status;
        String body;
        if (path.contains("/v2/builds/") && path.endsWith("/jobs/")) {
            status = jobsInBuildStatus;
            body = jobsInBuildBody;
        } else if (path.contains("/v2/builds/")) {
            status = buildsStatus;
            body = buildsBody;
        } else {
            status = jobStatus;
            body = jobBody;
        }
        byte[] bytes = (status == 200 ? body : "{\"message\":\"error\"}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static SauceBuildResultsTab newTab() {
        return new SauceBuildResultsTab(stub(WebControllerManager.class, Collections.emptyMap()),
                stub(BuildsManager.class, Collections.emptyMap()),
                stub(PluginDescriptor.class, Collections.singletonMap("getPluginResourcesPath", "sauceBuildResults.jsp")));
    }

    private SBuild build() {
        Map<String, Object> feature = new HashMap<String, Object>();
        feature.put("getType", Constants.BUILD_FEATURE_TYPE);
        feature.put("getParameters", featureParameters);

        Map<String, Object> buildType = new HashMap<String, Object>();
        buildType.put("getBuildFeatures", Collections.singletonList(stub(SBuildFeatureDescriptor.class, feature)));

        Map<String, Object> build = new HashMap<String, Object>();
        build.put("getBuildType", stub(SBuildType.class, buildType));
        build.put("getAgentName", "Default Agent");
        build.put("getBuildTypeExternalId", "Project_Build");
        build.put("getBuildNumber", "1");
        return stub(SBuild.class, build);
    }

    /**
     * A stand-in for a TeamCity interface: returns the given answers by method name, other interfaces' stand-ins
     * for methods returning an interface, and null/0/false otherwise.
     */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, ?> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (answers.containsKey(method.getName())) {
                return answers.get(method.getName());
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.isInterface()) {
                return stub(returnType, Collections.emptyMap());
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType.isPrimitive() && returnType != void.class) {
                return returnType == long.class ? 0L : returnType == double.class ? 0d : returnType == float.class ? 0f : 0;
            }
            return null;
        });
    }
}
