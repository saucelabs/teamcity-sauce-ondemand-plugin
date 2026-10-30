package com.saucelabs.teamcity.results;

import com.saucelabs.ci.JobInformation;
import com.saucelabs.saucerest.DataCenter;
import com.saucelabs.saucerest.SauceREST;
import com.saucelabs.saucerest.JobSource;
import com.saucelabs.saucerest.api.HttpClientConfig;
import com.saucelabs.saucerest.model.builds.*;
import com.saucelabs.saucerest.model.jobs.Job;
import com.saucelabs.teamcity.Constants;
import com.saucelabs.teamcity.ParametersProvider;
import com.saucelabs.teamcity.UserAgentInterceptor;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.web.openapi.BuildTab;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adds a Sauce-specific tab to the build results page.
 *
 * @author Ross Rowe
 */
public class SauceBuildResultsTab extends BuildTab {

    private static final Logger logger = Logger.getLogger(SauceBuildResultsTab.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd-HH";

    private static final String HMAC_KEY = "HMACMD5";

    static final String NO_TESTS = "No Sauce Labs tests were found for this build.";

    public SauceBuildResultsTab(WebControllerManager manager, BuildsManager buildManager, PluginDescriptor myPluginDescriptor) {
        super("sauceBuildResults", "Sauce Labs Results", manager, buildManager, myPluginDescriptor.getPluginResourcesPath("sauceBuildResults.jsp"));
    }

    /**
     * Returns the model to be used when rendering the sauce results jsp.
     *
     * @param model
     * @param build
     */
    @Override
    protected void fillModel(@NotNull Map<String, Object> model, @NotNull SBuild build) {
        //TODO can we store the Sauce session id on the build instance?
        //invoke Sauce REST API to retrieve job ids for TC build
        List<JobInformation> jobs = new ArrayList<JobInformation>();
        String error = null;
        String notice = null;
        try {
            jobs = retrieveJobIdsFromSauce(build);
        } catch (SauceResultsException e) {
            if (e.isNotice()) {
                notice = e.getMessage();
            } else {
                error = e.getMessage();
            }
        } catch (Exception | LinkageError e) {
            // The JSP requires "jobs" to be present, so a failure here must never skip the model.put calls below
            logger.error("Error retrieving job information", e);
            error = SauceResultsErrors.UNEXPECTED;
        }
        model.put("jobs", jobs);
        model.put("sauceError", error);
        model.put("sauceNotice", notice);
    }

    /**
     * Invokes the Sauce REST API to retrieve the build information.
     *
     * @param sauceREST   Sauce Rest object/credentials to use
     * @param buildNumber The build name on Sauce
     * @return Teamcity Build information
     * @throws JSONException Unable to parse json
     */
    public String retrieveBuildInformationFromSauce(
            SauceREST sauceREST, String buildNumber)
            throws IOException {
        logger.info("Performing Sauce REST retrieve results for " + buildNumber);

        List<Build> response = sauceREST.getBuildsEndpoint().lookupBuilds(JobSource.VDC, new LookupBuildsParameters.Builder().setName(buildNumber).setLimit(1).build());

        if (response.isEmpty()) {
            logger.error("Unable to find build for name: `" + buildNumber + "`");
            return "";
        }

        return response.get(0).id;
    }

    protected static List<String> getJobIdsForBuild(SauceREST sauceREST, String buildId) throws IOException {
        JobsInBuild response = sauceREST.getBuildsEndpoint().lookupJobsForBuild(JobSource.VDC, buildId, new LookupJobsParameters.Builder().build());
        if (response.jobs.isEmpty()) {
            logger.error("Build without jobs id=`" + buildId + "`");
            return new ArrayList<>();
        }

        return response.jobs.stream()
                .map(job -> job.id)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the list of Sauce jobs recorded against the TeamCity build.
     *
     * @param build
     * @return
     * @throws SauceResultsException with the message to show on the tab when there are no jobs to list
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     */
    public List<JobInformation> retrieveJobIdsFromSauce(SBuild build) throws SauceResultsException, InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
        //invoke Sauce Rest API to find plan results with those values
        List<JobInformation> jobInformation = new ArrayList<JobInformation>();

        SBuildFeatureDescriptor sauceBuildFeature = getSauceBuildFeature(build);
        if (sauceBuildFeature == null) {
            return null;
        }
        String agentName = build.getAgentName();
        ParametersProvider provider = new ParametersProvider(sauceBuildFeature.getParameters(), agentName);
        String username = provider.getUsername();
        String accessKey = provider.getAccessKey();
        if (username == null || username.trim().isEmpty() || accessKey == null || accessKey.trim().isEmpty()) {
            throw SauceResultsException.error("Add your Sauce Labs username and access key in the Sauce Labs build feature.");
        }
        DataCenter dataCenter = provider.getSauceRESTDataCenter();
        String buildNumber = build.getBuildTypeExternalId() + build.getBuildNumber();
        HttpClientConfig config = HttpClientConfig.defaultConfig().interceptor(new UserAgentInterceptor());
        SauceREST sauceREST = new SauceREST(username, accessKey, dataCenter, config);

        List<Job> response;
        try {
            String buildId = retrieveBuildInformationFromSauce(sauceREST, buildNumber);

            if (buildId == "") {
                logger.error("Unable to find build for name: `" + buildNumber + "`");
                throw SauceResultsException.notice(NO_TESTS);
            }

            logger.info("Retrieving jobs for  " + buildId);
            response = sauceREST.getJobsEndpoint().getJobDetails(getJobIdsForBuild(sauceREST, buildId));
            if (response.isEmpty()) {
                logger.error("Unable to get jobs for ID: `" + buildId + "`");
                throw SauceResultsException.notice(NO_TESTS);
            }
        } catch (SauceResultsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving job information", e);
            throw SauceResultsException.error(SauceResultsErrors.toMessage(e));
        }

        for (Job job : response) {
            JobInformation information = new JobInformation(job.id, calcHMAC(username, accessKey, job.id));
            information.populate(job);
            information.setLogUrl(getLogUrl(dataCenter));
            jobInformation.add(information);
        }

        //the list of results retrieved from the Sauce REST API is last-first, so reverse the list
        Collections.reverse(jobInformation);
        return jobInformation;
    }

    /**
     * Returns the Sauce-specific {@link SBuildFeatureDescriptor} instance.
     *
     * @param build
     * @return
     */
    private SBuildFeatureDescriptor getSauceBuildFeature(SBuild build) {
        Collection<SBuildFeatureDescriptor> features = build.getBuildType().getBuildFeatures();
        if (features.isEmpty()) return null;
        for (SBuildFeatureDescriptor feature : features) {
            if (feature.getType().equals(Constants.BUILD_FEATURE_TYPE)) {
                return feature;
            }

        }
        return null;
    }

    /**
     * @param build
     * @return true if sauce is configured
     */
    @Override
    protected boolean isAvailableFor(@NotNull SBuild build) {
        SBuildFeatureDescriptor sauceBuildFeature = getSauceBuildFeature(build);
        if (sauceBuildFeature == null) {
            return false;
        }
        if (sauceBuildFeature.getParameters().containsKey(Constants.DISABLE_RESULTS_KEY)
                && sauceBuildFeature.getParameters().get(Constants.DISABLE_RESULTS_KEY).equals("true")) {
            return false;
        }

        return super.isAvailableFor(build); //should return true
    }

    /**
     * Returns the HMAC to be used for authentication of the embedded Sauce job report.
     *
     * @param username
     * @param accessKey
     * @param jobId
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws UnsupportedEncodingException
     */
    public String calcHMAC(String username, String accessKey, String jobId) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Calendar calendar = Calendar.getInstance();

        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String key = username + ":" + accessKey + ":" + format.format(calendar.getTime());
        byte[] keyBytes = key.getBytes();
        SecretKeySpec sks = new SecretKeySpec(keyBytes, HMAC_KEY);
        Mac mac = Mac.getInstance(sks.getAlgorithm());
        mac.init(sks);
        byte[] hmacBytes = mac.doFinal(jobId.getBytes());
        byte[] hexBytes = new Hex().encode(hmacBytes);
        return new String(hexBytes, "ISO-8859-1");
    }

    private String getLogUrl(DataCenter dataCenter) {
        if (dataCenter == DataCenter.EU_CENTRAL) {
            return "https://app.eu-central-1.saucelabs.com";
        }
        return "https://app.saucelabs.com";
    }
}
