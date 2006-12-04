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

public class Constants
{
    public final static String APPLICATION_NAME = "Qpid Management Console";
    public final static String ITEM_VALUE = "value";
    public final static String ITEM_TYPE  = "type";
    public final static String SERVER     = "server";
    public final static String DOMAIN     = "domain";
    public final static String TYPE       = "mbeantype";
    public final static String MBEAN      = "mbean";
    public final static String ATTRIBUTES = "Attributes";
    public final static String NOTIFICATION = "Notifications";
    
    public final static String ALL = "All";
    
    public final static String NAVIGATION_ROOT = "Qpid Connections";
    public final static String DESCRIPTION = " Description : ";
    
    public final static String BROKER_MANAGER = "Broker_Manager";
    public final static String QUEUE  = "Queue";
    public final static String EXCHANGE = "Exchange";
    public final static String EXCHANGE_TYPE = "ExchangeType";
    public final static String[] EXCHANGE_TYPE_VALUES = {"direct", "topic", "headers"};
    public final static String CONNECTION ="Connection";
    
    public final static String ACTION_ADDSERVER = "New Connection";
    
    
    public final static String SUBSCRIBE_BUTTON   = "Subscribe";
    public final static String UNSUBSCRIBE_BUTTON = "Unsubscribe";
    
    public final static String CONSOLE_IMAGE = "ConsoelImage";
    public final static String CLOSED_FOLDER_IMAGE = "ClosedFolderImage";
    public final static String OPEN_FOLDER_IMAGE = "OpenFolderImage";
    public final static String MBEAN_IMAGE = "MBeanImage";
    public final static String NOTIFICATION_IMAGE = "NotificationImage";
    
    public final static String FONT_BUTTON = "ButtonFont";
    public final static String FONT_BOLD = "BoldFont";
    public final static String FONT_ITALIC = "ItalicFont";
    public final static String FONT_TABLE_CELL = "TableCellFont";
    public final static String FONT_NORMAL = "Normal";
    
    public final static String BUTTON_DETAILS = "Details";
    public final static String BUTTON_EDIT_ATTRIBUTE = "Edit Attribute";
    public final static String BUTTON_REFRESH = "Refresh";
    public final static String BUTTON_GRAPH = "Graph";
    public final static int TIMER_INTERVAL = 5000;
    public final static String BUTTON_EXECUTE = "Execute";
    public final static String BUTTON_CLEAR = "Clear";
    public final static String BUTTON_CONNECT = "Connect";
    public final static String BUTTON_CANCEL = "Cancel";
    
    public final static int OPERATION_IMPACT_INFO    = 0;
    public final static int OPERATION_IMPACT_ACTION  = 1;
    public final static int OPERATION_IMPACT_ACTIONINFO  = 2;
    public final static int OPERATION_IMPACT_UNKNOWN = 3;
}
