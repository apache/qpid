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

import java.util.Date;
import java.util.List;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotCompliantMBeanException;
import javax.management.monitor.MonitorNotification;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.amqp_8_0.ConnectionCloseBodyImpl;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.ManagedObject;

/**
 * This MBean class implements the management interface. In order to make more attributes, operations and notifications
 * available over JMX simply augment the ManagedConnection interface and add the appropriate implementation here.
 */
@MBeanDescription("Management Bean for an AMQ Broker Connection")
public class AMQProtocolSessionMBean extends AMQManagedObject implements ManagedConnection
{
    private AMQMinaProtocolSession _session = null;
    private String _name = null;
    //openmbean data types for representing the channel attributes
    private final static String[] _channelAtttibuteNames = {"Channel Id", "Transactional", "Default Queue", "Unacknowledged Message Count"};
    private final static String[] _indexNames = {_channelAtttibuteNames[0]};
    private final static OpenType[] _channelAttributeTypes = {SimpleType.INTEGER, SimpleType.BOOLEAN, SimpleType.STRING, SimpleType.INTEGER};
    private static CompositeType _channelType = null;      // represents the data type for channel data
    private static TabularType _channelsType = null;       // Data type for list of channels type
    private static final AMQShortString BROKER_MANAGEMENT_CONSOLE_HAS_CLOSED_THE_CONNECTION =
            new AMQShortString("Broker Management Console has closed the connection.");

    @MBeanConstructor("Creates an MBean exposing an AMQ Broker Connection")
    public AMQProtocolSessionMBean(AMQMinaProtocolSession session) throws NotCompliantMBeanException, OpenDataException
    {
        super(ManagedConnection.class, ManagedConnection.TYPE);
        _session = session;
        String remote = getRemoteAddress();
        remote = "anonymous".equals(remote) ? remote + hashCode() : remote;
        _name = jmxEncode(new StringBuffer(remote), 0).toString();
        init();
    }


    static
    {
        try
        {
            init();
        }
        catch(JMException ex)
        {
            // It should never occur
            System.out.println(ex.getMessage());
        }
    }

    /**
     * initialises the openmbean data types
     */
    private static void init() throws OpenDataException
    {

        _channelType = new CompositeType("Channel", "Channel Details", _channelAtttibuteNames,
                                         _channelAtttibuteNames, _channelAttributeTypes);
        _channelsType = new TabularType("Channels", "Channels", _channelType, _indexNames);
    }

    public Date getLastIoTime()
    {
        return new Date(_session.getIOSession().getLastIoTime());
    }

    public String getRemoteAddress()
    {
        return _session.getIOSession().getRemoteAddress().toString();
    }

    public ManagedObject getParentObject()
    {
        return _session.getVirtualHost().getManagedObject();
    }

    public Long getWrittenBytes()
    {
        return _session.getIOSession().getWrittenBytes();
    }

    public Long getReadBytes()
    {
        return _session.getIOSession().getReadBytes();
    }

    public Long getMaximumNumberOfChannels()
    {
        return _session.getMaximumNumberOfChannels();
    }

    public void setMaximumNumberOfChannels(Long value)
    {
        _session.setMaximumNumberOfChannels(value);
    }

    public String getObjectInstanceName()
    {
        return _name;
    }

    /**
     * commits transactions for a transactional channel
     *
     * @param channelId
     * @throws JMException if channel with given id doesn't exist or if commit fails
     */
    public void commitTransactions(int channelId) throws JMException
    {
        try
        {
            AMQChannel channel = _session.getChannel(channelId);
            if (channel == null)
            {
                throw new JMException("The channel (channel Id = " + channelId + ") does not exist");
            }
            _session.commitTransactions(channel);
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, ex.toString());
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
        try
        {
            AMQChannel channel = _session.getChannel(channelId);
            if (channel == null)
            {
                throw new JMException("The channel (channel Id = " + channelId + ") does not exist");
            }
            _session.rollbackTransactions(channel);
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, ex.toString());
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
        List<AMQChannel> list = _session.getChannels();

        for (AMQChannel channel : list)
        {
            Object[] itemValues = {channel.getChannelId(), channel.isTransactional(),
                    (channel.getDefaultQueue() != null) ? channel.getDefaultQueue().getName().asString() : null,
                    channel.getUnacknowledgedMessageMap().size()};

            CompositeData channelData = new CompositeDataSupport(_channelType, _channelAtttibuteNames, itemValues);
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
        ConnectionCloseBody connectionCloseBody =
                createConnectionCloseBody(AMQConstant.REPLY_SUCCESS.getCode(),	// replyCode
            BROKER_MANAGEMENT_CONSOLE_HAS_CLOSED_THE_CONNECTION    // replyText
            );
        _session.writeFrame(new AMQFrame(0,connectionCloseBody));

        try
        {
            _session.closeSession();
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, ex.toString());
        }
    }

    private ConnectionCloseBody createConnectionCloseBody(int code, AMQShortString replyText)
    {
        return new ConnectionCloseBodyImpl(code,replyText,0,0);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo()
    {
        String[] notificationTypes = new String[]{MonitorNotification.THRESHOLD_VALUE_EXCEEDED};
        String name = MonitorNotification.class.getName();
        String description = "Channel count has reached threshold value";
        MBeanNotificationInfo info1 = new MBeanNotificationInfo(notificationTypes, name, description);

        return new MBeanNotificationInfo[]{info1};
    }

    public void notifyClients(String notificationMsg)
    {
        Notification n = new Notification(MonitorNotification.THRESHOLD_VALUE_EXCEEDED, this,
                ++_notificationSequenceNumber, System.currentTimeMillis(), notificationMsg);
        _broadcaster.sendNotification(n);
    }

} // End of MBean class
