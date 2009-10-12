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
package org.apache.qpid.server.message;

import org.apache.qpid.transport.*;

import java.util.concurrent.atomic.AtomicLong;
import java.nio.ByteBuffer;
import java.lang.ref.WeakReference;


public class MessageTransferMessage implements InboundMessage, ServerMessage
{
    private static final AtomicLong _numberSource = new AtomicLong(0L);

    private final MessageTransfer _xfr;
    private final DeliveryProperties _deliveryProps;
    private final MessageProperties _messageProps;
    private final AMQMessageHeader _messageHeader;
    private final long _messageNumber;
    private final long _arrivalTime;
    private WeakReference<Session> _sessionRef;

    public MessageTransferMessage(MessageTransfer xfr, WeakReference<Session> sessionRef)
    {
        this(_numberSource.getAndIncrement(), xfr, sessionRef);
    }

    public MessageTransferMessage(long messageNumber, MessageTransfer xfr, WeakReference<Session> sessionRef)
    {

        _xfr = xfr;
        _messageNumber = messageNumber;
        Header header = _xfr.getHeader();
        if(header != null)
        {
            _deliveryProps = header.get(DeliveryProperties.class);
            _messageProps = header.get(MessageProperties.class);
        }
        else
        {
            _deliveryProps = null;
            _messageProps = null;
        }
        _messageHeader = new MessageTransferHeader(_deliveryProps, _messageProps);
        _arrivalTime = System.currentTimeMillis();
        _sessionRef = sessionRef;
    }

    public String getRoutingKey()
    {
        return _deliveryProps == null ? null : _deliveryProps.getRoutingKey();
    }

    public AMQMessageHeader getMessageHeader()
    {
        return _messageHeader;
    }

    public boolean isPersistent()
    {
        return (_deliveryProps != null) && (_deliveryProps.getDeliveryMode() == MessageDeliveryMode.PERSISTENT);
    }

    public boolean isRedelivered()
    {
        return false;
    }

    public long getSize()
    {

        return _xfr.getBodySize();
    }

    public boolean isImmediate()
    {
        return _deliveryProps != null && _deliveryProps.getImmediate();
    }

    public long getExpiration()
    {
        return _deliveryProps == null ? 0L : _deliveryProps.getExpiration();
    }

    public MessageReference newReference()
    {
        return new TransferMessageReference(this);
    }

    public Long getMessageNumber()
    {
        return _messageNumber;
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

    public Header getHeader()
    {
        return _xfr.getHeader();

    }

    public ByteBuffer getBody()
    {
        return _xfr.getBody();
    }

    public Session getSession()
    {
        return _sessionRef == null ? null : _sessionRef.get();
    }

}
