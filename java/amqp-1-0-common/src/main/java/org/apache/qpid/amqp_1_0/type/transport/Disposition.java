
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


import java.nio.ByteBuffer;


import org.apache.qpid.amqp_1_0.type.*;

public class Disposition
  implements FrameBody
  {


    private ByteBuffer _payload;

    private Role _role;

    private UnsignedInteger _first;

    private UnsignedInteger _last;

    private Boolean _settled;

    private DeliveryState _state;

    private Boolean _batchable;

    public Role getRole()
    {
        return _role;
    }

    public void setRole(Role role)
    {
        _role = role;
    }

    public UnsignedInteger getFirst()
    {
        return _first;
    }

    public void setFirst(UnsignedInteger first)
    {
        _first = first;
    }

    public UnsignedInteger getLast()
    {
        return _last;
    }

    public void setLast(UnsignedInteger last)
    {
        _last = last;
    }

    public Boolean getSettled()
    {
        return _settled;
    }

    public void setSettled(Boolean settled)
    {
        _settled = settled;
    }

    public DeliveryState getState()
    {
        return _state;
    }

    public void setState(DeliveryState state)
    {
        _state = state;
    }

    public Boolean getBatchable()
    {
        return _batchable;
    }

    public void setBatchable(Boolean batchable)
    {
        _batchable = batchable;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("Disposition{");
        final int origLength = builder.length();

        if(_role != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("role=").append(_role);
        }

        if(_first != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("first=").append(_first);
        }

        if(_last != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("last=").append(_last);
        }

        if(_settled != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("settled=").append(_settled);
        }

        if(_state != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("state=").append(_state);
        }

        if(_batchable != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("batchable=").append(_batchable);
        }

        builder.append('}');
        return builder.toString();
    }

    public void invoke(short channel, ConnectionEndpoint conn)
    {
        conn.receiveDisposition(channel, this);
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
