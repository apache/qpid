/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.qpid.server.protocol;

import org.apache.qpid.server.management.MBeanOperationParameter;
import org.apache.qpid.server.management.MBeanAttribute;
import org.apache.qpid.server.management.MBeanOperation;

import javax.management.openmbean.TabularData;
import javax.management.JMException;
import javax.management.MBeanOperationInfo;
import java.util.Date;
import java.io.IOException;

/**
 * The management interface exposed to allow management of Connections.
 * @author   Bhupendra Bhardwaj
 * @version  0.1
 */
public interface ManagedConnection
{
    static final String TYPE = "Connection";

    /**
     * channel details of all the channels opened for this connection.
     * @return general channel details
     * @throws IOException
     * @throws JMException
     */
    @MBeanAttribute(name="Channels",
                         description="channel details of all the channels opened for this connection")
    TabularData getChannels() throws IOException, JMException;

    /**
     * Tells the last time, the IO operation was done.
     * @return last IO time.
     */
    @MBeanAttribute(name="LastIOTime",
                         description="The last time, the IO operation was done")
    Date getLastIoTime();

    /**
     * Tells the remote address of this connection.
     * @return  remote address
     */
    @MBeanAttribute(name="RemoteAddress",
                         description="The remote address of this connection")
    String getRemoteAddress();

    /**
     * Tells the total number of bytes written till now.
     * @return number of bytes written.
     */
    @MBeanAttribute(name="WrittenBytes",
                         description="The total number of bytes written till now")
    Long getWrittenBytes();

    /**
     * Tells the total number of bytes read till now.
     * @return number of bytes read.
     */
    @MBeanAttribute(name="ReadBytes",
                         description="The total number of bytes read till now")
    Long getReadBytes();

    /**
     * Tells the maximum number of channels that can be opened using
     * this connection.  This is useful in setting notifications or
     * taking required action is there are more channels being created.
     * @return maximum number of channels allowed to be created.
     */
    Long getMaximumNumberOfAllowedChannels();

    /**
     * Sets the maximum number of channels allowed to be created using
     * this connection.
     * @param value
     */
    @MBeanAttribute(name="MaximumNumberOfAllowedChannels",
                             description="The maximum number of channels that can be opened using this connection")    
    void setMaximumNumberOfAllowedChannels(Long value);

    //********** Operations *****************//

    /**
     * Closes all the related channels and unregisters this connection from managed objects.
     */
    @MBeanOperation(name="closeConnection",
                         description="Closes this connection and all related channels",
                         impact= MBeanOperationInfo.ACTION)
    void closeConnection() throws Exception;

    /**
     * Unsubscribes the consumers and unregisters the channel from managed objects.
     */
    @MBeanOperation(name="closeChannel",
                         description="Closes the channel with given channeld and" +
                                     "connected consumers will be unsubscribed",
                         impact= MBeanOperationInfo.ACTION)
    void closeChannel(@MBeanOperationParameter(name="channel Id", description="channel Id")int channelId)
        throws Exception;

    /**
     * Commits the transactions if the channel is transactional.
     * @param channelId
     * @throws JMException
     */
    @MBeanOperation(name="commitTransaction",
                         description="Commits the transactions for given channelID, if the channel is transactional",
                         impact= MBeanOperationInfo.ACTION)
    void commitTransactions(@MBeanOperationParameter(name="channel Id", description="channel Id")int channelId) throws JMException;

    /**
     * Rollsback the transactions if the channel is transactional.
     * @param channelId
     * @throws JMException
     */
    @MBeanOperation(name="rollbackTransactions",
                         description="Rollsback the transactions for given channelId, if the channel is transactional",
                         impact= MBeanOperationInfo.ACTION)
    void rollbackTransactions(@MBeanOperationParameter(name="channel Id", description="channel Id")int channelId) throws JMException;
}
