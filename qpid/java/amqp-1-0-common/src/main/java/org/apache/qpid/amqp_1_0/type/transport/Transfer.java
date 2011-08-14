
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

public class Transfer
  implements FrameBody
  {


    private ByteBuffer _payload;

    private UnsignedInteger _handle;

    private UnsignedInteger _deliveryId;

    private Binary _deliveryTag;

    private UnsignedInteger _messageFormat;

    private Boolean _settled;

    private Boolean _more;

    private ReceiverSettleMode _rcvSettleMode;

    private DeliveryState _state;

    private Boolean _resume;

    private Boolean _aborted;

    private Boolean _batchable;

    public UnsignedInteger getHandle()
    {
        return _handle;
    }

    public void setHandle(UnsignedInteger handle)
    {
        _handle = handle;
    }

    public UnsignedInteger getDeliveryId()
    {
        return _deliveryId;
    }

    public void setDeliveryId(UnsignedInteger deliveryId)
    {
        _deliveryId = deliveryId;
    }

    public Binary getDeliveryTag()
    {
        return _deliveryTag;
    }

    public void setDeliveryTag(Binary deliveryTag)
    {
        _deliveryTag = deliveryTag;
    }

    public UnsignedInteger getMessageFormat()
    {
        return _messageFormat;
    }

    public void setMessageFormat(UnsignedInteger messageFormat)
    {
        _messageFormat = messageFormat;
    }

    public Boolean getSettled()
    {
        return _settled;
    }

    public void setSettled(Boolean settled)
    {
        _settled = settled;
    }

    public Boolean getMore()
    {
        return _more;
    }

    public void setMore(Boolean more)
    {
        _more = more;
    }

    public ReceiverSettleMode getRcvSettleMode()
    {
        return _rcvSettleMode;
    }

    public void setRcvSettleMode(ReceiverSettleMode rcvSettleMode)
    {
        _rcvSettleMode = rcvSettleMode;
    }

    public DeliveryState getState()
    {
        return _state;
    }

    public void setState(DeliveryState state)
    {
        _state = state;
    }

    public Boolean getResume()
    {
        return _resume;
    }

    public void setResume(Boolean resume)
    {
        _resume = resume;
    }

    public Boolean getAborted()
    {
        return _aborted;
    }

    public void setAborted(Boolean aborted)
    {
        _aborted = aborted;
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
        StringBuilder builder = new StringBuilder("Transfer{");
        final int origLength = builder.length();

        if(_handle != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("handle=").append(_handle);
        }

        if(_deliveryId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("deliveryId=").append(_deliveryId);
        }

        if(_deliveryTag != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("deliveryTag=").append(_deliveryTag);
        }

        if(_messageFormat != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("messageFormat=").append(_messageFormat);
        }

        if(_settled != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("settled=").append(_settled);
        }

        if(_more != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("more=").append(_more);
        }

        if(_rcvSettleMode != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("rcvSettleMode=").append(_rcvSettleMode);
        }

        if(_state != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("state=").append(_state);
        }

        if(_resume != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("resume=").append(_resume);
        }

        if(_aborted != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("aborted=").append(_aborted);
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
        conn.receiveTransfer(channel, this);
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
