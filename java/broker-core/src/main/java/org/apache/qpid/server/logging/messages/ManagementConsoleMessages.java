/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.logging.messages;

import static org.apache.qpid.server.logging.AbstractMessageLogger.DEFAULT_LOG_HIERARCHY_PREFIX;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.logging.LogMessage;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * DO NOT EDIT DIRECTLY, THIS FILE WAS GENERATED.
 *
 * Generated using GenerateLogMessages and LogMessages.vm
 * This file is based on the content of ManagementConsole_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class ManagementConsoleMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String MANAGEMENTCONSOLE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole";
    public static final String OPEN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole.open";
    public static final String LISTENING_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole.listening";
    public static final String STOPPED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole.stopped";
    public static final String CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole.close";
    public static final String SHUTTING_DOWN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole.shutting_down";
    public static final String STARTUP_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole.startup";
    public static final String READY_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "managementconsole.ready";

    static
    {
        Logger.getLogger(MANAGEMENTCONSOLE_LOG_HIERARCHY);
        Logger.getLogger(OPEN_LOG_HIERARCHY);
        Logger.getLogger(LISTENING_LOG_HIERARCHY);
        Logger.getLogger(STOPPED_LOG_HIERARCHY);
        Logger.getLogger(CLOSE_LOG_HIERARCHY);
        Logger.getLogger(SHUTTING_DOWN_LOG_HIERARCHY);
        Logger.getLogger(STARTUP_LOG_HIERARCHY);
        Logger.getLogger(READY_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.ManagementConsole_logmessages", _currentLocale);
    }

    /**
     * Log a ManagementConsole message of the Format:
     * <pre>MNG-1007 : Open : User {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage OPEN(String param1)
    {
        String rawMessage = _messages.getString("OPEN");

        final Object[] messageArguments = {param1};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return OPEN_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ManagementConsole message of the Format:
     * <pre>MNG-1002 : Starting : {0} : Listening on {1} port {2,number,#}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage LISTENING(String param1, String param2, Number param3)
    {
        String rawMessage = _messages.getString("LISTENING");

        final Object[] messageArguments = {param1, param2, param3};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return LISTENING_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ManagementConsole message of the Format:
     * <pre>MNG-1005 : {0} Management Stopped</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STOPPED(String param1)
    {
        String rawMessage = _messages.getString("STOPPED");

        final Object[] messageArguments = {param1};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return STOPPED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ManagementConsole message of the Format:
     * <pre>MNG-1008 : Close : User {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CLOSE(String param1)
    {
        String rawMessage = _messages.getString("CLOSE");

        final Object[] messageArguments = {param1};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return CLOSE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ManagementConsole message of the Format:
     * <pre>MNG-1003 : Shutting down : {0} : port {1,number,#}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage SHUTTING_DOWN(String param1, Number param2)
    {
        String rawMessage = _messages.getString("SHUTTING_DOWN");

        final Object[] messageArguments = {param1, param2};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return SHUTTING_DOWN_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ManagementConsole message of the Format:
     * <pre>MNG-1001 : {0} Management Startup</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STARTUP(String param1)
    {
        String rawMessage = _messages.getString("STARTUP");

        final Object[] messageArguments = {param1};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return STARTUP_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ManagementConsole message of the Format:
     * <pre>MNG-1004 : {0} Management Ready</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage READY(String param1)
    {
        String rawMessage = _messages.getString("READY");

        final Object[] messageArguments = {param1};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return READY_LOG_HIERARCHY;
            }
        };
    }


    private ManagementConsoleMessages()
    {
    }

}
