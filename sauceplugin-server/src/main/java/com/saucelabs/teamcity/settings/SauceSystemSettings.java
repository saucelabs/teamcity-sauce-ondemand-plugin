package com.saucelabs.teamcity.settings;

import com.saucelabs.saucerest.SauceREST;
import com.saucelabs.teamcity.BuildUtils;
import jetbrains.buildServer.agent.ServerProvidedProperties;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link BuildFeature} instance which is used to present the Sauce configuration options on a TeamCity project.
 *
 * @author Ross Rowe
 */
public class SauceSystemSettings extends BuildFeature {

    static {
        SauceREST.setExtraUserAgent("TeamCity/" + System.getProperty(
            ServerProvidedProperties.TEAMCITY_VERSION_PROP) + " " +
            "TeamCitySauceOnDemand/" + BuildUtils.getCurrentVersion());
    }

    private final PluginDescriptor myPluginDescriptor;

    public SauceSystemSettings(PluginDescriptor myPluginDescriptor, SBuildServer sBuildServer) {
        this.myPluginDescriptor = myPluginDescriptor;

        SauceREST.setExtraUserAgent("TeamCity/" + sBuildServer.getFullServerVersion() + " " +
            "TeamCitySauceOnDemand/" + this.myPluginDescriptor.getPluginVersion());
    }

    @NotNull
    @Override
    public String getType() {
        return "sauce";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Sauce Labs Build Feature";
    }

    @Nullable
    @Override
    public String getEditParametersUrl() {
        return myPluginDescriptor.getPluginResourcesPath("sauceSettings.jsp");
    }
}
