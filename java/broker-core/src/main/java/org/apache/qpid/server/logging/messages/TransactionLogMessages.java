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
 * This file is based on the content of TransactionLog_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class TransactionLogMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String TRANSACTIONLOG_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog";
    public static final String CLOSED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.closed";
    public static final String RECOVERY_COMPLETE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.recovery_complete";
    public static final String XA_INCOMPLETE_MESSAGE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.xa_incomplete_message";
    public static final String XA_INCOMPLETE_QUEUE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.xa_incomplete_queue";
    public static final String RECOVERED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.recovered";
    public static final String CREATED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.created";
    public static final String STORE_LOCATION_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.store_location";
    public static final String RECOVERY_START_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "transactionlog.recovery_start";

    static
    {
        LoggerFactory.getLogger(TRANSACTIONLOG_LOG_HIERARCHY);
        LoggerFactory.getLogger(CLOSED_LOG_HIERARCHY);
        LoggerFactory.getLogger(RECOVERY_COMPLETE_LOG_HIERARCHY);
        LoggerFactory.getLogger(XA_INCOMPLETE_MESSAGE_LOG_HIERARCHY);
        LoggerFactory.getLogger(XA_INCOMPLETE_QUEUE_LOG_HIERARCHY);
        LoggerFactory.getLogger(RECOVERED_LOG_HIERARCHY);
        LoggerFactory.getLogger(CREATED_LOG_HIERARCHY);
        LoggerFactory.getLogger(STORE_LOCATION_LOG_HIERARCHY);
        LoggerFactory.getLogger(RECOVERY_START_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.TransactionLog_logmessages", _currentLocale);
    }

    /**
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1003 : Closed</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CLOSED()
    {
        String rawMessage = _messages.getString("CLOSED");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return CLOSED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1006 : Recovery Complete[ : {0}]</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage RECOVERY_COMPLETE(String param1, boolean opt1)
    {
        String rawMessage = _messages.getString("RECOVERY_COMPLETE");
        StringBuffer msg = new StringBuffer();

        // Split the formatted message up on the option values so we can
        // rebuild the message based on the configured options.
        String[] parts = rawMessage.split("\\[");
        msg.append(parts[0]);

        int end;
        if (parts.length > 1)
        {

            // Add Option : : {0}.
            end = parts[1].indexOf(']');
            if (opt1)
            {
                msg.append(parts[1].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[1].substring(end + 1));
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
                return RECOVERY_COMPLETE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1008 : XA transaction recover for xid {0} incomplete as it references a message {1} which was not durably retained</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage XA_INCOMPLETE_MESSAGE(String param1, String param2)
    {
        String rawMessage = _messages.getString("XA_INCOMPLETE_MESSAGE");

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
                return XA_INCOMPLETE_MESSAGE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1007 : XA transaction recover for xid {0} incomplete as it references a queue {1} which was not durably retained</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage XA_INCOMPLETE_QUEUE(String param1, String param2)
    {
        String rawMessage = _messages.getString("XA_INCOMPLETE_QUEUE");

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
                return XA_INCOMPLETE_QUEUE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1005 : Recovered {0,number} messages for queue {1}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage RECOVERED(Number param1, String param2)
    {
        String rawMessage = _messages.getString("RECOVERED");

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
                return RECOVERED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1001 : Created</pre>
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
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1002 : Store location : {0}</pre>
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
     * Log a TransactionLog message of the Format:
     * <pre>TXN-1004 : Recovery Start[ : {0}]</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage RECOVERY_START(String param1, boolean opt1)
    {
        String rawMessage = _messages.getString("RECOVERY_START");
        StringBuffer msg = new StringBuffer();

        // Split the formatted message up on the option values so we can
        // rebuild the message based on the configured options.
        String[] parts = rawMessage.split("\\[");
        msg.append(parts[0]);

        int end;
        if (parts.length > 1)
        {

            // Add Option : : {0}.
            end = parts[1].indexOf(']');
            if (opt1)
            {
                msg.append(parts[1].substring(0, end));
            }

            // Use 'end + 1' to remove the ']' from the output
            msg.append(parts[1].substring(end + 1));
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
                return RECOVERY_START_LOG_HIERARCHY;
            }
        };
    }


    private TransactionLogMessages()
    {
    }

}
