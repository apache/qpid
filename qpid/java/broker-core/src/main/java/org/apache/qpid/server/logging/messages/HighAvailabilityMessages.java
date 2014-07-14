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
    public static final String STOPPED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.stopped";
    public static final String INTRUDER_DETECTED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.intruder_detected";
    public static final String STARTED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.started";
    public static final String TRANSFER_MASTER_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.transfer_master";
    public static final String QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.quorum_override_changed";
    public static final String DETACHED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.detached";
    public static final String MAJORITY_LOST_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.majority_lost";
    public static final String PRIORITY_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.priority_changed";
    public static final String ATTACHED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.attached";
    public static final String ADDED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.added";
    public static final String DELETED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.deleted";
    public static final String ROLE_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.role_changed";
    public static final String DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "highavailability.designated_primary_changed";

    static
    {
        Logger.getLogger(HIGHAVAILABILITY_LOG_HIERARCHY);
        Logger.getLogger(STOPPED_LOG_HIERARCHY);
        Logger.getLogger(INTRUDER_DETECTED_LOG_HIERARCHY);
        Logger.getLogger(STARTED_LOG_HIERARCHY);
        Logger.getLogger(TRANSFER_MASTER_LOG_HIERARCHY);
        Logger.getLogger(QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY);
        Logger.getLogger(DETACHED_LOG_HIERARCHY);
        Logger.getLogger(MAJORITY_LOST_LOG_HIERARCHY);
        Logger.getLogger(PRIORITY_CHANGED_LOG_HIERARCHY);
        Logger.getLogger(ATTACHED_LOG_HIERARCHY);
        Logger.getLogger(ADDED_LOG_HIERARCHY);
        Logger.getLogger(DELETED_LOG_HIERARCHY);
        Logger.getLogger(ROLE_CHANGED_LOG_HIERARCHY);
        Logger.getLogger(DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.HighAvailability_logmessages", _currentLocale);
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1012 : The node ''{0}'' from the replication group ''{1}'' is stopped.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STOPPED(String param1, String param2)
    {
        String rawMessage = _messages.getString("STOPPED");

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
                return STOPPED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1007: Intruder node ''{0}'' from ''{1}'' is detected in replication group ''{2}''</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage INTRUDER_DETECTED(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("INTRUDER_DETECTED");

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
                return INTRUDER_DETECTED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1013 : The node ''{0}'' from the replication group ''{1}'' is started.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage STARTED(String param1, String param2)
    {
        String rawMessage = _messages.getString("STARTED");

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
                return STARTED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1014 : Transfer master to ''{0}'' is requested on node ''{1}'' from the replication group ''{2}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage TRANSFER_MASTER(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("TRANSFER_MASTER");

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
                return TRANSFER_MASTER_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1009 : The quorum override attribute of node ''{0}'' from the replication group ''{1}'' is set to ''{2}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage QUORUM_OVERRIDE_CHANGED(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("QUORUM_OVERRIDE_CHANGED");

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
                return QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1003 : The node ''{0}'' detached from the replication group ''{1}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DETACHED(String param1, String param2)
    {
        String rawMessage = _messages.getString("DETACHED");

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
                return DETACHED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1006 : A majority of nodes from replication group ''{0}'' is not available for node ''{1}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage MAJORITY_LOST(String param1, String param2)
    {
        String rawMessage = _messages.getString("MAJORITY_LOST");

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
                return MAJORITY_LOST_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1008 : The priority attribute of node ''{0}'' from the replication group ''{1}'' is set to ''{2}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage PRIORITY_CHANGED(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("PRIORITY_CHANGED");

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
                return PRIORITY_CHANGED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1004 : The node ''{0}'' attached to the replication group ''{1}'' with role ''{2}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage ATTACHED(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("ATTACHED");

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
                return ATTACHED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1001 : A new node ''{0}'' is added into a replication group ''{1}''.</pre>
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
     * <pre>HA-1002 : An existing node ''{0}'' is removed from the replication group ''{1}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DELETED(String param1, String param2)
    {
        String rawMessage = _messages.getString("DELETED");

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
                return DELETED_LOG_HIERARCHY;
            }
        };
    }

    /**
     * Log a HighAvailability message of the Format:
     * <pre>HA-1005 : The role of the node ''{0}'' from the replication group ''{1}'' has changed from ''{2}'' to ''{3}''.</pre>
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
     * <pre>HA-1010 : The designated primary attribute of node ''{0}'' from the replication group ''{1}'' is set to ''{2}''.</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DESIGNATED_PRIMARY_CHANGED(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("DESIGNATED_PRIMARY_CHANGED");

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
                return DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY;
            }
        };
    }


    private HighAvailabilityMessages()
    {
    }

}
