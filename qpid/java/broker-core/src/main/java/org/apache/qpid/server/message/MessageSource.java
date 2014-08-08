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

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.store.TransactionLogResource;

import java.util.Collection;
import java.util.EnumSet;

public interface MessageSource extends TransactionLogResource, MessageNode
{
     ConsumerImpl addConsumer(ConsumerTarget target, FilterManager filters,
                         Class<? extends ServerMessage> messageClass,
                         String consumerName, EnumSet<ConsumerImpl.Option> options)
            throws ExistingExclusiveConsumer, ExistingConsumerPreventsExclusive,
                   ConsumerAccessRefused;

    Collection<? extends ConsumerImpl> getConsumers();

    void addConsumerRegistrationListener(ConsumerRegistrationListener<? super MessageSource> listener);

    void removeConsumerRegistrationListener(ConsumerRegistrationListener<? super MessageSource> listener);

    boolean verifySessionAccess(AMQSessionModel<?,?> session);

    interface ConsumerRegistrationListener<Q extends MessageSource>
    {
        void consumerAdded(Q source, ConsumerImpl consumer);
        void consumerRemoved(Q queue, ConsumerImpl consumer);
    }

    /**
     * ExistingExclusiveConsumer signals a failure to create a consumer, because an exclusive consumer
     * already exists.
     * <p>
     * TODO Move to top level, used outside this class.
     */
    static final class ExistingExclusiveConsumer extends Exception
    {

        public ExistingExclusiveConsumer()
        {
        }
    }

    /**
     * ExistingConsumerPreventsExclusive signals a failure to create an exclusive consumer, as a consumer
     * already exists.
     * <p>
     * TODO Move to top level, used outside this class.
     */
    static final class ExistingConsumerPreventsExclusive extends Exception
    {
        public ExistingConsumerPreventsExclusive()
        {
        }
    }

    static final class ConsumerAccessRefused extends Exception
    {
        public ConsumerAccessRefused()
        {
        }
    }


}
