package org.apache.qpid.nclient.impl;
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


import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.QpidException;
import org.apache.qpid.api.Message;
import org.apache.qpid.nclient.ClosedListener;
import org.apache.qpid.nclient.MessagePartListener;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.RangeSet;

import static org.apache.qpid.transport.Option.*;

/**
 * Implements a Qpid Sesion.
 */
public class ClientSession extends org.apache.qpid.transport.Session implements  org.apache.qpid.nclient.DtxSession
{
    static
    {
            String max = "message_size_before_sync"; // KB's
            try
            {
                MAX_NOT_SYNC_DATA_LENGH = new Long(System.getProperties().getProperty(max, "200000000"));
            }
            catch (NumberFormatException e)
            {
                // use default size
                MAX_NOT_SYNC_DATA_LENGH = 200000000;
            }
            String flush = "message_size_before_flush";
            try
            {
                MAX_NOT_FLUSH_DATA_LENGH = new Long(System.getProperties().getProperty(flush, "2000000"));
            }
            catch (NumberFormatException e)
            {
                // use default size
                MAX_NOT_FLUSH_DATA_LENGH = 20000000;
            }
    }

    private static  long MAX_NOT_SYNC_DATA_LENGH;
    private static  long MAX_NOT_FLUSH_DATA_LENGH;

    private Map<String,MessagePartListener> _messageListeners = new ConcurrentHashMap<String,MessagePartListener>();
    private ClosedListener _exceptionListner;
    private RangeSet _rejectedMessages;
    private long _currentDataSizeNotSynced;
    private long _currentDataSizeNotFlushed;

    public ClientSession(byte[] name)
    {
        super(name);
    }

    public void messageAcknowledge(RangeSet ranges, boolean accept)
    {
        for (Range range : ranges)
        {
            super.processed(range);
        }
        super.flushProcessed(accept ? BATCH : NONE);
        if (accept)
        {
            messageAccept(ranges);
        }
    }

    public void messageSubscribe(String queue, String destination, short acceptMode, short acquireMode, MessagePartListener listener, Map<String, Object> filter, Option... options)
    {
        setMessageListener(destination,listener);
        super.messageSubscribe(queue, destination, MessageAcceptMode.get(acceptMode),
                               MessageAcquireMode.get(acquireMode), null, 0, filter,
                               options);
    }

    public void messageTransfer(String destination, Message msg, short acceptMode, short acquireMode) throws IOException
    {
        DeliveryProperties dp = msg.getDeliveryProperties();
        MessageProperties mp = msg.getMessageProperties();
        ByteBuffer  body = msg.readData();
        int size = body.remaining();
        super.messageTransfer
            (destination, MessageAcceptMode.get(acceptMode),
             MessageAcquireMode.get(acquireMode),
             new Header(dp, mp), body);
        _currentDataSizeNotSynced += size;
        _currentDataSizeNotFlushed += size;
    }

    public void sync()
    {
        super.sync();
        _currentDataSizeNotSynced = 0;
    }

    public RangeSet getRejectedMessages()
    {
        return _rejectedMessages;
    }

    public void setMessageListener(String destination, MessagePartListener listener)
    {
        if (listener == null)
        {
            throw new IllegalArgumentException("Cannot set message listener to null");
        }
        _messageListeners.put(destination, listener);
    }

    public void setClosedListener(ClosedListener exceptionListner)
    {
        _exceptionListner = exceptionListner;
    }

    void setRejectedMessages(RangeSet rejectedMessages)
    {
        _rejectedMessages = rejectedMessages;
    }

    void notifyException(QpidException ex)
    {
        _exceptionListner.onClosed(null, null, null);
    }

    Map<String,MessagePartListener> getMessageListeners()
    {
        return _messageListeners;
    }
}
