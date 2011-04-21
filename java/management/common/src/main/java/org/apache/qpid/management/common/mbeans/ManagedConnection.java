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

package org.apache.qpid.management.common.mbeans;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.management.JMException;
import javax.management.MBeanOperationInfo;
import javax.management.openmbean.TabularData;

import org.apache.qpid.management.common.mbeans.annotations.MBeanAttribute;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;

/**
 * The management interface exposed to allow management of Connections.
 * 
 * @version Qpid JMX API 2.2
 * @since Qpid JMX API 1.3
 */
public interface ManagedConnection
{
    static final String TYPE = "Connection";
    
    //TabularType and contained CompositeType key/description information
    //For compatibility reasons, DONT MODIFY the existing key values if expanding the set. 
    //"Flow Blocked" added in Qpid JMX API 1.5
    String CHAN_ID = "Channel Id";
    String TRANSACTIONAL = "Transactional";
    String DEFAULT_QUEUE = "Default Queue";
    String UNACKED_COUNT = "Unacknowledged Message Count";
    String FLOW_BLOCKED = "Flow Blocked";
    List<String> COMPOSITE_ITEM_NAMES_DESC = Collections.unmodifiableList(Arrays.asList(CHAN_ID, TRANSACTIONAL, DEFAULT_QUEUE, UNACKED_COUNT, FLOW_BLOCKED));
    List<String> TABULAR_UNIQUE_INDEX = Collections.unmodifiableList(Arrays.asList(CHAN_ID));

    @MBeanAttribute(name = "ClientId", description = "Client Id")
    String getClientId();

    @MBeanAttribute(name = "AuthorizedId", description = "User Name")
    String getAuthorizedId();

    @MBeanAttribute(name = "Version", description = "Client Version")
    String getVersion();

    /**
     * Tells the remote address of this connection.
     * @return  remote address
     */
    @MBeanAttribute(name="RemoteAddress", description=TYPE + " Address")
    String getRemoteAddress();

    /**
     * Tells the last time, the IO operation was done.
     * @return last IO time.
     */
    @MBeanAttribute(name="LastIOTime", description="The last time, the IO operation was done")
    Date getLastIoTime();

    /**
     * Tells the total number of bytes written till now.
     * @return number of bytes written.
     *
    @MBeanAttribute(name="WrittenBytes", description="The total number of bytes written till now")
    Long getWrittenBytes();
    */
    /**
     * Tells the total number of bytes read till now.
     * @return number of bytes read.
     *
    @MBeanAttribute(name="ReadBytes", description="The total number of bytes read till now")
    Long getReadBytes();
    */

    /**
     * Threshold high value for no of channels.  This is useful in setting notifications or
     * taking required action is there are more channels being created.
     * @return threshold limit for no of channels
     */
    Long getMaximumNumberOfChannels();

    /**
     * Sets the threshold high value for number of channels for a connection
     * @param value
     */
    @MBeanAttribute(name="MaximumNumberOfChannels", description="The threshold high value for number of channels for this connection")
    void setMaximumNumberOfChannels(Long value);

    //********** Operations *****************//

    /**
     * channel details of all the channels opened for this connection.
     * @return general channel details
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="channels", description="Channel details for this connection")
    TabularData channels() throws IOException, JMException;

    /**
     * Commits the transactions if the channel is transactional.
     * @param channelId
     * @throws JMException
     */
    @MBeanOperation(name="commitTransaction",
                    description="Commits the transactions for given channel Id, if the channel is transactional",
                    impact= MBeanOperationInfo.ACTION)
    void commitTransactions(@MBeanOperationParameter(name="channel Id", description="channel Id")int channelId) throws JMException;

    /**
     * Rollsback the transactions if the channel is transactional.
     * @param channelId
     * @throws JMException
     */
    @MBeanOperation(name="rollbackTransactions",
                    description="Rollsback the transactions for given channel Id, if the channel is transactional",
                    impact= MBeanOperationInfo.ACTION)
    void rollbackTransactions(@MBeanOperationParameter(name="channel Id", description="channel Id")int channelId) throws JMException;

    /**
     * Closes all the related channels and unregisters this connection from managed objects.
     */
    @MBeanOperation(name="closeConnection",
                    description="Closes this connection and all related channels",
                    impact= MBeanOperationInfo.ACTION)
    void closeConnection() throws Exception;
 
    /**
     * Resets message and data statistics for this connection.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanOperation(name="resetStatistics",
                    description="Resets message and data statistics for this connection",
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
     * Peak rate of bytes delivered per second for the virtual host.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="PeakDataDeliveryRate", description=TYPE + " Peak Data Delivery Rate")
    double getPeakDataDeliveryRate();

    /**
     * Rate of messages delivered per second for the virtual host.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="MessageDeliveryRate", description=TYPE + " Message Delivery Rate")
    double getMessageDeliveryRate();

    /**
     * Rate of bytes delivered per second for the virtual host.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="DataDeliveryRate", description=TYPE + " Data Delivery Rate")
    double getDataDeliveryRate();

    /**
     * Total count of messages delivered for the virtual host.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="TotalMessagesDelivered", description=TYPE + " Total Messages Delivered")
    long getTotalMessagesDelivered();

    /**
     * Total count of bytes for the virtual host.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="TotalDataDelivered", description=TYPE + " Total Data Delivered")
    long getTotalDataDelivered();

    /**
     * Peak rate of messages received per second for this connection.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="PeakMessageReceiptRate", description=TYPE + " Peak Message Receipt Rate")
    double getPeakMessageReceiptRate();

    /**
     * Peak rate of bytes received per second for this connection.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="PeakDataReceiptRate", description=TYPE + " Peak Data Receipt Rate")
    double getPeakDataReceiptRate();

    /**
     * Rate of messages received per second for this connection.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="MessageReceiptRate", description=TYPE + " Message Receipt Rate")
    double getMessageReceiptRate();

    /**
     * Rate of bytes received per second for this connection.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="DataReceiptRate", description=TYPE + " Data Receipt Rate")
    double getDataReceiptRate();

    /**
     * Total count of messages received for this connection.
     * 
     * @since Qpid JMX API 2.2
     */
    @MBeanAttribute(name="TotalMessagesReceived", description=TYPE + " Total Messages Received")
    long getTotalMessagesReceived();

    /**
     * Total count of bytes received for this connection.
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

    /**
     * Sets statistics collection enabled/disabled for this connection.
     *
     * @param enabled
     * @since Qpid JMX API 2.2
     */
    void setStatisticsEnabled(boolean enabled);
}
