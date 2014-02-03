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
package org.apache.qpid.server.protocol.v0_8;

import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.subscription.ClientDeliveryMethod;
import org.apache.qpid.server.subscription.DelegatingSubscription;
import org.apache.qpid.server.subscription.RecordDeliveryMethod;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionTarget;

public class SubscriptionFactoryImpl implements SubscriptionFactory
{

    public Subscription createSubscription(int channelId, AMQProtocolSession protocolSession,
                                           AMQShortString consumerTag, boolean acks, FieldTable filters,
                                           boolean noLocal, FlowCreditManager creditManager) throws AMQException
    {
        AMQChannel channel = protocolSession.getChannel(channelId);
        if (channel == null)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "channel :" + channelId + " not found in protocol session");
        }
        ClientDeliveryMethod clientMethod = channel.getClientDeliveryMethod();
        RecordDeliveryMethod recordMethod = channel.getRecordDeliveryMethod();


        return createSubscription(channel, protocolSession, consumerTag, acks, filters,
                                      noLocal,
                                      creditManager,
                                      clientMethod,
                                      recordMethod
                                     );
    }


    public Subscription createSubscription(final AMQChannel channel,
                                           final AMQProtocolSession protocolSession,
                                           final AMQShortString consumerTag,
                                           final boolean acks,
                                           final FieldTable filters,
                                           final boolean noLocal,
                                           final FlowCreditManager creditManager,
                                           final ClientDeliveryMethod clientMethod,
                                           final RecordDeliveryMethod recordMethod
                                          )
            throws AMQException
    {
        boolean isBrowser;
        SubscriptionTarget_0_8 target;
        Subscription subscription;
        
        if (filters != null)
        {
            Boolean isBrowserObj = (Boolean) filters.get(AMQPFilterTypes.NO_CONSUME.getValue());
            isBrowser = (isBrowserObj != null) && isBrowserObj.booleanValue();
        }
        else
        {
            isBrowser = false;
        }

        final FilterManager filterManager = FilterManagerFactory.createManager(FieldTable.convertToMap(filters));
        boolean acquires;
        boolean seesReuques;
        boolean isTransient;
        if(isBrowser)
        {
            target = new SubscriptionTarget_0_8.BrowserSubscription(channel, consumerTag, filters, creditManager, clientMethod, recordMethod);
            acquires = false;
            seesReuques = false;
            isTransient = true;
        }
        else if(acks)
        {
            target = new SubscriptionTarget_0_8.AckSubscription(channel, consumerTag, filters, creditManager, clientMethod, recordMethod);
            acquires = true;
            seesReuques = true;
            isTransient = false;
        }
        else
        {
            target = new SubscriptionTarget_0_8.NoAckSubscription(channel, consumerTag, filters, creditManager, clientMethod, recordMethod);
            acquires = true;
            seesReuques = true;
            isTransient = false;
        }
        subscription =
                new DelegatingSubscription<SubscriptionTarget_0_8>(filterManager, AMQMessage.class, acquires, seesReuques, AMQShortString.toString(consumerTag),isTransient,target);
        target.setSubscription(subscription);
        return subscription;
    }



    public Subscription createBasicGetNoAckSubscription(final AMQChannel channel,
                                                        final AMQProtocolSession session,
                                                        final AMQShortString consumerTag,
                                                        final FieldTable filters,
                                                        final boolean noLocal,
                                                        final FlowCreditManager creditManager,
                                                        final ClientDeliveryMethod deliveryMethod,
                                                        final RecordDeliveryMethod recordMethod) throws AMQException
    {
        SubscriptionTarget_0_8 target = new SubscriptionTarget_0_8.NoAckSubscription(channel, consumerTag, filters, creditManager, deliveryMethod, recordMethod);

        Subscription subscription;
        final FilterManager filterManager = FilterManagerFactory.createManager(FieldTable.convertToMap(filters));
        subscription =
                new DelegatingSubscription<SubscriptionTarget_0_8>(filterManager, AMQMessage.class, true, true, AMQShortString.toString(consumerTag),true,target);
        target.setSubscription(subscription);
        return subscription;
    }

    public static final SubscriptionFactoryImpl INSTANCE = new SubscriptionFactoryImpl();

}
