package com.saucelabs.teamcity;

import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.messages.Status;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;


public class LoggerBuildAndAgent extends org.slf4j.helpers.AbstractLogger {
  BuildProgressLogger logger;

  public LoggerBuildAndAgent(BuildProgressLogger logger) {
    this.logger = logger;
  }

  @Override
  protected String getFullyQualifiedCallerName() {
    return null;
  }

  @Override
  protected void handleNormalizedLoggingCall(Level level, Marker marker, String s, Object[] objects, Throwable throwable) {
    String msg = MessageFormatter.basicArrayFormat(s, objects);
    String msgWithPrefix = "[sauceplugin] [" + level + "] " + msg;

    if (throwable == null) {
      Status status = Status.NORMAL;
      if (level == Level.WARN) {
        status = Status.WARNING;
      } else if (level == Level.ERROR) {
        status = Status.ERROR;
      }

      logger.logMessage(DefaultMessagesInfo.createTextMessage(msgWithPrefix, status));
    } else {
      logger.logMessage(DefaultMessagesInfo.createError(msgWithPrefix, null, throwable));
    }

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
