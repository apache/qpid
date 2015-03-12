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
 * This file is based on the content of Queue_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class QueueMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String QUEUE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "queue";
    public static final String OVERFULL_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "queue.overfull";
    public static final String UNDERFULL_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "queue.underfull";
    public static final String CREATED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "queue.created";
    public static final String DELETED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "queue.deleted";

    static
    {
        LoggerFactory.getLogger(QUEUE_LOG_HIERARCHY);
        LoggerFactory.getLogger(OVERFULL_LOG_HIERARCHY);
        LoggerFactory.getLogger(UNDERFULL_LOG_HIERARCHY);
        LoggerFactory.getLogger(CREATED_LOG_HIERARCHY);
        LoggerFactory.getLogger(DELETED_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Queue_logmessages", _currentLocale);
    }

    /**
     * Log a Queue message of the Format:
     * <pre>QUE-1003 : Overfull : Size : {0,number} bytes, Capacity : {1,number}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage OVERFULL(Number param1, Number param2)
    {
        String rawMessage = _messages.getString("OVERFULL");

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
                return OVERFULL_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Queue message of the Format:
     * <pre>QUE-1004 : Underfull : Size : {0,number} bytes, Resume Capacity : {1,number}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage UNDERFULL(Number param1, Number param2)
    {
        String rawMessage = _messages.getString("UNDERFULL");

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
                return UNDERFULL_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Queue message of the Format:
     * <pre>QUE-1001 : Create :[ Owner: {0}][ AutoDelete][ Durable][ Transient][ Priority: {1,number,#}]</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CREATED(String param1, Number param2, boolean opt1, boolean opt2, boolean opt3, boolean opt4, boolean opt5)
    {
        String rawMessage = _messages.getString("CREATED");
        StringBuffer msg = new StringBuffer();

        // Split the formatted message up on the option values so we can
        // rebuild the message based on the configured options.
        String[] parts = rawMessage.split("\\[");
        msg.append(parts[0]);

        int end;
        if (parts.length > 1)
        {

            // Add Option : Owner: {0}.
            end = parts[1].indexOf(']');
            if (opt1)
            {
                msg.append(parts[1].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[1].substring(end + 1));

            // Add Option : AutoDelete.
            end = parts[2].indexOf(']');
            if (opt2)
            {
                msg.append(parts[2].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[2].substring(end + 1));

            // Add Option : Durable.
            end = parts[3].indexOf(']');
            if (opt3)
            {
                msg.append(parts[3].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[3].substring(end + 1));

            // Add Option : Transient.
            end = parts[4].indexOf(']');
            if (opt4)
            {
                msg.append(parts[4].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[4].substring(end + 1));

            // Add Option : Priority: {1,number,#}.
            end = parts[5].indexOf(']');
            if (opt5)
            {
                msg.append(parts[5].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[5].substring(end + 1));
        }

        rawMessage = msg.toString();

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
                return CREATED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Queue message of the Format:
     * <pre>QUE-1002 : Deleted</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DELETED()
    {
        String rawMessage = _messages.getString("DELETED");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return DELETED_LOG_HIERARCHY;
            }
        };
    }


    private QueueMessages()
    {
    }

}
