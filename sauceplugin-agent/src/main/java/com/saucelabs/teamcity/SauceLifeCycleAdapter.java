package com.saucelabs.teamcity;

import com.saucelabs.ci.Browser;
import com.saucelabs.ci.BrowserFactory;
import com.saucelabs.ci.sauceconnect.AbstractSauceTunnelManager;
import com.saucelabs.ci.sauceconnect.SauceConnectManager;
import com.saucelabs.saucerest.DataCenter;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.IOException;
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
    private final SauceConnectManager sauceConnectManager;
    /**
     * Singleton instance used to retrieve browser information supported by Sauce, populated by Spring.
     */
    private BrowserFactory sauceBrowserFactory;

    /**
     * @param agentDispatcher     ???
     * @param sauceBrowserFactory Singleton instance used to retrieve browser information supported by Sauce, populated by Spring.
     * @param sauceConnectManager Singleton Sauce Connect Manager instance, populated by Spring.
     */
    public SauceLifeCycleAdapter(
            @NotNull EventDispatcher<AgentLifeCycleListener> agentDispatcher,
            BrowserFactory sauceBrowserFactory,
            SauceConnectManager sauceConnectManager) {
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

        Logger logger = new LoggerBuildAndAgent(build.getBuildLogger());
        String agentName = build.getAgentConfiguration().getName();

        Collection<AgentBuildFeature> features = build.getBuildFeaturesOfType("sauce");
        if (features.isEmpty()) return;
        for (AgentBuildFeature feature : features) {
            logger.info("Closing Sauce Connect");
            if (shouldStartSauceConnect(feature)) {
                String options = getSauceConnectOptions(build, feature, null);
                PrintStream printStream = createPrintStream(build);
                sauceConnectManager.closeTunnelsForPlan(getUsername(feature, agentName), options, printStream);
            }
        }
    }

    /**
     * If the build has the Sauce build feature enabled, populates the environment variables and starts Sauce Connect.
     *
     * @param runningBuild the current running build
     */
    @Override
    public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
        super.buildStarted(runningBuild);
        Logger logger = new LoggerBuildAndAgent(runningBuild.getBuildLogger());
        logger.info("Build Started, setting Sauce environment variables");
        Collection<AgentBuildFeature> features = runningBuild.getBuildFeaturesOfType("sauce");
        if (features.isEmpty()) return;
        for (AgentBuildFeature feature : features) {
            populateEnvironmentVariables(runningBuild, feature, logger);
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
        Logger logger = new LoggerBuildAndAgent(runningBuild.getBuildLogger());
        String agentName = runningBuild.getAgentConfiguration().getName();
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        DataCenter region = provider.getSauceRESTDataCenter();

        logger.info("Starting Sauce Connect");
        String options = getSauceConnectOptions(runningBuild, feature, region);
        addSharedEnvironmentVariable(runningBuild, Constants.TUNNEL_IDENTIFIER, AbstractSauceTunnelManager.getTunnelName(options, "default"), logger);

        PrintStream printStream = createPrintStream(runningBuild);

        // set to use latest sauce if set
        sauceConnectManager.setUseLatestSauceConnect(shouldUseLatestSauceConnect(feature));

        try {
            sauceConnectManager.openConnection(
                getUsername(feature, agentName),
                getAccessKey(feature, agentName),
                provider.getSauceRESTDataCenter(),
                options,
                logger,
                printStream,
                true
            );
        } catch (IOException e) {
            runningBuild.getBuildLogger().logBuildProblem(BuildProblemData.createBuildProblem(
                    "SAUCE_CONNECT",
                    "FAILED_TO_START_SAUCE_CONNECT",
                    "Failed to start sauce connect: " + e.getMessage()
            ));
        }
    }

    private String getSauceConnectOptions(AgentRunningBuild runningBuild, AgentBuildFeature feature, DataCenter region) {
        String options = feature.getParameters().get(Constants.SAUCE_CONNECT_OPTIONS);

        if (options == null || options.isEmpty()) {
            //default tunnel identifier to teamcity-%teamcity.agent.name%
            options = "--tunnel-name teamcity-" + StringUtils.deleteWhitespace(runningBuild.getSharedConfigParameters().get("teamcity.agent.name"));
        }

        if (region != null) {
            String regionName = region.name().toLowerCase().replace("_", "-");
            options = "--region " + regionName + " " + options;
        }

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
     * @param feature      contains the Sauce information set by the user within the build configuration
     */
    private void populateEnvironmentVariables(AgentRunningBuild runningBuild, AgentBuildFeature feature, Logger logger) {
        String agentName = runningBuild.getAgentConfiguration().getName();
        logger.info("Populating environment variables");
        String userName = getUsername(feature, agentName);
        String apiKey = getAccessKey(feature, agentName);
        String dataCenter = getDataCenter(feature, agentName);

        String[] selectedBrowsers = getSelectedBrowsers(logger, feature);
        if (selectedBrowsers.length == 0) {
            logger.info("No selected browsers found");
        } else {
            logger.info("Selected browsers: {}", Arrays.toString(selectedBrowsers));
            if (selectedBrowsers.length == 1) {
                Browser browser = sauceBrowserFactory.webDriverBrowserForKey(selectedBrowsers[0]);
                if (browser == null) {
                    logger.info("No browser found for: {}", selectedBrowsers[0]);
                    logger.info("Browsers: {}", sauceBrowserFactory);
                } else {
                    String sodDriverURI = getSodDriverUri(userName, apiKey, browser, feature);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_BROWSER_ENV, browser.getBrowserName(), logger);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_VERSION_ENV, browser.getVersion(), logger);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_PLATFORM_ENV, browser.getOs(), logger);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_DRIVER_ENV, sodDriverURI, logger);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_ORIENTATION, browser.getDeviceOrientation(), logger);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_DEVICE, browser.getDevice(), logger);
                    addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_DEVICE_TYPE, browser.getDeviceType(), logger);
                }
            }

            JSONArray browsersJSON = new JSONArray();
            for (String browser : selectedBrowsers) {
                Browser browserInstance = sauceBrowserFactory.webDriverBrowserForKey(browser);
                if (browserInstance != null) {
                    browserAsJSON(logger, userName, apiKey, browsersJSON, browserInstance);
                }
            }
            addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_BROWSERS_ENV, browsersJSON.toString(), logger);

        }
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_USER_NAME, userName, logger);
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_API_KEY, apiKey, logger);
        //backwards compatibility with environment variables expected by Sausage
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_USERNAME, userName, logger);
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_ACCESS_KEY, apiKey, logger);
        addSharedEnvironmentVariable(runningBuild, Constants.SAUCE_DATA_CENTER, dataCenter, logger);

        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_HOST_ENV, getSeleniumHost(feature), logger);
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_PORT_ENV, getSeleniumPort(feature), logger);
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_STARTING_URL_ENV, feature.getParameters().get(Constants.SELENIUM_STARTING_URL_KEY), logger);
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_MAX_DURATION_ENV, feature.getParameters().get(Constants.SELENIUM_MAX_DURATION_KEY), logger);
        addSharedEnvironmentVariable(runningBuild, Constants.SELENIUM_IDLE_TIMEOUT_ENV, feature.getParameters().get(Constants.SELENIUM_IDLE_TIMEOUT_KEY), logger);
        addSharedEnvironmentVariable(runningBuild, Constants.BUILD_NUMBER_ENV, runningBuild.getBuildTypeExternalId() + runningBuild.getBuildNumber(), logger);
    }

    private void browserAsJSON(Logger logger, String userName, String apiKey, JSONArray browsersJSON, Browser browserInstance) {
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
            logger.error("Unable to create JSON Object", e);
        }
        browsersJSON.put(config);
    }

    private void addSharedEnvironmentVariable(AgentRunningBuild runningBuild, String key, String value, Logger logger) {
        if (value != null) {
            logger.info("Setting environment variable {}", key);
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

    /**
     * Generates a String that represents the Sauce OnDemand driver URL. This is used by the
     * <a href="http://selenium-client-factory.infradna.com/">selenium-client-factory</a> library to instantiate the Sauce-specific drivers.
     *
     * @param username String representing Sauce Username
     * @param apiKey   String representing Sauce API Key
     * @param feature  Plugin configuration
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
     * @param feature      contains the Sauce information set by the user within the build configuration
     */
    private String[] getSelectedBrowsers(Logger logger, AgentBuildFeature feature) {
        logger.info("Retrieving parameter: {}", Constants.SELENIUM_SELECTED_BROWSER);
        String selectedBrowser = feature.getParameters().get(Constants.SELENIUM_SELECTED_BROWSER);
        logger.info("Parameter value: {}", selectedBrowser);
        if (selectedBrowser != null) {
            String[] selectedBrowsers = selectedBrowser.split(",");
            if (selectedBrowsers.length != 0) {
                return selectedBrowsers;
            }
        }
        logger.info("Retrieving parameter: {}", Constants.SELENIUM_WEB_DRIVER_BROWSERS);
        selectedBrowser = feature.getParameters().get(Constants.SELENIUM_WEB_DRIVER_BROWSERS);
        logger.info("Parameter value: {}", selectedBrowser);
        if (selectedBrowser != null) {
            return selectedBrowser.split(",");
        } else {
            return new String[]{};
        }
    }

    private PrintStream createPrintStream(@NotNull AgentRunningBuild build) {
        return new PrintStream(NullOutputStream.INSTANCE) {
            @Override
            public void println(String x) {
                build.getBuildLogger().logMessage(DefaultMessagesInfo.createTextMessage(x));
            }
        };
    }
}
