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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.logging.LogMessage;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * DO NOT EDIT DIRECTLY, THIS FILE WAS GENERATED.
 *
 * Generated using GenerateLogMessages and LogMessages.vm
 * This file is based on the content of Broker_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class BrokerMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String BROKER_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker";
    public static final String LOG_CONFIG_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.log_config";
    public static final String CONFIG_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.config";
    public static final String STATS_DATA_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.stats_data";
    public static final String STOPPED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.stopped";
    public static final String STATS_MSGS_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.stats_msgs";
    public static final String LISTENING_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.listening";
    public static final String FLOW_TO_DISK_INACTIVE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.flow_to_disk_inactive";
    public static final String FLOW_TO_DISK_ACTIVE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.flow_to_disk_active";
    public static final String MAX_MEMORY_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.max_memory";
    public static final String PLATFORM_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.platform";
    public static final String PROCESS_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.process";
    public static final String SHUTTING_DOWN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.shutting_down";
    public static final String MANAGEMENT_MODE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.management_mode";
    public static final String STARTUP_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.startup";
    public static final String FATAL_ERROR_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.fatal_error";
    public static final String READY_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "broker.ready";

    static
    {
        LoggerFactory.getLogger(BROKER_LOG_HIERARCHY);
        LoggerFactory.getLogger(LOG_CONFIG_LOG_HIERARCHY);
        LoggerFactory.getLogger(CONFIG_LOG_HIERARCHY);
        LoggerFactory.getLogger(STATS_DATA_LOG_HIERARCHY);
        LoggerFactory.getLogger(STOPPED_LOG_HIERARCHY);
        LoggerFactory.getLogger(STATS_MSGS_LOG_HIERARCHY);
        LoggerFactory.getLogger(LISTENING_LOG_HIERARCHY);
        LoggerFactory.getLogger(FLOW_TO_DISK_INACTIVE_LOG_HIERARCHY);
        LoggerFactory.getLogger(FLOW_TO_DISK_ACTIVE_LOG_HIERARCHY);
        LoggerFactory.getLogger(MAX_MEMORY_LOG_HIERARCHY);
        LoggerFactory.getLogger(PLATFORM_LOG_HIERARCHY);
        LoggerFactory.getLogger(PROCESS_LOG_HIERARCHY);
        LoggerFactory.getLogger(SHUTTING_DOWN_LOG_HIERARCHY);
        LoggerFactory.getLogger(MANAGEMENT_MODE_LOG_HIERARCHY);
        LoggerFactory.getLogger(STARTUP_LOG_HIERARCHY);
        LoggerFactory.getLogger(FATAL_ERROR_LOG_HIERARCHY);
        LoggerFactory.getLogger(READY_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Broker_logmessages", _currentLocale);
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1007 : Using logging configuration : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage LOG_CONFIG(String param1)
    {
        String rawMessage = _messages.getString("LOG_CONFIG");

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
                return LOG_CONFIG_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1006 : Using configuration : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CONFIG(String param1)
    {
        String rawMessage = _messages.getString("CONFIG");

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
                return CONFIG_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1008 : {0,choice,0#delivered|1#received} : {1,number,#.###} kB/s peak : {2,number,#} bytes total</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STATS_DATA(Number param1, Number param2, Number param3)
    {
        String rawMessage = _messages.getString("STATS_DATA");

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
                return STATS_DATA_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1005 : Stopped</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STOPPED()
    {
        String rawMessage = _messages.getString("STOPPED");

        final String message = rawMessage;

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
     * Log a Broker message of the Format:
     * <pre>BRK-1009 : {0,choice,0#delivered|1#received} : {1,number,#.###} msg/s peak : {2,number,#} msgs total</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STATS_MSGS(Number param1, Number param2, Number param3)
    {
        String rawMessage = _messages.getString("STATS_MSGS");

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
                return STATS_MSGS_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1002 : Starting : Listening on {0} port {1,number,#}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage LISTENING(String param1, Number param2)
    {
        String rawMessage = _messages.getString("LISTENING");

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
                return LISTENING_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1015 : Message flow to disk inactive : Message memory use {0,number,#}KB within threshold {1,number,#.##}KB</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage FLOW_TO_DISK_INACTIVE(Number param1, Number param2)
    {
        String rawMessage = _messages.getString("FLOW_TO_DISK_INACTIVE");

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
                return FLOW_TO_DISK_INACTIVE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1014 : Message flow to disk active :  Message memory use {0,number,#}KB exceeds threshold {1,number,#.##}KB</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage FLOW_TO_DISK_ACTIVE(Number param1, Number param2)
    {
        String rawMessage = _messages.getString("FLOW_TO_DISK_ACTIVE");

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
                return FLOW_TO_DISK_ACTIVE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1011 : Maximum Memory : {0,number} bytes</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage MAX_MEMORY(Number param1)
    {
        String rawMessage = _messages.getString("MAX_MEMORY");

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
                return MAX_MEMORY_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1010 : Platform : JVM : {0} version: {1} OS : {2} version: {3} arch: {4}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage PLATFORM(String param1, String param2, String param3, String param4, String param5)
    {
        String rawMessage = _messages.getString("PLATFORM");

        final Object[] messageArguments = {param1, param2, param3, param4, param5};
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
                return PLATFORM_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1017 : Process : PID : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage PROCESS(String param1)
    {
        String rawMessage = _messages.getString("PROCESS");

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
                return PROCESS_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1003 : Shutting down : {0} port {1,number,#}</pre>
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
     * Log a Broker message of the Format:
     * <pre>BRK-1012 : Management Mode : User Details : {0} / {1}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage MANAGEMENT_MODE(String param1, String param2)
    {
        String rawMessage = _messages.getString("MANAGEMENT_MODE");

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
                return MANAGEMENT_MODE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1001 : Startup : Version: {0} Build: {1}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STARTUP(String param1, String param2)
    {
        String rawMessage = _messages.getString("STARTUP");

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
                return STARTUP_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1016 : Fatal error : {0} : See log file for more information</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage FATAL_ERROR(String param1)
    {
        String rawMessage = _messages.getString("FATAL_ERROR");

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
                return FATAL_ERROR_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Broker message of the Format:
     * <pre>BRK-1004 : Qpid Broker Ready</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage READY()
    {
        String rawMessage = _messages.getString("READY");

        final String message = rawMessage;

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


    private BrokerMessages()
    {
    }

}
