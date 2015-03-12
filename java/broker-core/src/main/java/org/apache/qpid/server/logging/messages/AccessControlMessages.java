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
 * This file is based on the content of AccessControl_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class AccessControlMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String ACCESSCONTROL_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "accesscontrol";
    public static final String DENIED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "accesscontrol.denied";
    public static final String ALLOWED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "accesscontrol.allowed";

    static
    {
        LoggerFactory.getLogger(ACCESSCONTROL_LOG_HIERARCHY);
        LoggerFactory.getLogger(DENIED_LOG_HIERARCHY);
        LoggerFactory.getLogger(ALLOWED_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.AccessControl_logmessages", _currentLocale);
    }

    /**
     * Log a AccessControl message of the Format:
     * <pre>ACL-1002 : Denied : {0} {1} {2}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DENIED(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("DENIED");

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
                return DENIED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a AccessControl message of the Format:
     * <pre>ACL-1001 : Allowed : {0} {1} {2}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage ALLOWED(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("ALLOWED");

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
                return ALLOWED_LOG_HIERARCHY;
            }
        };
    }


    private AccessControlMessages()
    {
    }

}
