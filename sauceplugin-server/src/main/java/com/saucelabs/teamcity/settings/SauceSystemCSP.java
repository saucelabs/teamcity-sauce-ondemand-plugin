package com.saucelabs.teamcity.settings;

import jetbrains.buildServer.web.ContentSecurityPolicyConfig;


public class SauceSystemCSP {
    public SauceSystemCSP(ContentSecurityPolicyConfig config) {

        config.addDirectiveItems("script-src", "https://app.saucelabs.com");
        config.addDirectiveItems("frame-src", "https://app.saucelabs.com");
    }
}
