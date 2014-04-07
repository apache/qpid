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
package org.apache.qpid.server.logging.subjects;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.BINDING_FORMAT;

import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class BindingLogSubject extends AbstractLogSubject
{

    /**
     * Create a BindingLogSubject that Logs in the following format.
     *
     * [ vh(/)/ex(amq.direct)/qu(testQueue)/bd(testQueue) ]
     *
     * @param routingKey
     * @param exchange
     * @param queue
     */
    public BindingLogSubject(String routingKey, ExchangeImpl exchange,
                             AMQQueue queue)
    {
        VirtualHost virtualHost = queue.getVirtualHost();
        ExchangeType exchangeType = exchange.getExchangeType();
        setLogStringWithFormat(BINDING_FORMAT,
                               virtualHost.getName(),
                               exchangeType.getType(),
                               exchange.getName(),
                               queue.getName(),
                               routingKey);
    }

}
