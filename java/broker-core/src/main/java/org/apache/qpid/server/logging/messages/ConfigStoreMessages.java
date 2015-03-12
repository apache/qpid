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
 * This file is based on the content of ConfigStore_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class ConfigStoreMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String CONFIGSTORE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "configstore";
    public static final String RECOVERY_COMPLETE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "configstore.recovery_complete";
    public static final String CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "configstore.close";
    public static final String CREATED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "configstore.created";
    public static final String STORE_LOCATION_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "configstore.store_location";
    public static final String RECOVERY_START_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "configstore.recovery_start";

    static
    {
        LoggerFactory.getLogger(CONFIGSTORE_LOG_HIERARCHY);
        LoggerFactory.getLogger(RECOVERY_COMPLETE_LOG_HIERARCHY);
        LoggerFactory.getLogger(CLOSE_LOG_HIERARCHY);
        LoggerFactory.getLogger(CREATED_LOG_HIERARCHY);
        LoggerFactory.getLogger(STORE_LOCATION_LOG_HIERARCHY);
        LoggerFactory.getLogger(RECOVERY_START_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.ConfigStore_logmessages", _currentLocale);
    }

    /**
     * Log a ConfigStore message of the Format:
     * <pre>CFG-1005 : Recovery Complete</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage RECOVERY_COMPLETE()
    {
        String rawMessage = _messages.getString("RECOVERY_COMPLETE");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return RECOVERY_COMPLETE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ConfigStore message of the Format:
     * <pre>CFG-1003 : Closed</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CLOSE()
    {
        String rawMessage = _messages.getString("CLOSE");

        final String message = rawMessage;

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
     * Log a ConfigStore message of the Format:
     * <pre>CFG-1001 : Created</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CREATED()
    {
        String rawMessage = _messages.getString("CREATED");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return CREATED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ConfigStore message of the Format:
     * <pre>CFG-1002 : Store location : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STORE_LOCATION(String param1)
    {
        String rawMessage = _messages.getString("STORE_LOCATION");

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
                return STORE_LOCATION_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a ConfigStore message of the Format:
     * <pre>CFG-1004 : Recovery Start</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage RECOVERY_START()
    {
        String rawMessage = _messages.getString("RECOVERY_START");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return RECOVERY_START_LOG_HIERARCHY;
            }
        };
    }


    private ConfigStoreMessages()
    {
    }

}
