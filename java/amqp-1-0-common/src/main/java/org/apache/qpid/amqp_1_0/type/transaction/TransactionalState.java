
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


package org.apache.qpid.amqp_1_0.type.transaction;



import org.apache.qpid.amqp_1_0.type.*;

public class TransactionalState
  implements DeliveryState
  {


    private Binary _txnId;

    private Outcome _outcome;

    public Binary getTxnId()
    {
        return _txnId;
    }

    public void setTxnId(Binary txnId)
    {
        _txnId = txnId;
    }

    public Outcome getOutcome()
    {
        return _outcome;
    }

    public void setOutcome(Outcome outcome)
    {
        _outcome = outcome;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("TransactionalState{");
        final int origLength = builder.length();

        if(_txnId != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("txnId=").append(_txnId);
        }

        if(_outcome != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("outcome=").append(_outcome);
        }

        builder.append('}');
        return builder.toString();
    }


  }
