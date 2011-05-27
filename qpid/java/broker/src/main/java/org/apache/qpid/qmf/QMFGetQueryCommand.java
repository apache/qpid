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
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.AMQException;

import java.util.*;

public class QMFGetQueryCommand extends QMFCommand
{
    private Map<String, Object> _map;


    public QMFGetQueryCommand(QMFCommandHeader header, BBDecoder decoder)
    {
        super(header);

        _map = decoder.readMap();
    }

    public void process(VirtualHost virtualHost, ServerMessage message)
    {
        String exchangeName = message.getMessageHeader().getReplyToExchange();
        String routingKey = message.getMessageHeader().getReplyToRoutingKey();

        IApplicationRegistry appRegistry = virtualHost.getApplicationRegistry();
        QMFService service = appRegistry.getQMFService();

        String className = (String) _map.get("_class");
        String packageName = (String) _map.get("_package");
        byte[] objectIdBytes = (byte[]) _map.get("_objectId");
        UUID objectId;
        if(objectIdBytes != null)
        {
            long msb = 0;
            long lsb = 0;

            for (int i = 0; i != 8; i++)
            {
                msb = (msb << 8) | (objectIdBytes[i] & 0xff);
            }
            for (int i = 8; i != 16; i++)
            {
                lsb = (lsb << 8) | (objectIdBytes[i] & 0xff);
            }
            objectId = new UUID(msb, lsb);
        }
        else
        {
            objectId = null;
        }

        List<QMFCommand> commands = new ArrayList<QMFCommand>();
        final long sampleTime = System.currentTimeMillis() * 1000000l;

        Collection<QMFPackage> packages;

        if(packageName != null && packageName.length() != 0)
        {
            QMFPackage qmfPackage = service.getPackage(packageName);
            if(qmfPackage == null)
            {
                packages = Collections.EMPTY_LIST;
            }
            else
            {
                packages = Collections.singleton(qmfPackage);
            }
        }
        else
        {
            packages = service.getSupportedSchemas();
        }

        for(QMFPackage qmfPackage : packages)
        {

            Collection<QMFClass> qmfClasses;

            if(className != null && className.length() != 0)
            {
                QMFClass qmfClass = qmfPackage.getQMFClass(className);
                if(qmfClass == null)
                {
                    qmfClasses = Collections.EMPTY_LIST;
                }
                else
                {
                    qmfClasses = Collections.singleton(qmfClass);
                }
            }
            else
            {
                qmfClasses = qmfPackage.getClasses();
            }


            for(QMFClass qmfClass : qmfClasses)
            {
                Collection<QMFObject> objects;

                if(objectId != null)
                {
                    QMFObject obj = service.getObjectById(qmfClass, objectId);
                    if(obj == null)
                    {
                        objects = Collections.EMPTY_LIST;
                    }
                    else
                    {
                        objects = Collections.singleton(obj);
                    }
                }
                else
                {
                    objects = service.getObjects(qmfClass);
                }

                for(QMFObject object : objects)
                {

                    commands.add(object.asGetQueryResponseCmd(this, sampleTime));
                }
            }


        }


        commands.add( new QMFCommandCompletionCommand(this));


        for(QMFCommand cmd : commands)
        {


            QMFMessage responseMessage = new QMFMessage(routingKey, cmd);

            Exchange exchange = virtualHost.getExchangeRegistry().getExchange(exchangeName);

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