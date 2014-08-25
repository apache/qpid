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

package org.apache.qpid.server.jmx.mbeans;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.JMException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.monitor.MonitorNotification;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.QueueNotificationListener;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.NotificationCheck;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueEntryVisitor;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class QueueMBean extends AMQManagedObject implements ManagedQueue, QueueNotificationListener
{
    private static final Logger LOGGER = Logger.getLogger(QueueMBean.class);

    private static final String[] VIEW_MSGS_COMPOSITE_ITEM_NAMES_DESC_ARRAY =
            VIEW_MSGS_COMPOSITE_ITEM_NAMES_DESC.toArray(new String[VIEW_MSGS_COMPOSITE_ITEM_NAMES_DESC.size()]);

    private static final OpenType[] MSG_ATTRIBUTE_TYPES;
    private static final CompositeType MSG_DATA_TYPE;
    private static final TabularType MSG_LIST_DATA_TYPE;
    private static final CompositeType MSG_CONTENT_TYPE;
    private static final String[] VIEW_MSG_COMPOSITE_ITEM_NAMES_ARRAY = VIEW_MSG_CONTENT_COMPOSITE_ITEM_NAMES_DESC.toArray(
            new String[VIEW_MSG_CONTENT_COMPOSITE_ITEM_NAMES_DESC.size()]);

    static
    {

        try
        {
            MSG_ATTRIBUTE_TYPES = new OpenType[] {
                SimpleType.LONG, // For message id
                new ArrayType(1, SimpleType.STRING), // For header attributes
                SimpleType.LONG, // For size
                SimpleType.BOOLEAN, // For redelivered
                SimpleType.LONG, // For queue position
                SimpleType.INTEGER // For delivery count}
            };

            MSG_DATA_TYPE = new CompositeType("Message", "AMQ Message",
                VIEW_MSGS_COMPOSITE_ITEM_NAMES_DESC_ARRAY,
                VIEW_MSGS_COMPOSITE_ITEM_NAMES_DESC_ARRAY, MSG_ATTRIBUTE_TYPES);

            MSG_LIST_DATA_TYPE = new TabularType("Messages", "List of messages", MSG_DATA_TYPE,
                                                VIEW_MSGS_TABULAR_UNIQUE_INDEX.toArray(new String[VIEW_MSGS_TABULAR_UNIQUE_INDEX.size()]));

            OpenType[] msgContentAttrs = new OpenType[] {
                    SimpleType.LONG, // For message id
                    SimpleType.STRING, // For MimeType
                    SimpleType.STRING, // For MimeType
                    new ArrayType(SimpleType.BYTE, true) // For message content
            };


            MSG_CONTENT_TYPE = new CompositeType("Message Content", "AMQ Message Content",
                    VIEW_MSG_CONTENT_COMPOSITE_ITEM_NAMES_DESC.toArray(new String[VIEW_MSG_CONTENT_COMPOSITE_ITEM_NAMES_DESC.size()]),
                    VIEW_MSG_CONTENT_COMPOSITE_ITEM_NAMES_DESC.toArray(new String[VIEW_MSG_CONTENT_COMPOSITE_ITEM_NAMES_DESC.size()]),
                    msgContentAttrs);

        }
        catch (OpenDataException e)
        {
            throw new ServerScopedRuntimeException(e);
        }
    }

    private final Queue<?> _queue;
    private final VirtualHostMBean _vhostMBean;

    /** Date/time format used for message expiration and message timestamp formatting */
    public static final String JMSTIMESTAMP_DATETIME_FORMAT = "MM-dd-yy HH:mm:ss.SSS z";

    private static final FastDateFormat FAST_DATE_FORMAT = FastDateFormat.getInstance(JMSTIMESTAMP_DATETIME_FORMAT);

    public QueueMBean(Queue queue, VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedQueue.class, ManagedQueue.TYPE, virtualHostMBean.getRegistry());
        _queue = queue;
        _vhostMBean = virtualHostMBean;
        register();
        _queue.setNotificationListener(this);
    }

    public ManagedObject getParentObject()
    {
        return _vhostMBean;
    }

    public String getObjectInstanceName()
    {
        return ObjectName.quote(getName());
    }

    public String getName()
    {
        return _queue.getName();
    }

    public Integer getMessageCount()
    {
        return _queue.getQueueDepthMessages();
    }

    public Integer getMaximumDeliveryCount()
    {
        return _queue.getMaximumDeliveryAttempts();
    }

    public Long getReceivedMessageCount()
    {
        return _queue.getTotalEnqueuedMessages();
    }

    public Long getQueueDepth()
    {
        return _queue.getQueueDepthBytes();
    }

    public Integer getActiveConsumerCount()
    {
        return  _queue.getConsumerCountWithCredit();
    }

    public Integer getConsumerCount()
    {
        return  _queue.getConsumerCount();
    }

    public String getOwner()
    {
        return _queue.getOwner();
    }

    @Override
    public String getQueueType()
    {
        return _queue.getType();
    }

    public boolean isDurable()
    {
        return _queue.isDurable();
    }

    public boolean isAutoDelete()
    {
        return _queue.getLifetimePolicy() != LifetimePolicy.PERMANENT;
    }

    public Long getMaximumMessageAge()
    {
        return _queue.getAlertThresholdMessageAge();
    }

    public void setMaximumMessageAge(Long age)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_MESSAGE_AGE, getMaximumMessageAge(), age);
    }

    public Long getMaximumMessageSize()
    {
        return _queue.getAlertThresholdMessageSize();
    }

    public void setMaximumMessageSize(Long size)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_MESSAGE_SIZE, getMaximumMessageSize(), size);
    }

    public Long getMaximumMessageCount()
    {
        return _queue.getAlertThresholdQueueDepthMessages();
    }

    public void setMaximumMessageCount(Long value)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, getMaximumMessageCount(), value);
    }

    public Long getMaximumQueueDepth()
    {
        return _queue.getAlertThresholdQueueDepthBytes();
    }

    public void setMaximumQueueDepth(Long value)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, getMaximumQueueDepth(), value);
    }

    public Long getCapacity()
    {
        return _queue.getQueueFlowControlSizeBytes();
    }

    public void setCapacity(Long value)
    {
        _queue.setAttribute(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, getCapacity(), value);
    }

    public Long getFlowResumeCapacity()
    {
        return _queue.getQueueFlowResumeSizeBytes();
    }

    public void setFlowResumeCapacity(Long value)
    {
        _queue.setAttribute(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, getFlowResumeCapacity(), value);
    }

    public boolean isFlowOverfull()
    {
        return _queue.isQueueFlowStopped();
    }

    public boolean isExclusive()
    {
        final ExclusivityPolicy attribute = _queue.getExclusive();
        return attribute != null && attribute != ExclusivityPolicy.NONE;
    }

    public void setExclusive(boolean exclusive)
    {
        if(exclusive)
        {
            ExclusivityPolicy currentValue = _queue.getExclusive();
            if(currentValue == null || currentValue == ExclusivityPolicy.NONE)
            {
                _queue.setAttribute(Queue.EXCLUSIVE, currentValue, ExclusivityPolicy.CONTAINER);
            }
        }
        else
        {
            ExclusivityPolicy currentValue = _queue.getExclusive();
            if(currentValue != null && currentValue != ExclusivityPolicy.NONE)
            {
                _queue.setAttribute(Queue.EXCLUSIVE, currentValue, ExclusivityPolicy.NONE);
            }
        }

    }

    public void setAlternateExchange(String exchangeName) throws OperationsException
    {
        if (exchangeName == null || "".equals(exchangeName))
        {
            _queue.setAttributes(Collections.singletonMap(Queue.ALTERNATE_EXCHANGE, null));
        }
        else
        {
            try
            {

                _queue.setAttributes(Collections.<String,Object>singletonMap(Queue.ALTERNATE_EXCHANGE, exchangeName));
            }
            catch (IllegalArgumentException e)
            {
                throw new OperationsException("No such exchange \""+exchangeName+"\"");
            }
        }
    }

    public String getAlternateExchange()
    {
        Exchange alternateExchange =  _queue.getAlternateExchange();
        return alternateExchange == null ? null : alternateExchange.getName();
    }

    public TabularData viewMessages(int fromIndex, int toIndex)
            throws IOException, JMException
    {
        return viewMessages((long)fromIndex, (long)toIndex);
    }

    public TabularData viewMessages(long startPosition, long endPosition)
            throws IOException, JMException
    {
        if ((startPosition > endPosition) || (startPosition < 1))
        {
            throw new OperationsException("From Index = " + startPosition + ", To Index = " + endPosition
                + "\n\"From Index\" should be greater than 0 and less than \"To Index\"");
        }

        if ((endPosition - startPosition) > Integer.MAX_VALUE)
        {
            throw new OperationsException("Specified MessageID interval is too large. Intervals must be less than 2^31 in size");
        }


        List<QueueEntry> messages = getMessages(startPosition, endPosition);

        TabularDataSupport messageTable = new TabularDataSupport(MSG_LIST_DATA_TYPE);


            // Create the tabular list of message header contents
            long position = startPosition;

            for (QueueEntry queueEntry : messages)
            {
                ServerMessage serverMsg = queueEntry.getMessage();
                AMQMessageHeader header = serverMsg.getMessageHeader();
                String[] headerAttributes =
                    {"reply-to = " + header.getReplyTo(),
                     "propertyFlags = ",
                     "ApplicationID = " + header.getAppId(),
                     "ClusterID = ",
                     "UserId = " + header.getUserId(),
                     "JMSMessageID = " + header.getMessageId(),
                     "JMSCorrelationID = " + header.getCorrelationId(),
                     "JMSDeliveryMode = " + (serverMsg.isPersistent() ? "Persistent" : "Non_Persistent"),
                     "JMSPriority = " + header.getPriority(),
                     "JMSType = " + header.getType(),
                     "JMSExpiration = " + (header.getExpiration() == 0 ? null : FAST_DATE_FORMAT.format(header.getExpiration())),
                     "JMSTimestamp = " + (header.getTimestamp() == 0 ? null : FAST_DATE_FORMAT.format(header.getTimestamp()))
                     };

                Object[] itemValues = new Object[]{ serverMsg.getMessageNumber(),
                                                    headerAttributes,
                                                    serverMsg.getSize(),
                                                    queueEntry.isRedelivered(),
                                                    position,
                                                    queueEntry.getDeliveryCount()};

                position++;

                CompositeData messageData =
                        new CompositeDataSupport(MSG_DATA_TYPE, VIEW_MSGS_COMPOSITE_ITEM_NAMES_DESC_ARRAY, itemValues);
                messageTable.put(messageData);
            }

        return messageTable;

    }

    public CompositeData viewMessageContent(long messageId)
            throws IOException, JMException
    {
        QueueEntry entry = getMessage(messageId);
        if(entry == null)
        {
            throw new OperationsException("AMQMessage with message id = " + messageId + " is not in the " + _queue.getName());
        }

        ServerMessage serverMsg = entry.getMessage();
        final int bodySize = (int) serverMsg.getSize();

        byte[] msgContent = new byte[bodySize];

        ByteBuffer buf = ByteBuffer.wrap(msgContent);
        int stored = serverMsg.getContent(buf, 0);

        if(bodySize != stored)
        {
            LOGGER.error(String.format("An unexpected amount of content was retrieved " +
                    "(expected %d, got %d bytes) when viewing content for message with ID %d " +
                    "on queue '%s' in virtual host '%s'",
                    bodySize, stored, messageId, _queue.getName(), _vhostMBean.getName()));
        }

        AMQMessageHeader header = serverMsg.getMessageHeader();

        String mimeType = null, encoding = null;
        if (header != null)
        {
            mimeType = header.getMimeType();

            encoding = header.getEncoding();
        }


        Object[] itemValues = { messageId, mimeType, encoding, msgContent };

        return new CompositeDataSupport(MSG_CONTENT_TYPE, VIEW_MSG_COMPOSITE_ITEM_NAMES_ARRAY, itemValues);


    }

    private QueueEntry getMessage(long messageId)
    {
        GetMessageVisitor visitor = new GetMessageVisitor(messageId);
        _queue.visit(visitor);
        return visitor.getEntry();
    }

    public void deleteMessageFromTop() throws IOException, JMException
    {
        VirtualHost vhost = _queue.getParent(VirtualHost.class);
        vhost.executeTransaction(new VirtualHost.TransactionalOperation()
        {
            public void withinTransaction(final VirtualHost.Transaction txn)
            {
                _queue.visit(new QueueEntryVisitor()
                {

                    public boolean visit(final QueueEntry entry)
                    {
                        if(entry.acquire())
                        {
                            txn.dequeue(entry);
                            return true;
                        }
                        return false;
                    }
                });

            }
        });

    }

    public Long clearQueue() throws IOException, JMException
    {
        VirtualHost vhost = _queue.getParent(VirtualHost.class);
        final AtomicLong count = new AtomicLong();

        vhost.executeTransaction(new VirtualHost.TransactionalOperation()
        {
            public void withinTransaction(final VirtualHost.Transaction txn)
            {
                _queue.visit(new QueueEntryVisitor()
                {

                    public boolean visit(final QueueEntry entry)
                    {
                        final ServerMessage message = entry.getMessage();
                        if(message != null)
                        {
                            txn.dequeue(entry);
                            count.incrementAndGet();

                        }
                        return false;
                    }
                });

            }
        });
        return count.get();
    }

    public void moveMessages(final long fromMessageId, final long toMessageId, String toQueue)
            throws IOException, JMException
    {
        if ((fromMessageId > toMessageId) || (fromMessageId < 1))
        {
            throw new OperationsException("\"From MessageId\" should be greater than 0 and less than \"To MessageId\"");
        }

        VirtualHost<?,?,?> vhost = _queue.getParent(VirtualHost.class);
        final Queue<?> destinationQueue = vhost.getChildByName(Queue.class, toQueue);
        if (destinationQueue == null)
        {
            throw new OperationsException("No such queue \""+ toQueue +"\"");
        }

        vhost.executeTransaction(new VirtualHost.TransactionalOperation()
        {
            public void withinTransaction(final VirtualHost.Transaction txn)
            {
                _queue.visit(new QueueEntryVisitor()
                {

                    public boolean visit(final QueueEntry entry)
                    {
                        final ServerMessage message = entry.getMessage();
                        if(message != null)
                        {
                            final long messageId = message.getMessageNumber();

                            if ((messageId >= fromMessageId)
                                && (messageId <= toMessageId)
                                && !(message.isReferenced((TransactionLogResource)destinationQueue)))
                            {
                                txn.move(entry, destinationQueue);
                            }

                        }
                        return false;
                    }
                });
            }
        });
    }

    public void deleteMessages(final long fromMessageId, final long toMessageId)
            throws IOException, JMException
    {
        VirtualHost vhost = _queue.getParent(VirtualHost.class);
        vhost.executeTransaction(new VirtualHost.TransactionalOperation()
        {
            public void withinTransaction(final VirtualHost.Transaction txn)
            {
                _queue.visit(new QueueEntryVisitor()
                {
                    public boolean visit(final QueueEntry entry)
                    {
                        final ServerMessage message = entry.getMessage();
                        if(message != null)
                        {
                            final long messageId = message.getMessageNumber();

                            if ((messageId >= fromMessageId)
                                && (messageId <= toMessageId))
                            {
                                txn.dequeue(entry);
                            }
                        }
                        return false;
                    }
                });
            }
        });
    }

    public void copyMessages(final long fromMessageId, final long toMessageId, String toQueue)
            throws IOException, JMException
    {
        if ((fromMessageId > toMessageId) || (fromMessageId < 1))
        {
            throw new OperationsException("\"From MessageId\" should be greater than 0 and less than \"To MessageId\"");
        }

        VirtualHost<?,?,?> vhost = _queue.getParent(VirtualHost.class);
        final Queue<?> destinationQueue = vhost.getChildByName(Queue.class, toQueue);
        if (destinationQueue == null)
        {
            throw new OperationsException("No such queue \""+ toQueue +"\"");
        }
        vhost.executeTransaction(new VirtualHost.TransactionalOperation()
        {
            public void withinTransaction(final VirtualHost.Transaction txn)
            {
                _queue.visit(new QueueEntryVisitor()
                {

                    public boolean visit(final QueueEntry entry)
                    {
                        final ServerMessage message = entry.getMessage();
                        if(message != null)
                        {
                            final long messageId = message.getMessageNumber();

                            if ((messageId >= fromMessageId)
                                && (messageId <= toMessageId)
                                && !(message.isReferenced((TransactionLogResource)destinationQueue)))
                            {
                                txn.copy(entry, destinationQueue);
                            }

                        }
                        return false;
                    }
                });
            }
        });
    }

    private List<QueueEntry> getMessages(final long first, final long last)
    {
        final List<QueueEntry> messages = new ArrayList<QueueEntry>((int)(last-first)+1);
        _queue.visit(new QueueEntryVisitor()
        {
            private long position = 1;

            public boolean visit(QueueEntry entry)
            {
                if(position >= first && position <= last)
                {
                    messages.add(entry);
                }
                position++;
                return position > last;
            }
        });
        return messages;
    }


    protected static class GetMessageVisitor implements QueueEntryVisitor
    {

        private final long _messageNumber;
        private QueueEntry _entry;

        public GetMessageVisitor(long messageId)
        {
            _messageNumber = messageId;
        }

        public boolean visit(QueueEntry entry)
        {
            if(entry.getMessage().getMessageNumber() == _messageNumber)
            {
                _entry = entry;
                return true;
            }
            return false;
        }

        public QueueEntry getEntry()
        {
            return _entry;
        }
    }

    @Override
    public void notifyClients(NotificationCheck notification, Queue queue, String notificationMsg)
    {
        notificationMsg = notification.name() + " " + notificationMsg;

        Notification note = new Notification(MonitorNotification.THRESHOLD_VALUE_EXCEEDED, this, 
                                             incrementAndGetSequenceNumber(), System.currentTimeMillis(), notificationMsg);

        getBroadcaster().sendNotification(note);
    }

    /**
     * returns Notifications sent by this MBean.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo()
    {
        String[] notificationTypes = new String[] { MonitorNotification.THRESHOLD_VALUE_EXCEEDED };
        String name = MonitorNotification.class.getName();
        String description = "Either Message count or Queue depth or Message size has reached threshold high value";
        MBeanNotificationInfo info1 = new MBeanNotificationInfo(notificationTypes, name, description);

        return new MBeanNotificationInfo[] { info1 };
    }

    @Override
    public String getDescription()
    {
        return _queue.getDescription();
    }

    @Override
    public void setDescription(String description)
    {
        _queue.setAttribute(Queue.DESCRIPTION, getDescription(), description);
    }

    @Override
    public String getMessageGroupKey()
    {
        return  _queue.getMessageGroupKey();
    }

    @Override
    public boolean isMessageGroupSharedGroups()
    {
        return _queue.isMessageGroupSharedGroups();
    }

    @Override
    public Long getOldestMessageAge()
    {
        return _queue.getOldestMessageAge();
    }
}
