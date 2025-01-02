package com.saucelabs.teamcity;

import com.saucelabs.saucerest.DataCenter;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;


class ParametersProviderTest {
    Map<String, String> parametersMap;
    String agentName = "agent name";

    @BeforeEach
    void beforeEach() {
        parametersMap = new HashMap<String, String>() {{
            put(Constants.SAUCE_USER_ID_KEY, "sauce_user_id_key");
            put(Constants.SAUCE_PLUGIN_ACCESS_KEY, "sauce_plugin_access_key");
            put(Constants.SAUCE_PLUGIN_DATA_CENTER, "EU");
        }};
    }

    @Test
    public void testGetUsernameWhenSet() {
        ParametersProvider provider = new ParametersProvider(parametersMap, agentName);
        Assertions.assertEquals(provider.getUsername(), "sauce_user_id_key");
    }

    @Test
    public void testGetAgentUsernameWhenTeamcityAgentNamePropertySet() {
        parametersMap.put(Constants.SAUCE_USER_ID_KEY, ParametersProvider.TEAMCITY_AGENT_NAME);
        ParametersProvider provider = new ParametersProvider(parametersMap, agentName);
        Assertions.assertEquals(provider.getUsername(), agentName);
    }

    @Test
    public void testGetAccessKeyWhenSet() {
        ParametersProvider provider = new ParametersProvider(parametersMap, agentName);
        Assertions.assertEquals(provider.getAccessKey(), "sauce_plugin_access_key");
    }

    @Test
    public void testGetDataCenterWhenSet() {
        ParametersProvider provider = new ParametersProvider(parametersMap, agentName);
        Assertions.assertEquals(provider.getDataCenter(), "EU");
    }

    @Test
    public void testGetDefaultDataCenterWhenNotSet() {
        ParametersProvider provider = new ParametersProvider(new HashMap<String, String>(), agentName);
        Assertions.assertEquals(provider.getDataCenter(), ParametersProvider.SAUCE_PLUGIN_DEFAULT_DATA_CENTER);
    }
}
