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

import javax.management.NotCompliantMBeanException;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.VirtualHost;

abstract class AbstractStatisticsGatheringMBean<T extends ConfiguredObject> extends AMQManagedObject
{
    private long _lastStatUpdateTime;
    private long _statUpdatePeriod = 5000L;
    private long _lastMessagesReceived;
    private long _lastMessagesSent;
    private long _lastBytesReceived;
    private long _lastBytesSent;
    private double _messageReceivedRate;
    private double _messageSentRate;
    private double _bytesReceivedRate;
    private double _bytesSentRate;
    private double _peakMessageReceivedRate;
    private double _peakMessageSentRate;
    private double _peakBytesReceivedRate;
    private double _peakBytesSentRate;
    private final T _configuredObject;

    protected AbstractStatisticsGatheringMBean(Class<?> managementInterface, 
                                               String typeName, 
                                               ManagedObjectRegistry registry,
                                               T object) throws NotCompliantMBeanException
    {
        super(managementInterface, typeName, registry);
        _configuredObject = object;
        initStats();
    }

    protected void initStats()
    {
        _lastStatUpdateTime = System.currentTimeMillis();
    }

    protected synchronized void updateStats()
    {
        long time = System.currentTimeMillis();
        final long period = time - _lastStatUpdateTime;
        if(period > _statUpdatePeriod)
        {
            long messagesReceived = getMessagesIn();
            long messagesSent = getMessagesOut();
            long bytesReceived = getBytesIn();
            long bytesSent = getBytesOut();

            double messageReceivedRate = (double)(messagesReceived - _lastMessagesReceived) / (double)period;
            double messageSentRate = (double)(messagesSent - _lastMessagesSent) / (double)period;
            double bytesReceivedRate = (double)(bytesReceived - _lastBytesReceived) / (double)period;
            double bytesSentRate = (double)(bytesSent - _lastBytesSent) / (double)period;

            _lastMessagesReceived = messagesReceived;
            _lastMessagesSent = messagesSent;
            _lastBytesReceived = bytesReceived;
            _lastBytesSent = bytesSent;
            
            _messageReceivedRate = messageReceivedRate;
            _messageSentRate = messageSentRate;
            _bytesReceivedRate = bytesReceivedRate;
            _bytesSentRate = bytesSentRate;
            
            if(messageReceivedRate > _peakMessageReceivedRate)
            {
                _peakMessageReceivedRate = messageReceivedRate;
            }
            
            if(messageSentRate > _peakMessageSentRate)
            {
                _peakMessageSentRate = messageSentRate;
            }

            if(bytesReceivedRate > _peakBytesReceivedRate)
            {
                _peakBytesReceivedRate = bytesReceivedRate;
            }
            
            if(bytesSentRate > _peakBytesSentRate)
            {
                _peakBytesSentRate = bytesSentRate;
            }
            
        }
    }

    protected abstract long getBytesOut();

    protected abstract long getBytesIn();

    protected abstract long getMessagesOut();

    protected abstract long getMessagesIn();

    public synchronized void resetStatistics() throws Exception
    {
        updateStats();
        //TODO - implement resetStatistics()
    }

    public synchronized double getPeakMessageDeliveryRate()
    {
        updateStats();
        return _peakMessageSentRate;
    }

    public synchronized double getPeakDataDeliveryRate()
    {
        updateStats();
        return _peakBytesSentRate;
    }

    public synchronized double getMessageDeliveryRate()
    {
        updateStats();
        return _messageSentRate;
    }

    public synchronized double getDataDeliveryRate()
    {
        updateStats();
        return _bytesSentRate;
    }

    public synchronized long getTotalMessagesDelivered()
    {
        updateStats();
        return getMessagesOut();
    }

    public synchronized long getTotalDataDelivered()
    {
        updateStats();
        return getBytesOut();
    }

    protected final T getConfiguredObject()
    {
        return _configuredObject;
    }

    public synchronized double getPeakMessageReceiptRate()
    {
        updateStats();
        return _peakMessageReceivedRate;
    }

    public synchronized double getPeakDataReceiptRate()
    {
        updateStats();
        return _peakBytesReceivedRate;
    }

    public synchronized double getMessageReceiptRate()
    {
        updateStats();
        return _messageReceivedRate;
    }

    public synchronized double getDataReceiptRate()
    {
        updateStats();
        return _bytesReceivedRate;
    }

    public synchronized long getTotalMessagesReceived()
    {
        updateStats();
        return getMessagesIn();
    }

    public synchronized long getTotalDataReceived()
    {
        updateStats();
        return getBytesIn();
    }

}
