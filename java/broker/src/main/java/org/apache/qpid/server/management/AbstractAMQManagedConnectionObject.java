package org.apache.qpid.server.management;

import javax.management.Notification;

import javax.management.JMException;
import javax.management.MBeanNotificationInfo;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.monitor.MonitorNotification;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;
import org.apache.qpid.management.common.mbeans.ManagedConnection;

public abstract class AbstractAMQManagedConnectionObject extends AMQManagedObject implements ManagedConnection
{
    protected final String _name;

    protected static final OpenType[] _channelAttributeTypes = { SimpleType.INTEGER, SimpleType.BOOLEAN, SimpleType.STRING, SimpleType.INTEGER, SimpleType.BOOLEAN };
    protected static final CompositeType _channelType;
    protected static final TabularType _channelsType;

    protected static final String BROKER_MANAGEMENT_CONSOLE_HAS_CLOSED_THE_CONNECTION_STR =
                                    "Broker Management Console has closed the connection.";

    static
    {
        try
        {
            _channelType = new CompositeType("Channel", "Channel Details", COMPOSITE_ITEM_NAMES_DESC.toArray(new String[COMPOSITE_ITEM_NAMES_DESC.size()]),
                            COMPOSITE_ITEM_NAMES_DESC.toArray(new String[COMPOSITE_ITEM_NAMES_DESC.size()]), _channelAttributeTypes);
            _channelsType = new TabularType("Channels", "Channels", _channelType, (String[]) TABULAR_UNIQUE_INDEX.toArray(new String[TABULAR_UNIQUE_INDEX.size()]));
        }
        catch (JMException ex)
        {
            // This is not expected to ever occur.
            throw new RuntimeException("Got JMException in static initializer.", ex);
        }
    }

    protected AbstractAMQManagedConnectionObject(final String remoteAddress) throws NotCompliantMBeanException
    {
        super(ManagedConnection.class, ManagedConnection.TYPE);
        _name = "anonymous".equals(remoteAddress) ? (remoteAddress + hashCode()) : remoteAddress;
    }

    @Override
    public String getObjectInstanceName()
    {
        return ObjectName.quote(_name);
    }

    public void notifyClients(String notificationMsg)
    {
        final Notification n = new Notification(MonitorNotification.THRESHOLD_VALUE_EXCEEDED, this, ++_notificationSequenceNumber,
                                                System.currentTimeMillis(), notificationMsg);
        _broadcaster.sendNotification(n);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo()
    {
        String[] notificationTypes = new String[] { MonitorNotification.THRESHOLD_VALUE_EXCEEDED };
        String name = MonitorNotification.class.getName();
        String description = "Channel count has reached threshold value";
        MBeanNotificationInfo info1 = new MBeanNotificationInfo(notificationTypes, name, description);

        return new MBeanNotificationInfo[] { info1 };
    }
}
