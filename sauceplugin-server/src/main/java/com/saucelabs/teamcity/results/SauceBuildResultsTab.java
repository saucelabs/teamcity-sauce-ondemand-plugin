package com.saucelabs.teamcity.results;

import com.saucelabs.ci.JobInformation;
import com.saucelabs.saucerest.SauceREST;
import com.saucelabs.teamcity.Constants;
import com.saucelabs.teamcity.ParametersProvider;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.web.openapi.BuildTab;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Adds a Sauce-specific tab to the build results page.
 *
 * @author Ross Rowe
 */
public class SauceBuildResultsTab extends BuildTab {

    private static final Logger logger = Logger.getLogger(SauceBuildResultsTab.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd-HH";

    private static final String HMAC_KEY = "HMACMD5";

    protected SauceBuildResultsTab(WebControllerManager manager, BuildsManager buildManager, PluginDescriptor myPluginDescriptor) {
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
        try {
            jobs = retrieveJobIdsFromSauce(build);
        } catch (IOException e) {
            logger.error("Error retrieving job information", e);
        } catch (JSONException e) {
            logger.error("Error retrieving job information", e);

        } catch (InvalidKeyException e) {
            logger.error("Error retrieving job information", e);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error retrieving job information", e);
        }
        model.put("jobs", jobs);
    }

    /**
     * Retrieve the list of Sauce jobs recorded against the TeamCity build.
     *
     * @param build
     * @return
     * @throws IOException
     * @throws JSONException
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     */
    public List<JobInformation> retrieveJobIdsFromSauce(SBuild build) throws IOException, JSONException, InvalidKeyException, NoSuchAlgorithmException {
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
        String dataCenter = provider.getDataCenter();
        String buildNumber = build.getBuildTypeExternalId() + build.getBuildNumber();
        SauceREST sauceREST = getSauceREST(username, accessKey, dataCenter);

        logger.info("Retrieving Sauce jobs for " + buildNumber + " user: " + username);
        String jsonResponse = sauceREST.getBuildJobs(buildNumber); // FIXME - limit 200);
        JSONObject job = new JSONObject(jsonResponse);
        JSONArray jobResults = job.getJSONArray("jobs");
        if (jobResults.length() == 0) {
            //try query using the build number
            logger.info("Retrieving Sauce jobs for " + build.getBuildNumber() + " user: " + username);
            jsonResponse = sauceREST.getBuildJobs(build.getBuildNumber()); // FIXME - limit 200);
            job = new JSONObject(jsonResponse);
            jobResults = job.getJSONArray("jobs");
        }

        for (int i = 0; i < jobResults.length(); i++) {
            //check custom data to find job that was for build
            JSONObject jobData = jobResults.getJSONObject(i);
            String jobId = jobData.getString("id");
            JobInformation information = new JobInformation(jobId, calcHMAC(username, accessKey, jobId));
            information.populateFromJson(jobData);
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
            if (feature.getType().equals("sauce")) {
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
    protected SauceREST getSauceREST(String username, String accessKey, String dataCenter) {
        if (dataCenter == null || dataCenter == "") {
            dataCenter = "US";
        }
        return new SauceREST(username,  accessKey, dataCenter);
    }

    private String getLogUrl(String dataCenter) {
        String url = "https://app.saucelabs.com";
        if (dataCenter.equals("EU")) {
            url = "https://app.eu-central-1.saucelabs.com";
        }
        if (dataCenter.equals("US_EAST")) {
            url = "https://app.us-east-1.saucelabs.com";
        }
        return url;
    } 
}
