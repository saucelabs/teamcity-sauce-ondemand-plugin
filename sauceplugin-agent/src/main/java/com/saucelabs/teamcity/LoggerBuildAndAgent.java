package com.saucelabs.teamcity;

import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.messages.Status;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;


public class LoggerBuildAndAgent extends org.slf4j.helpers.AbstractLogger {
  BuildProgressLogger buildLogger;
  Boolean verbose;

  public LoggerBuildAndAgent(BuildProgressLogger logger, Boolean verbose) {
    this.buildLogger = logger;
    this.verbose = verbose;
  }

  @Override
  protected String getFullyQualifiedCallerName() {
    return null;
  }

  @Override
  protected void handleNormalizedLoggingCall(Level level, Marker marker, String s, Object[] objects, Throwable throwable) {
    String msg = MessageFormatter.basicArrayFormat(s, objects);
    logUsingBuildLogger(level, msg, throwable);
    logUsingAgentLogger(level, msg, throwable);
  }

  private void logUsingBuildLogger(Level level, String msg, Throwable throwable) {
    String msgWithPrefix = "[sauceplugin] [" + level + "] " + msg;

    if (!verbose && (level == Level.TRACE || level == Level.DEBUG)) {
      return;
    }

    Status status = Status.NORMAL;
    if (level == Level.WARN) {
      status = Status.WARNING;
    } else if (level == Level.ERROR) {
      status = Status.ERROR;
    }

    if (throwable == null) {
      buildLogger.logMessage(DefaultMessagesInfo.createTextMessage(msgWithPrefix, status));
    } else {
      buildLogger.logMessage(DefaultMessagesInfo.createError(msgWithPrefix, null, throwable));
    }
  }

  private void logUsingAgentLogger(Level level, String msg, Throwable throwable) {
    if (level == Level.TRACE) {
      Loggers.AGENT.debug(msg, throwable);
    } else if (level == Level.DEBUG) {
      Loggers.AGENT.debug(msg, throwable);
    } else if (level == Level.INFO) {
      Loggers.AGENT.info(msg, throwable);
    } else if (level == Level.WARN) {
      Loggers.AGENT.warn(msg, throwable);
    } else if (level == Level.ERROR) {
      Loggers.AGENT.error(msg, throwable);
    } else {
      Loggers.AGENT.info(msg, throwable);
    }
  }

  //
  // Ignore methods below, they are implemented just to satisfy the slf4j logger interface
  //

  @Override
  public boolean isTraceEnabled() {
    return true;
  }

  @Override
  public boolean isTraceEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isDebugEnabled() {
    return true;
  }

  @Override
  public boolean isDebugEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isInfoEnabled() {
    return true;
  }

  @Override
  public boolean isInfoEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isWarnEnabled() {
    return true;
  }

  @Override
  public boolean isWarnEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isErrorEnabled() {
    return true;
  }

  @Override
  public boolean isErrorEnabled(Marker marker) {
    return true;
  }
}
