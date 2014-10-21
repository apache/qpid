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
 * This file is based on the content of HighAvailability_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class HighAvailabilityMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale = BrokerProperties.getLocale();

    public static final String HIGHAVAILABILITY_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability";
    public static final String INTRUDER_DETECTED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.intruder_detected";
    public static final String TRANSFER_MASTER_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.transfer_master";
    public static final String QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.quorum_override_changed";
    public static final String REMOVED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.removed";
    public static final String LEFT_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.left";
    public static final String JOINED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.joined";
    public static final String CREATED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.created";
    public static final String QUORUM_LOST_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.quorum_lost";
    public static final String PRIORITY_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.priority_changed";
    public static final String ADDED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.added";
    public static final String DELETED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.deleted";
    public static final String ROLE_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.role_changed";
    public static final String DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.designated_primary_changed";
    public static final String NODE_ROLLEDBACK_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.node_rolledback";

    static
    {
        Logger.getLogger(HIGHAVAILABILITY_LOG_HIERARCHY);
        Logger.getLogger(INTRUDER_DETECTED_LOG_HIERARCHY);
        Logger.getLogger(TRANSFER_MASTER_LOG_HIERARCHY);
        Logger.getLogger(QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY);
        Logger.getLogger(REMOVED_LOG_HIERARCHY);
        Logger.getLogger(LEFT_LOG_HIERARCHY);
        Logger.getLogger(JOINED_LOG_HIERARCHY);
        Logger.getLogger(CREATED_LOG_HIERARCHY);
        Logger.getLogger(QUORUM_LOST_LOG_HIERARCHY);
        Logger.getLogger(PRIORITY_CHANGED_LOG_HIERARCHY);
        Logger.getLogger(ADDED_LOG_HIERARCHY);
        Logger.getLogger(DELETED_LOG_HIERARCHY);
        Logger.getLogger(ROLE_CHANGED_LOG_HIERARCHY);
        Logger.getLogger(DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY);
        Logger.getLogger(NODE_ROLLEDBACK_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.HighAvailability_logmessages", _currentLocale);
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1008 : Intruder detected : Node ''{0}'' ({1})</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage INTRUDER_DETECTED(String param1, String param2)
    {
        String rawMessage = _messages.getString("INTRUDER_DETECTED");

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
                return INTRUDER_DETECTED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1007 : Master transfer requested : to ''{0}'' ({1})</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage TRANSFER_MASTER(String param1, String param2)
    {
        String rawMessage = _messages.getString("TRANSFER_MASTER");

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
                return TRANSFER_MASTER_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1011 : Minimum group size : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage QUORUM_OVERRIDE_CHANGED(String param1)
    {
        String rawMessage = _messages.getString("QUORUM_OVERRIDE_CHANGED");

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
                return QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1004 : Removed : Node : ''{0}'' ({1})</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage REMOVED(String param1, String param2)
    {
        String rawMessage = _messages.getString("REMOVED");

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
                return REMOVED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1006 : Left : Node : ''{0}'' ({1})</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage LEFT(String param1, String param2)
    {
        String rawMessage = _messages.getString("LEFT");

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
                return LEFT_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1005 : Joined : Node : ''{0}'' ({1})</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage JOINED(String param1, String param2)
    {
        String rawMessage = _messages.getString("JOINED");

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
                return JOINED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1001 : Created</pre>
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
     * Log a HighAvailability message of the Format:
     * <pre>HA-1009 : Insufficient replicas contactable</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage QUORUM_LOST()
    {
        String rawMessage = _messages.getString("QUORUM_LOST");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return QUORUM_LOST_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1012 : Priority : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage PRIORITY_CHANGED(String param1)
    {
        String rawMessage = _messages.getString("PRIORITY_CHANGED");

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
                return PRIORITY_CHANGED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1003 : Added : Node : ''{0}'' ({1})</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage ADDED(String param1, String param2)
    {
        String rawMessage = _messages.getString("ADDED");

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
                return ADDED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1002 : Deleted</pre>
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

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1010 : Role change reported: Node : ''{0}'' ({1}) : from ''{2}'' to ''{3}''</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage ROLE_CHANGED(String param1, String param2, String param3, String param4)
    {
        String rawMessage = _messages.getString("ROLE_CHANGED");

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
                return ROLE_CHANGED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1013 : Designated primary : {0}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DESIGNATED_PRIMARY_CHANGED(String param1)
    {
        String rawMessage = _messages.getString("DESIGNATED_PRIMARY_CHANGED");

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
                return DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1014 : Diverged transactions discarded</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage NODE_ROLLEDBACK()
    {
        String rawMessage = _messages.getString("NODE_ROLLEDBACK");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return NODE_ROLLEDBACK_LOG_HIERARCHY;
            }
        };
    }


    private HighAvailabilityMessages()
    {
    }

}
