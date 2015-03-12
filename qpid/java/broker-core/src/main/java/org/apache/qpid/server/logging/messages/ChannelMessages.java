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
 * This file is based on the content of Channel_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class ChannelMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String CHANNEL_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel";
    public static final String FLOW_ENFORCED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.flow_enforced";
    public static final String CREATE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.create";
    public static final String FLOW_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.flow";
    public static final String CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.close";
    public static final String PREFETCH_SIZE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.prefetch_size";
    public static final String CLOSE_FORCED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.close_forced";
    public static final String LARGE_TRANSACTION_WARN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.large_transaction_warn";
    public static final String DEADLETTERMSG_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.deadlettermsg";
    public static final String DISCARDMSG_NOALTEXCH_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.discardmsg_noaltexch";
    public static final String IDLE_TXN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.idle_txn";
    public static final String FLOW_CONTROL_IGNORED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.flow_control_ignored";
    public static final String DISCARDMSG_NOROUTE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.discardmsg_noroute";
    public static final String OPEN_TXN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.open_txn";
    public static final String FLOW_REMOVED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "channel.flow_removed";

    static
    {
        LoggerFactory.getLogger(CHANNEL_LOG_HIERARCHY);
        LoggerFactory.getLogger(FLOW_ENFORCED_LOG_HIERARCHY);
        LoggerFactory.getLogger(CREATE_LOG_HIERARCHY);
        LoggerFactory.getLogger(FLOW_LOG_HIERARCHY);
        LoggerFactory.getLogger(CLOSE_LOG_HIERARCHY);
        LoggerFactory.getLogger(PREFETCH_SIZE_LOG_HIERARCHY);
        LoggerFactory.getLogger(CLOSE_FORCED_LOG_HIERARCHY);
        LoggerFactory.getLogger(LARGE_TRANSACTION_WARN_LOG_HIERARCHY);
        LoggerFactory.getLogger(DEADLETTERMSG_LOG_HIERARCHY);
        LoggerFactory.getLogger(DISCARDMSG_NOALTEXCH_LOG_HIERARCHY);
        LoggerFactory.getLogger(IDLE_TXN_LOG_HIERARCHY);
        LoggerFactory.getLogger(FLOW_CONTROL_IGNORED_LOG_HIERARCHY);
        LoggerFactory.getLogger(DISCARDMSG_NOROUTE_LOG_HIERARCHY);
        LoggerFactory.getLogger(OPEN_TXN_LOG_HIERARCHY);
        LoggerFactory.getLogger(FLOW_REMOVED_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Channel_logmessages", _currentLocale);
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1005 : Flow Control Enforced (Queue {0})</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage FLOW_ENFORCED(String param1)
    {
        String rawMessage = _messages.getString("FLOW_ENFORCED");

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
                return FLOW_ENFORCED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1001 : Create</pre>
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
     * Log a Channel message of the Format:
     * <pre>CHN-1002 : Flow {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage FLOW(String param1)
    {
        String rawMessage = _messages.getString("FLOW");

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
                return FLOW_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1003 : Close</pre>
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
     * Log a Channel message of the Format:
     * <pre>CHN-1004 : Prefetch Size (bytes) {0,number} : Count {1,number}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage PREFETCH_SIZE(Number param1, Number param2)
    {
        String rawMessage = _messages.getString("PREFETCH_SIZE");

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
                return PREFETCH_SIZE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1003 : Close : {0,number} - {1}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CLOSE_FORCED(Number param1, String param2)
    {
        String rawMessage = _messages.getString("CLOSE_FORCED");

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
                return CLOSE_FORCED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1013 : Uncommitted transaction contains {0,number} bytes of incoming message data.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage LARGE_TRANSACTION_WARN(Number param1)
    {
        String rawMessage = _messages.getString("LARGE_TRANSACTION_WARN");

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
                return LARGE_TRANSACTION_WARN_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1011 : Message : {0,number} moved to dead letter queue : {1}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DEADLETTERMSG(Number param1, String param2)
    {
        String rawMessage = _messages.getString("DEADLETTERMSG");

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
                return DEADLETTERMSG_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1009 : Discarded message : {0,number} as no alternate exchange configured for queue : {1} routing key : {2}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DISCARDMSG_NOALTEXCH(Number param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("DISCARDMSG_NOALTEXCH");

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
                return DISCARDMSG_NOALTEXCH_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1008 : Idle Transaction : {0,number} ms</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage IDLE_TXN(Number param1)
    {
        String rawMessage = _messages.getString("IDLE_TXN");

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
                return IDLE_TXN_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1012 : Flow Control Ignored. Channel will be closed.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage FLOW_CONTROL_IGNORED()
    {
        String rawMessage = _messages.getString("FLOW_CONTROL_IGNORED");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return FLOW_CONTROL_IGNORED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1010 : Discarded message : {0,number} as no binding on alternate exchange : {1}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DISCARDMSG_NOROUTE(Number param1, String param2)
    {
        String rawMessage = _messages.getString("DISCARDMSG_NOROUTE");

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
                return DISCARDMSG_NOROUTE_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1007 : Open Transaction : {0,number} ms</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage OPEN_TXN(Number param1)
    {
        String rawMessage = _messages.getString("OPEN_TXN");

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
                return OPEN_TXN_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a Channel message of the Format:
     * <pre>CHN-1006 : Flow Control Removed</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage FLOW_REMOVED()
    {
        String rawMessage = _messages.getString("FLOW_REMOVED");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return FLOW_REMOVED_LOG_HIERARCHY;
            }
        };
    }


    private ChannelMessages()
    {
    }

}
