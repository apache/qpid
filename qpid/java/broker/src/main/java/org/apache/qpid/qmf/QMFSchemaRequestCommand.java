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

package org.apache.qpid.qmf;

import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.AMQException;

import java.util.Collection;
import java.util.ArrayList;

public class QMFSchemaRequestCommand extends QMFCommand
{
    private final String _packageName;
    private final String _className;
    private final byte[] _hash;

    public QMFSchemaRequestCommand(QMFCommandHeader header, BBDecoder decoder)
    {
        super(header);
        _packageName = decoder.readStr8();
        _className = decoder.readStr8();
        _hash = decoder.readBin128();
    }

    public void process(VirtualHost virtualHost, ServerMessage message)
    {
        String exchangeName = message.getMessageHeader().getReplyToExchange();
        String routingKey = message.getMessageHeader().getReplyToRoutingKey();

        IApplicationRegistry appRegistry = virtualHost.getApplicationRegistry();
        QMFService service = appRegistry.getQMFService();

        QMFPackage qmfPackage = service.getPackage(_packageName);
        QMFClass qmfClass = qmfPackage.getQMFClass( _className );

        QMFCommand[] commands = new QMFCommand[2];
        commands[0] = new QMFSchemaResponseCommand(this, qmfClass);
        commands[ 1 ] = new QMFCommandCompletionCommand(this);



        Exchange exchange = virtualHost.getExchangeRegistry().getExchange(exchangeName);

        for(QMFCommand cmd : commands)
        {
            QMFMessage responseMessage = new QMFMessage(routingKey, cmd);


            ArrayList<? extends BaseQueue> queues = exchange.route(responseMessage);

            for(BaseQueue q : queues)
            {
                try
                {
                    q.enqueue(responseMessage);
                }
                catch (AMQException e)
                {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
    }
}
