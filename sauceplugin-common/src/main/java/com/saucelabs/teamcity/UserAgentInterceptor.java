package com.saucelabs.teamcity;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class UserAgentInterceptor implements Interceptor {
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        String userAgent = getUserAgent();

        Request modifiedRequest = chain.request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .build();

        return chain.proceed(modifiedRequest);
    }

    private String getUserAgent() {
        return "TeamCitySauceOnDemand-Agent/" + BuildUtils.getCurrentVersion();
    }
}