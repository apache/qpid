
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


package org.apache.qpid.amqp_1_0.type.transport;


import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;


import java.util.Map;


import java.nio.ByteBuffer;


import org.apache.qpid.amqp_1_0.type.*;

public class Flow
  implements FrameBody
  {


    private ByteBuffer _payload;

    private UnsignedInteger _nextIncomingId;

    private UnsignedInteger _incomingWindow;

    private UnsignedInteger _nextOutgoingId;

    private UnsignedInteger _outgoingWindow;

    private UnsignedInteger _handle;

    private UnsignedInteger _deliveryCount;

    private UnsignedInteger _linkCredit;

    private UnsignedInteger _available;

    private Boolean _drain;

    private Boolean _echo;

    private Map _properties;

    public UnsignedInteger getNextIncomingId()
    {
        return _nextIncomingId;
    }

    public void setNextIncomingId(UnsignedInteger nextIncomingId)
    {
        _nextIncomingId = nextIncomingId;
    }

    public UnsignedInteger getIncomingWindow()
    {
        return _incomingWindow;
    }

    public void setIncomingWindow(UnsignedInteger incomingWindow)
    {
        _incomingWindow = incomingWindow;
    }

    public UnsignedInteger getNextOutgoingId()
    {
        return _nextOutgoingId;
    }

    public void setNextOutgoingId(UnsignedInteger nextOutgoingId)
    {
        _nextOutgoingId = nextOutgoingId;
    }

    public UnsignedInteger getOutgoingWindow()
    {
        return _outgoingWindow;
    }

    public void setOutgoingWindow(UnsignedInteger outgoingWindow)
    {
        _outgoingWindow = outgoingWindow;
    }

    public UnsignedInteger getHandle()
    {
        return _handle;
    }

    public void setHandle(UnsignedInteger handle)
    {
        _handle = handle;
    }

    public UnsignedInteger getDeliveryCount()
    {
        return _deliveryCount;
    }

    public void setDeliveryCount(UnsignedInteger deliveryCount)
    {
        _deliveryCount = deliveryCount;
    }

    public UnsignedInteger getLinkCredit()
    {
        return _linkCredit;
    }

    public void setLinkCredit(UnsignedInteger linkCredit)
    {
        _linkCredit = linkCredit;
    }

    public UnsignedInteger getAvailable()
    {
        return _available;
    }

    public void setAvailable(UnsignedInteger available)
    {
        _available = available;
    }

    public Boolean getDrain()
    {
        return _drain;
    }

    public void setDrain(Boolean drain)
    {
        _drain = drain;
    }

    public Boolean getEcho()
    {
        return _echo;
    }

    public void setEcho(Boolean echo)
    {
        _echo = echo;
    }

    public Map getProperties()
    {
        return _properties;
    }

    public void setProperties(Map properties)
    {
        _properties = properties;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("Flow{");
        final int origLength = builder.length();

        if(_nextIncomingId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("nextIncomingId=").append(_nextIncomingId);
        }

        if(_incomingWindow != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("incomingWindow=").append(_incomingWindow);
        }

        if(_nextOutgoingId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("nextOutgoingId=").append(_nextOutgoingId);
        }

        if(_outgoingWindow != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("outgoingWindow=").append(_outgoingWindow);
        }

        if(_handle != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("handle=").append(_handle);
        }

        if(_deliveryCount != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("deliveryCount=").append(_deliveryCount);
        }

        if(_linkCredit != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("linkCredit=").append(_linkCredit);
        }

        if(_available != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("available=").append(_available);
        }

        if(_drain != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("drain=").append(_drain);
        }

        if(_echo != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("echo=").append(_echo);
        }

        if(_properties != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("properties=").append(_properties);
        }

        builder.append('}');
        return builder.toString();
    }

    public void invoke(short channel, ConnectionEndpoint conn)
    {
        conn.receiveFlow(channel, this);
    }

    public void setPayload(ByteBuffer payload)
    {
        _payload = payload;
    }

    public ByteBuffer getPayload()
    {
        return _payload;
    }


  }
