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
package org.apache.qpid.management.common.mbeans;

import java.io.IOException;

import javax.management.MBeanOperationInfo;

import org.apache.qpid.management.common.mbeans.annotations.MBeanAttribute;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;

/**
 * Interface for the ServerInformation MBean
 * 
 * @version Qpid JMX API 2.3
 * @since Qpid JMX API 1.3
 */
public interface ServerInformation
{
    String TYPE = "ServerInformation";
    
    /* API version info for the brokers JMX management interface
     * 
     *  As the ServerInformation MBean was only introduced in Qpid JMX API 1.3 it is not possible to use it
     *  for identifying earlier broker JMX APIs for compatibility purposes. However, this can be accomplished 
     *  by inspecting the 'version' property key of the UserManagement MBean JMX ObjectName. This was first 
     *  introduced in Qpid JMX API 1.2 and so if present in the absence of the ServerInformation MBean 
     *  indicates that version. If it is not present then a null value will be returned upon inspection and 
     *  Qpid JMX API 1.1 can be assumed.
     */
    int QPID_JMX_API_MAJOR_VERSION = 2;
    int QPID_JMX_API_MINOR_VERSION = 3;
    
    
    /**
     * Attribute to represent the major version number for the management API.
     * @return The major management version number.
     * @since Qpid JMX API 1.3
     */
    @MBeanAttribute(name="ManagementApiMajorVersion", 
                    description = "The major version number for the broker management API")
    Integer getManagementApiMajorVersion() throws IOException;
    
    /**
     * Attribute to represent the minor version number for the management API.
     * @return The minor management version number.
     * @since Qpid JMX API 1.3
     */
    @MBeanAttribute(name="ManagementApiMinorVersion", 
                    description = "The minor version number for the broker management API")
    Integer getManagementApiMinorVersion() throws IOException;
    
    /**
     * Attribute to represent the build version string.
     * @return The build version string
     * @since Qpid JMX API 1.3
     */
    @MBeanAttribute(name="BuildVersion", 
                    description = "The repository build version string")
    String getBuildVersion() throws IOException;
    
    /**
     * Attribute to represent the product version string.
     * @return The product version string
     * @since Qpid JMX API 1.3
     */
    @MBeanAttribute(name="ProductVersion", 
                    description = "The product version string")
    String getProductVersion() throws IOException;
    
    /**
     * Resets all message and data statistics for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanOperation(name="resetStatistics",
                    description="Resets all message and data statistics for the broker",
                    impact= MBeanOperationInfo.ACTION)
    void resetStatistics() throws Exception;

    /**
     * Peak rate of messages delivered per second for the virtual host.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="PeakMessageDeliveryRate", description=TYPE + " Peak Message Delivery Rate")
    double getPeakMessageDeliveryRate();

    /**
     * Peak rate of bytes delivered per second for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="PeakDataDeliveryRate", description=TYPE + " Peak Data Delivery Rate")
    double getPeakDataDeliveryRate();

    /**
     * Rate of messages delivered per second for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="MessageDeliveryRate", description=TYPE + " Message Delivery Rate")
    double getMessageDeliveryRate();

    /**
     * Rate of bytes delivered per second for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="DataDeliveryRate", description=TYPE + " Data Delivery Rate")
    double getDataDeliveryRate();

    /**
     * Total count of messages delivered for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="TotalMessagesDelivered", description=TYPE + " Total Messages Delivered")
    long getTotalMessagesDelivered();

    /**
     * Total count of bytes for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="TotalDataDelivered", description=TYPE + " Total Data Delivered")
    long getTotalDataDelivered();

    /**
     * Peak rate of messages received per second for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="PeakMessageReceiptRate", description=TYPE + " Peak Message Receipt Rate")
    double getPeakMessageReceiptRate();

    /**
     * Peak rate of bytes received per second for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="PeakDataReceiptRate", description=TYPE + " Peak Data Receipt Rate")
    double getPeakDataReceiptRate();

    /**
     * Rate of messages received per second for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="MessageReceiptRate", description=TYPE + " Message Receipt Rate")
    double getMessageReceiptRate();

    /**
     * Rate of bytes received per second for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="DataReceiptRate", description=TYPE + " Data Receipt Rate")
    double getDataReceiptRate();

    /**
     * Total count of messages received for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="TotalMessagesReceived", description=TYPE + " Total Messages Received")
    long getTotalMessagesReceived();

    /**
     * Total count of bytes received for the broker.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="TotalDataReceived", description=TYPE + " Total Data Received")
    long getTotalDataReceived();

    /**
     * Is statistics collection enabled for this connection.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="StatisticsEnabled", description=TYPE + " Statistics Enabled")
    boolean isStatisticsEnabled();
}
