package com.saucelabs.teamcity;

import java.util.Map;

public class ParametersProvider {

    private Map<String, String> parameters;
    public static final String SAUCE_PLUGIN_DEFAULT_DATA_CENTER = "US";

    public ParametersProvider(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public String getUsername() { return this.parameters.get(Constants.SAUCE_USER_ID_KEY); }

    public String getAccessKey() { return this.parameters.get(Constants.SAUCE_PLUGIN_ACCESS_KEY); }

    public String getDataCenter() {
        String dataCenter = this.parameters.get(Constants.SAUCE_PLUGIN_DATA_CENTER);
        if (dataCenter == null || dataCenter == "") {
            dataCenter = SAUCE_PLUGIN_DEFAULT_DATA_CENTER;
        }
        return dataCenter;
    }
}
