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
/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.management.common.mbeans.annotations.MBeanConstructor;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.ManagementActor;
import org.apache.qpid.server.management.AbstractAMQManagedConnectionObject;
import org.apache.qpid.server.management.ManagedObject;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.NotCompliantMBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import java.util.Date;
import java.util.List;

/**
 * This MBean class implements the management interface. In order to make more attributes, operations and notifications
 * available over JMX simply augment the ManagedConnection interface and add the appropriate implementation here.
 */
@MBeanDescription("Management Bean for an AMQ Broker 0-9-1/0-9/0-8 Connections")
public class AMQProtocolSessionMBean extends AbstractAMQManagedConnectionObject
{
    private AMQProtocolSession _protocolSession = null;

    private static final AMQShortString BROKER_MANAGEMENT_CONSOLE_HAS_CLOSED_THE_CONNECTION =
                                        new AMQShortString(BROKER_MANAGEMENT_CONSOLE_HAS_CLOSED_THE_CONNECTION_STR);

    @MBeanConstructor("Creates an MBean exposing an AMQ Broker 0-9-1/0-9/0-8 Connection")
    public AMQProtocolSessionMBean(AMQProtocolSession amqProtocolSession) throws NotCompliantMBeanException, OpenDataException
    {
        super(amqProtocolSession.getRemoteAddress().toString());
        _protocolSession = amqProtocolSession;
    }

    public String getClientId()
    {
        return String.valueOf(_protocolSession.getContextKey());
    }

    public String getAuthorizedId()
    {
        return (_protocolSession.getAuthorizedPrincipal() != null ) ? _protocolSession.getAuthorizedPrincipal().getName() : null;
    }

    public String getVersion()
    {
        return (_protocolSession.getClientVersion() == null) ? null : _protocolSession.getClientVersion().toString();
    }

    public Date getLastIoTime()
    {
        return new Date(_protocolSession.getLastIoTime());
    }

    public String getRemoteAddress()
    {
        return _protocolSession.getRemoteAddress().toString();
    }

    public ManagedObject getParentObject()
    {
        return _protocolSession.getVirtualHost().getManagedObject();
    }

    public Long getWrittenBytes()
    {
        return _protocolSession.getWrittenBytes();
    }

    public Long getReadBytes()
    {
        return _protocolSession.getWrittenBytes();
    }

    public Long getMaximumNumberOfChannels()
    {
        return _protocolSession.getMaximumNumberOfChannels();
    }

