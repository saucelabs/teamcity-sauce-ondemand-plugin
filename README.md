Sauce Labs TeamCity Plugin
=====================

[![Build Status](https://travis-ci.org/saucelabs/teamcity-sauce-ondemand-plugin.svg?branch=master)](https://travis-ci.org/saucelabs/teamcity-sauce-ondemand-plugin)

This plugin allows you to integrate Sauce Labs with TeamCity. Specifically, you can:

*    Specify the browsers versions and operating systems you want your tests to run against
*    Automate the setup and tear down of Sauce Connect, which enables you to run your Selenium tests against local websites using Sauce Labs
*    Integrate the Sauce results videos within the TeamCity build output


Installation
====

[Download](https://saucelabs.com/downloads/teamcity/release/com/saucelabs/teamcity/build/1.45/build-1.45.zip) the plugin zip file and copy it into your ~/.BuildServer/plugins directory

For more information, see [Sauce Labs with TeamCity](https://docs.saucelabs.com/ci/teamcity/) in the Sauce Labs Documentation.

Usage
===

The plugin provides a 'Sauce Labs Build Feature' which can be added a TeamCity build.

Once the build feature has been selected, enter your Sauce Labs username and access key, and specify whether you want Sauce Connect to be launched as part of your build.  You can also select the browsers you wish to be used by your tests and the data center your tests and sauce connect will be run on.  By default sauce connect will connect to our 'US' data center other options are 'US_EAST' for headless testing and 'EU'. 

In order to integrate the Sauce tests with the TeamCity build, you will need to include the following output as part of the running of each test:

    SauceOnDemandSessionID=SESSION_ID job-name=JOB_NAME

where SESSION_ID is the job session id, and job-name is the name of your job.

Release process:
===========

Make sure you have below server tags added to maven settings (~/.m2/settings.xml)

```
<servers>
    <server>
     <id>teamcity.s3.release</id>
     <username>AWS_ACCESS_KEY_ID</username>
     <password>AWS_SECRET_ACCESS_KEY</password>
   </server>
   <server>
     <id>teamcity.s3.snapshot</id>
     <username>AWS_ACCESS_KEY_ID</username>
     <password>AWS_SECRET_ACCESS_KEY</password>
   </server>
   <server>
<servers>
```

Prepare release and perform:

```
mvn release:clean release:prepare
mvn release:perform
```
