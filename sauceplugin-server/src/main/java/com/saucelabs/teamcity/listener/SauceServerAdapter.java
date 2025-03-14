package com.saucelabs.teamcity.listener;

import com.saucelabs.saucerest.DataCenter;
import com.saucelabs.saucerest.SauceREST;
import com.saucelabs.saucerest.api.HttpClientConfig;
import com.saucelabs.saucerest.model.jobs.UpdateJobParameter;
import com.saucelabs.teamcity.Constants;
import com.saucelabs.teamcity.ParametersProvider;
import com.saucelabs.teamcity.UserAgentInterceptor;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.buildLog.LogMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * Server-side adapter which invokes post-build logic.
 *
 * @author Ross Rowe
 */
public class SauceServerAdapter extends BuildServerAdapter {

    private static final String SAUCE_ON_DEMAND_SESSION_ID = "SauceOnDemandSessionID";
    private final SBuildServer myBuildServer;

    private static final Logger logger = Logger.getLogger(SauceServerAdapter.class);

    public SauceServerAdapter(SBuildServer sBuildServer) {
        myBuildServer = sBuildServer;
    }

    public void register() {
        myBuildServer.addListener(this);
    }

    /**
     * Invoked when a build is finished.  Iterates over the build output and identifies lines which contains 'SauceOnDemandSessionID',
     * and for each line, invokes the Sauce REST API to associate the TeamCity build number with the Sauce Job.
     *
     * @param build
     */
    @Override
    public void buildFinished(SRunningBuild build) {
        super.buildFinished(build);

        Iterator<LogMessage> iterator = build.getBuildLog().getMessagesIterator();
        while (iterator.hasNext()) {
            LogMessage logMessage = iterator.next();
            String line = logMessage.getText();
            if (StringUtils.containsIgnoreCase(line, SAUCE_ON_DEMAND_SESSION_ID)) {
                //extract session id
                String sessionId = StringUtils.substringBetween(line, SAUCE_ON_DEMAND_SESSION_ID + "=", " ");
                if (sessionId == null) {
                    //we might not have a space separating the session id and job-name, so retrieve the text up to the end of the string
                    sessionId = StringUtils.substringAfter(line, SAUCE_ON_DEMAND_SESSION_ID + "=");
                }
                if (sessionId != null && !sessionId.equalsIgnoreCase("null")) {
                    storeBuildNumberInSauce(build, sessionId);
                    //build.getTags().add(sessionId);
                }
            }
        }
    }

    /**
     * Invokes the Sauce REST API to store the TeamCity build number and pass/fail status within
     * Sauce.
     *
     * @param build
     * @param sessionId
     */
    private void storeBuildNumberInSauce(SRunningBuild build, String sessionId) {
        String agentName = build.getAgentName();
        Collection<SBuildFeatureDescriptor> features = build.getBuildType().getBuildFeatures();
        if (features.isEmpty()) return;
        for (SBuildFeatureDescriptor feature : features) {
            if (feature.getType().equals(Constants.BUILD_FEATURE_TYPE)) {
                HttpClientConfig config = HttpClientConfig.defaultConfig().interceptor(new UserAgentInterceptor());
                SauceREST sauceREST = new SauceREST(
                        getUsername(feature, agentName),
                        getAccessKey(feature, agentName),
                        getDataCenter(feature, agentName),
                        config
                );

                try {
                    String buildNumber = build.getBuildTypeExternalId() + build.getBuildNumber();
                    logger.info("Setting build number " + buildNumber + " for job " + sessionId + " user: " + getUsername(feature, agentName));
                    UpdateJobParameter.Builder parameters = new UpdateJobParameter.Builder().setBuild(buildNumber);

                    if (build.getStatusDescriptor().getStatus().isSuccessful()) {
                        parameters.setPassed(true);
                    } else if (build.getStatusDescriptor().getStatus().isFailed()) {
                        parameters.setPassed(false);
                    }

                    sauceREST.getJobsEndpoint().updateJob(sessionId, parameters.build());
                } catch (IOException e) {
                    logger.error("Failed to parse JSON for session id: " + sessionId + " user: " + getUsername(feature, agentName), e);
                }
            }
        }
    }

    private String getAccessKey(SBuildFeatureDescriptor feature, String agentName) {
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        return provider.getAccessKey();
    }

    private String getUsername(SBuildFeatureDescriptor feature, String agentName) {
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        return provider.getUsername();
    }

    private DataCenter getDataCenter(SBuildFeatureDescriptor feature, String agentName) {
        ParametersProvider provider = new ParametersProvider(feature.getParameters(), agentName);
        return provider.getSauceRESTDataCenter();
    }
}
