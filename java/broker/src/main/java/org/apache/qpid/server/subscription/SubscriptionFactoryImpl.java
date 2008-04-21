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
package org.apache.qpid.server.subscription;

import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionFactory;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;

public class SubscriptionFactoryImpl implements SubscriptionFactory
{

  /*  private SubscriptionFactoryImpl()
    {

    }*/

    public Subscription createSubscription(int channel, AMQProtocolSession protocolSession,
                                           AMQShortString consumerTag, boolean acks, FieldTable filters,
                                           boolean noLocal, FlowCreditManager creditManager) throws AMQException
    {

        boolean isBrowser;

        if (filters != null)
        {
            Boolean isBrowserObj = (Boolean) filters.get(AMQPFilterTypes.NO_CONSUME.getValue());
            isBrowser = (isBrowserObj != null) && isBrowserObj.booleanValue();
        }
        else
        {
            isBrowser = false;
        }

        if(isBrowser)
        {
            return new SubscriptionImpl.BrowserSubscription(channel, protocolSession, consumerTag,  filters, noLocal, creditManager);
        }
        else if(acks)
        {
            return new SubscriptionImpl.AckSubscription(channel, protocolSession, consumerTag,  filters, noLocal, creditManager);
        }
        else
        {
            return new SubscriptionImpl.NoAckSubscription(channel, protocolSession, consumerTag,  filters, noLocal, creditManager);
        }
    }



    public static final SubscriptionFactoryImpl INSTANCE = new SubscriptionFactoryImpl();
}
