
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


package org.apache.qpid.amqp_1_0.type.messaging;



import java.util.Map;


import org.apache.qpid.amqp_1_0.type.*;

public class DeliveryState
  implements org.apache.qpid.amqp_1_0.type.DeliveryState
  {


    private Map _options;

    private UnsignedInteger _sectionNumber;

    private UnsignedLong _sectionOffset;

    private Outcome _outcome;

    private Binary _txnId;

    public Map getOptions()
    {
        return _options;
    }

    public void setOptions(Map options)
    {
        _options = options;
    }

    public UnsignedInteger getSectionNumber()
    {
        return _sectionNumber;
    }

    public void setSectionNumber(UnsignedInteger sectionNumber)
    {
        _sectionNumber = sectionNumber;
    }

    public UnsignedLong getSectionOffset()
    {
        return _sectionOffset;
    }

    public void setSectionOffset(UnsignedLong sectionOffset)
    {
        _sectionOffset = sectionOffset;
    }

    public Outcome getOutcome()
    {
        return _outcome;
    }

    public void setOutcome(Outcome outcome)
    {
        _outcome = outcome;
    }

    public Binary getTxnId()
    {
        return _txnId;
    }

    public void setTxnId(Binary txnId)
    {
        _txnId = txnId;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("DeliveryState{");
        final int origLength = builder.length();

        if(_options != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("options=").append(_options);
        }

        if(_sectionNumber != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("sectionNumber=").append(_sectionNumber);
        }

        if(_sectionOffset != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("sectionOffset=").append(_sectionOffset);
        }

        if(_outcome != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("outcome=").append(_outcome);
        }

        if(_txnId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("txnId=").append(_txnId);
        }

        builder.append('}');
        return builder.toString();
    }


  }
