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
 * This file is based on the content of Subscription_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class SubscriptionMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String SUBSCRIPTION_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "subscription";
    public static final String STATE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "subscription.state";
    public static final String CREATE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "subscription.create";
    public static final String CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "subscription.close";

    static
    {
        LoggerFactory.getLogger(SUBSCRIPTION_LOG_HIERARCHY);
        LoggerFactory.getLogger(STATE_LOG_HIERARCHY);
        LoggerFactory.getLogger(CREATE_LOG_HIERARCHY);
        LoggerFactory.getLogger(CLOSE_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Subscription_logmessages", _currentLocale);
    }

    /**
     * Log a Subscription message of the Format:
     * <pre>SUB-1003 : State : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STATE(String param1)
    {
        String rawMessage = _messages.getString("STATE");

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
                return STATE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Subscription message of the Format:
     * <pre>SUB-1001 : Create[ : Durable][ : Arguments : {0}]</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CREATE(String param1, boolean opt1, boolean opt2)
    {
        String rawMessage = _messages.getString("CREATE");
        StringBuffer msg = new StringBuffer();

        // Split the formatted message up on the option values so we can
        // rebuild the message based on the configured options.
        String[] parts = rawMessage.split("\\[");
        msg.append(parts[0]);

        int end;
        if (parts.length > 1)
        {

            // Add Option : : Durable.
            end = parts[1].indexOf(']');
            if (opt1)
            {
                msg.append(parts[1].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[1].substring(end + 1));

            // Add Option : : Arguments : {0}.
            end = parts[2].indexOf(']');
            if (opt2)
            {
                msg.append(parts[2].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[2].substring(end + 1));
        }

        rawMessage = msg.toString();

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
                return CREATE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Subscription message of the Format:
     * <pre>SUB-1002 : Close</pre>
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


    private SubscriptionMessages()
    {
    }

}
