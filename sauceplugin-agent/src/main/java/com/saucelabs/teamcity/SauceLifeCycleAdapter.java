package com.saucelabs.teamcity;

import com.saucelabs.ci.Browser;
import com.saucelabs.ci.BrowserFactory;
import com.saucelabs.ci.sauceconnect.AbstractSauceTunnelManager;
import com.saucelabs.ci.sauceconnect.SauceConnectFourManager;
import com.saucelabs.saucerest.SauceREST;
import com.saucelabs.saucerest.api.HttpClientConfig;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;

// https://confluence.jetbrains.com/display/TCD10/Agent-side+Object+Model

/**
 * Handles populating the environment variables and starting and stopping Sauce Connect when a TeamCity build is started/stopped.
 *
 * @author Ross Rowe
 */
public class SauceLifeCycleAdapter extends AgentLifeCycleAdapter {

    /**
     * Singleton Sauce Connect Manager instance, populated by Spring.
     */
    private final SauceConnectFourManager sauceConnectManager;
    /**
     * Singleton instance used to retrieve browser information supported by Sauce, populated by Spring.
     */
    private BrowserFactory sauceBrowserFactory;

    private void logInfo(@NotNull AgentRunningBuild build, @NotNull String msg) {
        Loggers.AGENT.info(msg);
        build.getBuildLogger().logMessage(DefaultMessagesInfo.createTextMessage(msg));
    }

    private void logError(@NotNull AgentRunningBuild build, @NotNull String msg, @NotNull Throwable err) {
        Loggers.AGENT.error(msg, err);
        build.getBuildLogger().logMessage(DefaultMessagesInfo.createError(msg, null, err));
    }

    /**
     * @param agentDispatcher ???
     * @param sauceBrowserFactory    Singleton instance used to retrieve browser information supported by Sauce, populated by Spring.
     * @param sauceConnectManager    Singleton Sauce Connect Manager instance, populated by Spring.
     */
    public SauceLifeCycleAdapter(
            @NotNull EventDispatcher<AgentLifeCycleListener> agentDispatcher,
            BrowserFactory sauceBrowserFactory,
            SauceConnectFourManager sauceConnectManager) {
        agentDispatcher.addListener(this);
        this.sauceBrowserFactory = sauceBrowserFactory;
        this.sauceConnectManager = sauceConnectManager;
    }

    /**
     * If Sauce Connect is enabled, then close the Sauce Connect process.
     *
     * @param build       the current build
     * @param buildStatus state of the build
     */
    @Override
    public void beforeBuildFinish(@NotNull final AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
        super.beforeBuildFinish(build, buildStatus);

        String agentName = build.getAgentConfiguration().getName();

        Collection<AgentBuildFeature> features = build.getBuildFeaturesOfType("sauce");
        if (features.isEmpty()) return;
        for (AgentBuildFeature feature : features) {
            logInfo(build, "Closing Sauce Connect");
            if (shouldStartSauceConnect(feature)) {
                String options = getSauceConnectOptions(build, feature);
                PrintStream printStream = new PrintStream(new NullOutputStream()) {
                    @Override
                    public void println(String x) {
                        build.getBuildLogger().logMessage(DefaultMessagesInfo.createTextMessage(x));
                    }
                };

                sauceConnectManager.closeTunnelsForPlan(getUsername(feature, agentName), options, printStream);
            }
        }
    }

    /**
     * @param feature contains the Sauce information set by the user within the build configuration
     * @return boolean indicating whether Sauce Connect v3 should be started
     */
    private boolean shouldStartSauceConnectThree(AgentBuildFeature feature) {
        String useSauceConnect = feature.getParameters().get(Constants.USE_SAUCE_CONNECT_3);
        return useSauceConnect != null && useSauceConnect.equals("true");
    }