    /**
     * commits transactions for a transactional channel
     *
     * @param channelId
     * @throws JMException if channel with given id doesn't exist or if commit fails
     */
    public void commitTransactions(int channelId) throws JMException
    {
        CurrentActor.set(new ManagementActor(getLogActor().getRootMessageLogger()));
        try
        {
            AMQChannel channel = _protocolSession.getChannel(channelId);
            if (channel == null)
            {
                throw new JMException("The channel (channel Id = " + channelId + ") does not exist");
            }

            _protocolSession.commitTransactions(channel);
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, ex.toString());
        }
        finally
        {
            CurrentActor.remove();
        }
    }

    /**
     * rollsback the transactions for a transactional channel
     *
     * @param channelId
     * @throws JMException if channel with given id doesn't exist or if rollback fails
     */
    public void rollbackTransactions(int channelId) throws JMException
    {
        CurrentActor.set(new ManagementActor(getLogActor().getRootMessageLogger()));
        try
        {
            AMQChannel channel = _protocolSession.getChannel(channelId);
            if (channel == null)
            {
                throw new JMException("The channel (channel Id = " + channelId + ") does not exist");
            }

            _protocolSession.rollbackTransactions(channel);
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, ex.toString());
        }
        finally
        {
            CurrentActor.remove();
        }
    }

    /**
     * Creates the list of channels in tabular form from the _channelMap.
     *
     * @return list of channels in tabular form.
     * @throws OpenDataException
     */
    public TabularData channels() throws OpenDataException
    {
        TabularDataSupport channelsList = new TabularDataSupport(_channelsType);
        List<AMQChannel> list = _protocolSession.getChannels();

        for (AMQChannel channel : list)
        {
            Object[] itemValues =
                {
                    channel.getChannelId(), channel.isTransactional(),
                    (channel.getDefaultQueue() != null) ? channel.getDefaultQueue().getNameShortString().asString() : null,
                    channel.getUnacknowledgedMessageMap().size(), channel.getBlocking()
                };

            CompositeData channelData = new CompositeDataSupport(_channelType, 
                    COMPOSITE_ITEM_NAMES_DESC.toArray(new String[COMPOSITE_ITEM_NAMES_DESC.size()]), itemValues);
            channelsList.put(channelData);
        }

        return channelsList;
    }

    /**
     * closes the connection. The administrator can use this management operation to close connection to free up
     * resources.
     * @throws JMException
     */
    public void closeConnection() throws JMException
    {

        MethodRegistry methodRegistry = _protocolSession.getMethodRegistry();
        ConnectionCloseBody responseBody =
                methodRegistry.createConnectionCloseBody(AMQConstant.REPLY_SUCCESS.getCode(),
                                                         // replyCode
                                                         BROKER_MANAGEMENT_CONSOLE_HAS_CLOSED_THE_CONNECTION,
                                                         // replyText,
                                                         0,
                                                         0);

        // This seems ugly but because we use closeConnection in both normal
        // broker operation and as part of the management interface it cannot
        // be avoided. The Current Actor will be null when this method is
        // called via the Management interface. This is because we allow the
        // Local API connection with JConsole. If we did not allow that option
        // then the CurrentActor could be set in our JMX Proxy object.
        // As it is we need to set the CurrentActor on all MBean methods
        // Ideally we would not have a single method that can be called from
        // two contexts.
        boolean removeActor = false;
        if (CurrentActor.get() == null)
        {
            removeActor = true;
            CurrentActor.set(new ManagementActor(getLogActor().getRootMessageLogger()));
        }

        try
        {
            _protocolSession.writeFrame(responseBody.generateFrame(0));

            try
            {

                _protocolSession.closeSession();
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex, ex.toString());
            }
        }
        finally
        {
            if (removeActor)
            {
                CurrentActor.remove();
            }
        }
    }

    public void resetStatistics() throws Exception
    {
        _protocolSession.resetStatistics();
    }

    public double getPeakMessageDeliveryRate()
    {
        return _protocolSession.getMessageDeliveryStatistics().getPeak();
    }

    public double getPeakDataDeliveryRate()
    {
        return _protocolSession.getDataDeliveryStatistics().getPeak();
    }

    public double getMessageDeliveryRate()
    {
        return _protocolSession.getMessageDeliveryStatistics().getRate();
    }

    public double getDataDeliveryRate()
    {
        return _protocolSession.getDataDeliveryStatistics().getRate();
    }

    public long getTotalMessagesDelivered()
    {
        return _protocolSession.getMessageDeliveryStatistics().getTotal();
    }

    public long getTotalDataDelivered()
    {
        return _protocolSession.getDataDeliveryStatistics().getTotal();
    }

    public double getPeakMessageReceiptRate()
    {
        return _protocolSession.getMessageReceiptStatistics().getPeak();
    }

    public double getPeakDataReceiptRate()
    {
        return _protocolSession.getDataReceiptStatistics().getPeak();
    }

    public double getMessageReceiptRate()
    {
        return _protocolSession.getMessageReceiptStatistics().getRate();
    }

    public double getDataReceiptRate()
    {
        return _protocolSession.getDataReceiptStatistics().getRate();
    }

    public long getTotalMessagesReceived()
    {
        return _protocolSession.getMessageReceiptStatistics().getTotal();
    }

    public long getTotalDataReceived()
    {
        return _protocolSession.getDataReceiptStatistics().getTotal();
    }

    public boolean isStatisticsEnabled()
    {
        return _protocolSession.isStatisticsEnabled();
    }

    public void setStatisticsEnabled(boolean enabled)
    {
        _protocolSession.setStatisticsEnabled(enabled);
    }
}
