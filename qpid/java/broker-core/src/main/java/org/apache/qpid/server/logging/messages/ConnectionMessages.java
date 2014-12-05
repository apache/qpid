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
 * This file is based on the content of Connection_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class ConnectionMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String CONNECTION_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "connection";
    public static final String OPEN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "connection.open";
    public static final String DROPPED_CONNECTION_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "connection.dropped_connection";
    public static final String IDLE_CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "connection.idle_close";
    public static final String CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "connection.close";

    static
    {
        Logger.getLogger(CONNECTION_LOG_HIERARCHY);
        Logger.getLogger(OPEN_LOG_HIERARCHY);
        Logger.getLogger(DROPPED_CONNECTION_LOG_HIERARCHY);
        Logger.getLogger(IDLE_CLOSE_LOG_HIERARCHY);
        Logger.getLogger(CLOSE_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Connection_logmessages", _currentLocale);
    }

    /**
     * Log a Connection message of the Format:
     * <pre>CON-1001 : Open[ : Client ID : {0}][ : Protocol Version : {1}][ : Client Version : {2}][ : Client Product : {3}]</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage OPEN(String param1, String param2, String param3, String param4, boolean opt1, boolean opt2, boolean opt3, boolean opt4)
    {
        String rawMessage = _messages.getString("OPEN");
        StringBuffer msg = new StringBuffer();

        // Split the formatted message up on the option values so we can
        // rebuild the message based on the configured options.
        String[] parts = rawMessage.split("\\[");
        msg.append(parts[0]);

        int end;
        if (parts.length > 1)
        {

            // Add Option : : Client ID : {0}.
            end = parts[1].indexOf(']');
            if (opt1)
            {
                msg.append(parts[1].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[1].substring(end + 1));

            // Add Option : : Protocol Version : {1}.
            end = parts[2].indexOf(']');
            if (opt2)
            {
                msg.append(parts[2].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[2].substring(end + 1));

            // Add Option : : Client Version : {2}.
            end = parts[3].indexOf(']');
            if (opt3)
            {
                msg.append(parts[3].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[3].substring(end + 1));

            // Add Option : : Client Product : {3}.
            end = parts[4].indexOf(']');
            if (opt4)
            {
                msg.append(parts[4].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[4].substring(end + 1));
        }

        rawMessage = msg.toString();

        final Object[] messageArguments = {param1, param2, param3, param4};
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
     * Log a Connection message of the Format:
     * <pre>CON-1004 : Connection dropped</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DROPPED_CONNECTION()
    {
        String rawMessage = _messages.getString("DROPPED_CONNECTION");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return DROPPED_CONNECTION_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Connection message of the Format:
     * <pre>CON-1003 : Closed due to inactivity</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage IDLE_CLOSE()
    {
        String rawMessage = _messages.getString("IDLE_CLOSE");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return IDLE_CLOSE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Connection message of the Format:
     * <pre>CON-1002 : Close</pre>
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


    private ConnectionMessages()
    {
    }

}