    /**
     * If the build has the Sauce build feature enabled, populates the environment variables and starts Sauce Connect.
     *
     * @param runningBuild the current running build
     */
    @Override
    public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
        super.buildStarted(runningBuild);
        logInfo(runningBuild, "Build Started, setting Sauce environment variables");
        Collection<AgentBuildFeature> features = runningBuild.getBuildFeaturesOfType("sauce");
        if (features.isEmpty()) return;
        for (AgentBuildFeature feature : features) {
            populateEnvironmentVariables(runningBuild, feature);
            if (shouldStartSauceConnect(feature)) {
                startSauceConnect(runningBuild, feature);
            }
        }
    }

    /**
     * Starts Sauce Connect.
     *
     * @param runningBuild
     * @param feature      contains the Sauce information set by the user within the build configuration
     */
    private void startSauceConnect(final AgentRunningBuild runningBuild, AgentBuildFeature feature) {
        String agentName = runningBuild.getAgentConfiguration().getName();

        logInfo(runningBuild, "Starting Sauce Connect");
        String options = getSauceConnectOptions(runningBuild, feature);
        addSharedEnvironmentVariable(runningBuild, Constants.TUNNEL_IDENTIFIER, AbstractSauceTunnelManager.getTunnelName(options, "default"));

        PrintStream printStream = new PrintStream(new NullOutputStream()) {
            @Override
            public void println(String x) {
                runningBuild.getBuildLogger().logMessage(DefaultMessagesInfo.createTextMessage(x));
            }
        };

        // set to use latest sauce if set
        sauceConnectManager.setUseLatestSauceConnect(shouldUseLatestSauceConnect(feature));

        try {
            sauceConnectManager.openConnection(
                    getUsername(feature, agentName),
                    getAccessKey(feature, agentName),
                    Integer.parseInt(getSeleniumPort(feature)),
                    null,
                    options,
                    printStream,
                    Boolean.TRUE,
                    null
            );
        } catch (Throwable e) {
            logError(runningBuild, "Error launching Sauce Connect", e);
            runningBuild.getBuildLogger().logBuildProblem(BuildProblemData.createBuildProblem(
                    "SAUCE_CONNECT",
                    "FAILED_TO_START_SAUCE_CONNECT",
                    "Failed to start sauce connect: " + e.getMessage()
            ));
        }
    }

    private String getSauceConnectOptions(AgentRunningBuild runningBuild, AgentBuildFeature feature) {
        String options = feature.getParameters().get(Constants.SAUCE_CONNECT_OPTIONS);
        SauceREST sauceREST = getSauceREST(feature, runningBuild.getAgentConfiguration().getName());

        if (options == null || options.equals("")) {
            //default tunnel identifier to teamcity-%teamcity.agent.name%
            options = "-i teamcity-" + StringUtils.deleteWhitespace(runningBuild.getSharedConfigParameters().get("teamcity.agent.name"));
        }
        options = "-x " + sauceREST.getServer() + "rest/v1" + " " + options;
        return options;
    }

    /**
     * @param feature contains the Sauce information set by the user within the build configuration
     * @return
     */
    private String getSeleniumHost(AgentBuildFeature feature) {
        String host = feature.getParameters().get(Constants.SELENIUM_HOST_KEY);
        if (host == null || host.equals("")) {
            if (shouldStartSauceConnect(feature)) {
                host = "localhost";
            } else {
                host = "ondemand.saucelabs.com";
            }
        }
        return host;
    }

    /**
     * @param feature contains the Sauce information set by the user within the build configuration
     * @return
     */
    private String getSeleniumPort(AgentBuildFeature feature) {
        String port = feature.getParameters().get(Constants.SELENIUM_PORT_KEY);
        if (port == null || port.equals("")) {
            if (shouldStartSauceConnect(feature)) {
                port = "4445";
            } else {
                port = "80";
            }

        }
        return port;

    }

    /**
     * @param feature contains the Sauce information set by the user within the build configuration
     * @return
     */
    private boolean shouldStartSauceConnect(AgentBuildFeature feature) {
        String useSauceConnect = feature.getParameters().get(Constants.SAUCE_CONNECT_KEY);
        return useSauceConnect != null && useSauceConnect.equals("true");
    }

    /**
     * @param feature contains the Sauce information set by the user within the build configuration
     * @return boolean indicating whether the latest Sauce Connect should be used
     */
    private boolean shouldUseLatestSauceConnect(AgentBuildFeature feature) {
        String useSauceConnect = feature.getParameters().get(Constants.USE_LATEST_SAUCE_CONNECT);
        return useSauceConnect != null && useSauceConnect.equals("true");
    }

    /**
     * @param runningBuild
     * @param feature contains the Sauce information set by the user within the build configuration
     */
    private void populateEnvironmentVariables(AgentRunningBuild runningBuild, AgentBuildFeature feature) {
        String agentName = runningBuild.getAgentConfiguration().getName();
        logInfo(runningBuild, "Populating environment variables");
        String userName = getUsername(feature, agentName);
        String apiKey = getAccessKey(feature, agentName);
        String dataCenter = getDataCenter(feature, agentName);

        String[] selectedBrowsers = getSelectedBrowsers(runningBuild, feature);
        if (selectedBrowsers.length == 0) {
            logInfo(runningBuild, "No selected browsers found");
        } else {
            logInfo(runningBuild, "Selected browsers: " + Arrays.toString(selectedBrowsers));
            if (selectedBrowsers.length == 1) {
                Browser browser = sauceBrowserFactory.webDriverBrowserForKey(selectedBrowsers[0]);
                if (browser == null) {
                    logInfo(runningBuild, "No browser found for: " + selectedBrowsers[0]);
                    logInfo(runningBuild, "Browsers : " + sauceBrowserFactory);
                } else {
                    String sodDriverURI = getSodDriverUri(userName, apiKey, browser, feature);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_BROWSER_ENV, browser.getBrowserName());
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_VERSION_ENV, browser.getVersion());
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_PLATFORM_ENV, browser.getOs());
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_DRIVER_ENV, sodDriverURI);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_ORIENTATION, browser.getDeviceOrientation());
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_DEVICE, browser.getDevice());
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_DEVICE_TYPE, browser.getDeviceType());
                }
            }

            JSONArray browsersJSON = new JSONArray();
            for (String browser : selectedBrowsers) {
                Browser browserInstance = sauceBrowserFactory.webDriverBrowserForKey(browser);
                if (browserInstance != null) {
                    browserAsJSON(runningBuild, userName, apiKey, browsersJSON, browserInstance);
                }
            }
            addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_BROWSERS_ENV, browsersJSON.toString());

        }
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_USER_NAME, userName);
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_API_KEY, apiKey);
        //backwards compatibility with environment variables expected by Sausage
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_USERNAME, userName);
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_ACCESS_KEY, apiKey);
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_DATA_CENTER, dataCenter);

        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_HOST_ENV, getSeleniumHost(feature));
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_PORT_ENV, getSeleniumPort(feature));
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_STARTING_URL_ENV, feature.getParameters().get(Constants.SELENIUM_STARTING_URL_KEY));
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_MAX_DURATION_ENV, feature.getParameters().get(Constants.SELENIUM_MAX_DURATION_KEY));
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_IDLE_TIMEOUT_ENV, feature.getParameters().get(Constants.SELENIUM_IDLE_TIMEOUT_KEY));
        addSharedEnvironmentVariable(runningBuild, Constants.BUILD_NUMBER_ENV, runningBuild.getBuildTypeExternalId() + runningBuild.getBuildNumber());
    }

    /**
     * @param runningBuild
     * @param userName
     * @param apiKey
     * @param browsersJSON
     * @param browserInstance
     */
    private void browserAsJSON(AgentRunningBuild runningBuild, String userName, String apiKey, JSONArray browsersJSON, Browser browserInstance) {
        if (browserInstance == null) {
            return;
        }
        JSONObject config = new JSONObject();
        try {
            config.put("os", browserInstance.getOs());
            config.put("browser", browserInstance.getBrowserName());
            config.put("browser-version", browserInstance.getVersion());
            config.put("long-name", browserInstance.getLongName());
            config.put("long-version", browserInstance.getLongVersion());
            config.put("url", browserInstance.getUri(userName, apiKey));
            if (browserInstance.getDevice() != null) {
                config.put("device", browserInstance.getDevice());
            }
            if (browserInstance.getDeviceType() != null) {
                config.put("device-type", browserInstance.getDeviceType());
            }
            if (browserInstance.getDeviceOrientation() != null) {
                config.put("device-orientation", browserInstance.getDeviceOrientation());
            }

        } catch (JSONException e) {
            logError(runningBuild, "Unable to create JSON Object", e);
        }
        browsersJSON.put(config);
    }

    private void addSharedEnvironmentVariable(AgentRunningBuild runningBuild, String key, String value) {
        if (value != null) {
            logInfo(runningBuild, "Setting environment variable: " + key);
            runningBuild.addSharedEnvironmentVariable(key, value);
        }
    }

    private String getAccessKey(AgentBuildFeature feature, String agentName) {
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        return provider.getAccessKey();
    }

    private String getUsername(AgentBuildFeature feature, String agentName) {
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        return provider.getUsername();
    }

    private String getDataCenter(AgentBuildFeature feature, String agentName) {
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        return provider.getDataCenter();
    }

    protected SauceREST getSauceREST(AgentBuildFeature feature, String agentName) {
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        HttpClientConfig config = HttpClientConfig.defaultConfig().interceptor(new UserAgentInterceptor());
        return new SauceREST(
            provider.getUsername(),
            provider.getAccessKey(),
            provider.getSauceRESTDataCenter(),
            config
        );
    }
    /**
     * Generates a String that represents the Sauce OnDemand driver URL. This is used by the
     * <a href="http://selenium-client-factory.infradna.com/">selenium-client-factory</a> library to instantiate the Sauce-specific drivers.
     *
     * @param username String representing Sauce Username
     * @param apiKey String representing Sauce API Key
     * @param feature Plugin configuration
     * @return String representing the Sauce OnDemand driver URI
     */
    protected String getSodDriverUri(String username, String apiKey, Browser browser, AgentBuildFeature feature) {
        StringBuilder sb = new StringBuilder("sauce-ondemand:?username=");
        sb.append(username);
        sb.append("&access-key=").append(apiKey);
        if (browser != null) {
            sb.append("&os=").append(browser.getOs());
            sb.append("&browser=").append(browser.getBrowserName());
            sb.append("&browser-version=").append(browser.getVersion());
        }
        sb.append("&max-duration=").append(feature.getParameters().get(Constants.SELENIUM_MAX_DURATION_KEY));
        sb.append("&idle-timeout=").append(feature.getParameters().get(Constants.SELENIUM_IDLE_TIMEOUT_KEY));

        return sb.toString();
    }

    /**
     *
     * @param runningBuild
     * @param feature contains the Sauce information set by the user within the build configuration
     * @return
     */
    private String[] getSelectedBrowsers(AgentRunningBuild runningBuild, AgentBuildFeature feature) {
        logInfo(runningBuild, "Retrieving parameter: " + Constants.SELENIUM_SELECTED_BROWSER);
        String selectedBrowser = feature.getParameters().get(Constants.SELENIUM_SELECTED_BROWSER);
        logInfo(runningBuild, "Parameter value: " + selectedBrowser);
        if (selectedBrowser != null) {
            String[] selectedBrowsers = selectedBrowser.split(",");
            if (selectedBrowsers.length != 0) {
                return selectedBrowsers;
            }
        }
        logInfo(runningBuild, "Retrieving parameter: " + Constants.SELENIUM_WEB_DRIVER_BROWSERS);
        selectedBrowser = feature.getParameters().get(Constants.SELENIUM_WEB_DRIVER_BROWSERS);
        logInfo(runningBuild, "Parameter value: " + selectedBrowser);
        if (selectedBrowser != null) {
            return selectedBrowser.split(",");
        } else {
            return new String[]{};
        }
    }
}
