package com.saucelabs.teamcity;

import java.util.Map;

public class ParametersProvider {

    private Map<String, String> parameters;
    private String agentName;
    public static final String SAUCE_PLUGIN_DEFAULT_DATA_CENTER = "US";
    public static final String TEAMCITY_AGENT_NAME = "%teamcity.agent.name%";

    public ParametersProvider(Map<String, String> parameters, String agentName) {
        this.parameters = parameters;
        this.agentName = agentName;
    }

    public String getUsername() {
        String username = this.parameters.get(Constants.SAUCE_USER_ID_KEY);
        if (username.equals(TEAMCITY_AGENT_NAME)) {
            username = this.agentName;
        }
        return username;
    }

    public String getAccessKey() { return this.parameters.get(Constants.SAUCE_PLUGIN_ACCESS_KEY); }

    public String getDataCenter() {
        String dataCenter = this.parameters.get(Constants.SAUCE_PLUGIN_DATA_CENTER);
        if (dataCenter == null || dataCenter == "") {
            dataCenter = SAUCE_PLUGIN_DEFAULT_DATA_CENTER;
        }
        return dataCenter;
    }
}
