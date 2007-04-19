/* Copyright Rupert Smith, 2005 to 2006, all rights reserved. */
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.management.ui;

import static org.apache.qpid.management.ui.Constants.CONNECTION_PROTOCOLS;

/**
 * Contains constants for the application
 * @author Bhupendra Bhardwaj
 *
 */
public class Constants
{
    public static final String APPLICATION_NAME = "Qpid Management Console";

    public static final String ACTION_REMOVE_MBEANNODE = "Remove from list";
    public static final String VALUE = "value";
    public static final String TYPE = "type";
    public static final String NODE_TYPE_SERVER = "server";
    public static final String NODE_TYPE_DOMAIN = "domain";
    public static final String NODE_TYPE_MBEANTYPE = "mbeantype";
    // currently used only for virtual host instances, but will work as general also
    public static final String NODE_TYPE_TYPEINSTANCE = "mbeantype_instance";
    public static final String MBEAN = "mbean";
    public static final String ATTRIBUTE = "Attribute";
    public static final String ATTRIBUTES = "Attributes";
    public static final String NOTIFICATIONS = "Notifications";
    public static final String RESULT = "Result";
    public static final String VIRTUAL_HOST = "VirtualHost";
    public static final String DEFAULT_VH = "Default";
    public static final String DEFAULT_USERNAME = "guest";
    public static final String DEFAULT_PASSWORD = "guest";

    public static final String USERNAME = "Username";
    public static final String PASSWORD = "Password";

    // Attributes and operations are used to customize the GUI for Qpid. If these are changes in the
    // Qpid server, then these should be updated accordingly
    public static final String ATTRIBUTE_QUEUE_OWNER = "owner";
    public static final String ATTRIBUTE_QUEUE_DEPTH = "QueueDepth";
    public static final String ATTRIBUTE_QUEUE_CONSUMERCOUNT = "ActiveConsumerCount";
    public static final String OPERATION_CREATE_QUEUE = "createNewQueue";
    public static final String OPERATION_CREATE_BINDING = "createNewBinding";
    public static final String OPERATION_MOVE_MESSAGES = "moveMessages";

    public static final String OPERATION_CREATEUSER = "createUser";
    public static final String OPERATION_VIEWUSERS = "viewUsers";
    public static final String OPERATION_PARAM_USERNAME = "username";

    public static final String OPERATION_SUCCESSFUL = "Operation successful";
    public static final String OPERATION_UNSUCCESSFUL = "Operation unsuccessful";

    public static final String ALL = "All";

    public static final String NAVIGATION_ROOT = "Qpid Connections";
    public static final String DESCRIPTION = " Description";

    public static final String ADMIN_MBEAN_TYPE = "UserManagement";
    public static final String QUEUE = "Queue";
    public static final String CONNECTION = "Connection";
    public static final String EXCHANGE = "Exchange";
    public static final String EXCHANGE_TYPE = "ExchangeType";
    public static final String[] EXCHANGE_TYPE_VALUES = { "direct", "fanout", "headers", "topic" };
    public static final String[] BOOLEAN_TYPE_VALUES = { "false", "true" };
    public static final String[] ATTRIBUTE_TABLE_TITLES = { "Attribute Name", "Value" };
    public static final String[] CONNECTION_PROTOCOLS = { "RMI" };
    public static final String DEFAULT_PROTOCOL = CONNECTION_PROTOCOLS[0];

    public static final String ACTION_ADDSERVER = "New Connection";
    public static final String ACTION_RECONNECT = "Reconnect";
    public static final String ACTION_LOGIN = "Login";

    public static final String QUEUE_SORT_BY_NAME = "Queue Name";
    public static final String QUEUE_SORT_BY_DEPTH = "Queue Depth";
    public static final String QUEUE_SORT_BY_CONSUMERCOUNT = "Consumer Count";
    public static final String QUEUE_SHOW_TEMP_QUEUES = "show temporary queues";

    public static final String SUBSCRIBE_BUTTON = "Subscribe";
    public static final String UNSUBSCRIBE_BUTTON = "Unsubscribe";

    public static final String CONSOLE_IMAGE = "ConsoelImage";
    public static final String CLOSED_FOLDER_IMAGE = "ClosedFolderImage";
    public static final String OPEN_FOLDER_IMAGE = "OpenFolderImage";
    public static final String MBEAN_IMAGE = "MBeanImage";
    public static final String NOTIFICATION_IMAGE = "NotificationImage";

    public static final String FONT_BUTTON = "ButtonFont";
    public static final String FONT_BOLD = "BoldFont";
    public static final String FONT_ITALIC = "ItalicFont";
    public static final String FONT_TABLE_CELL = "TableCellFont";
    public static final String FONT_NORMAL = "Normal";

    public static final String BUTTON_DETAILS = "Details";
    public static final String BUTTON_EDIT_ATTRIBUTE = "Edit Attribute";
    public static final String BUTTON_REFRESH = "Refresh";
    public static final String BUTTON_GRAPH = "Graph";
    public static final int TIMER_INTERVAL = 5000;
    public static final String BUTTON_EXECUTE = "Execute";
    public static final String BUTTON_CLEAR = "Clear";
    public static final String BUTTON_CONNECT = "Connect";
    public static final String BUTTON_CANCEL = "Cancel";
    public static final String BUTTON_UPDATE = "Update";

    public static final int OPERATION_IMPACT_INFO = 0;
    public static final int OPERATION_IMPACT_ACTION = 1;
    public static final int OPERATION_IMPACT_ACTIONINFO = 2;
    public static final int OPERATION_IMPACT_UNKNOWN = 3;

    public static final String ERROR_SERVER_CONNECTION = "Server could not be connected";
    public static final String INFO_PROTOCOL = "Please select the protocol";
    public static final String INFO_HOST_ADDRESS = "Please enter the host address";
    public static final String INFO_HOST_PORT = "Please enter the port number";
    public static final String INFO_USERNAME = "Please enter the " + USERNAME;
    public static final String INFO_PASSWORD = "Please enter the " + PASSWORD;

    public static final String MECH_CRAMMD5 = "CRAM-MD5";
    public static final String MECH_PLAIN = "PLAIN";
    public static final String SASL_CRAMMD5 = "SASL/CRAM-MD5";
    public static final String SASL_PLAIN = "SASL/PLAIN";
}
