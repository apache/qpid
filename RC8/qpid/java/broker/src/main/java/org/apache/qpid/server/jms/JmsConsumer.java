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
package org.apache.qpid.server.jms;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.protocol.AMQProtocolSession;

public class JmsConsumer
{
    private int _prefetchValue;

    private PrefetchUnits _prefetchUnits;

    private boolean _noLocal;

    private boolean _autoAck;

    private boolean _exclusive;

    private AMQProtocolSession _protocolSession;

    public enum PrefetchUnits
    {
        OCTETS,
        MESSAGES
    }

    public int getPrefetchValue()
    {
        return _prefetchValue;
    }

    public void setPrefetchValue(int prefetchValue)
    {
        _prefetchValue = prefetchValue;
    }

    public PrefetchUnits getPrefetchUnits()
    {
        return _prefetchUnits;
    }

    public void setPrefetchUnits(PrefetchUnits prefetchUnits)
    {
        _prefetchUnits = prefetchUnits;
    }

    public boolean isNoLocal()
    {
        return _noLocal;
    }

    public void setNoLocal(boolean noLocal)
    {
        _noLocal = noLocal;
    }

    public boolean isAutoAck()
    {
        return _autoAck;
    }

    public void setAutoAck(boolean autoAck)
    {
        _autoAck = autoAck;
    }

    public boolean isExclusive()
    {
        return _exclusive;
    }

    public void setExclusive(boolean exclusive)
    {
        _exclusive = exclusive;
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }

    public void setProtocolSession(AMQProtocolSession protocolSession)
    {
        _protocolSession = protocolSession;
    }

    public void deliverMessage() throws AMQException
    {

    }
}
