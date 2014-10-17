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
 * This file is based on the content of Port_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class PortMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String PORT_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "port";
    public static final String OPEN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "port.open";
    public static final String CREATE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "port.create";
    public static final String CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "port.close";
    public static final String CONNECTION_REJECTED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "port.connection_rejected";
    public static final String CONNECTION_COUNT_WARN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "port.connection_count_warn";

    static
    {
        Logger.getLogger(PORT_LOG_HIERARCHY);
        Logger.getLogger(OPEN_LOG_HIERARCHY);
        Logger.getLogger(CREATE_LOG_HIERARCHY);
        Logger.getLogger(CLOSE_LOG_HIERARCHY);
        Logger.getLogger(CONNECTION_REJECTED_LOG_HIERARCHY);
        Logger.getLogger(CONNECTION_COUNT_WARN_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Port_logmessages", _currentLocale);
    }

    /**
     * Log a Port message of the Format:
     * <pre>PRT-1002 : Open</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage OPEN()
    {
        String rawMessage = _messages.getString("OPEN");

        final String message = rawMessage;

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
     * Log a Port message of the Format:
     * <pre>PRT-1001 : Create</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CREATE()
    {
        String rawMessage = _messages.getString("CREATE");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return CREATE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Port message of the Format:
     * <pre>PRT-1003 : Close</pre>
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
     * Log a Port message of the Format:
     * <pre>PRT-1005 : Connection from {0} reject as connection limit reached</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CONNECTION_REJECTED(String param1)
    {
        String rawMessage = _messages.getString("CONNECTION_REJECTED");

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
                return CONNECTION_REJECTED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Port message of the Format:
     * <pre>PRT-1004 : Connection count {0,number} within {1, number} % of maximum {2,number}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CONNECTION_COUNT_WARN(Number param1, Number param2, Number param3)
    {
        String rawMessage = _messages.getString("CONNECTION_COUNT_WARN");

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
                return CONNECTION_COUNT_WARN_LOG_HIERARCHY;
            }
        };
    }


    private PortMessages()
    {
    }

}
