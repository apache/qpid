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
package org.apache.qpid.server.transport;

import org.apache.qpid.transport.*;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.flow.*;
import org.apache.qpid.AMQException;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.nio.ByteBuffer;

public class ServerSessionDelegate extends SessionDelegate
{
    private final IApplicationRegistry _appRegistry;
    private Map<String, Subscription_0_10> _subscriptions = new HashMap<String, Subscription_0_10>();

    public ServerSessionDelegate(IApplicationRegistry appRegistry)
    {
        _appRegistry = appRegistry;
    }

    @Override
    public void messageAccept(Session session, MessageAccept method)
    {
        super.messageAccept(session, method);
    }

    @Override
    public void messageReject(Session session, MessageReject method)
    {
        super.messageReject(session, method);
    }

    @Override
    public void messageRelease(Session session, MessageRelease method)
    {
        super.messageRelease(session, method);
    }

    @Override
    public void messageAcquire(Session session, MessageAcquire method)
    {
        super.messageAcquire(session, method);
    }

    @Override
    public void messageResume(Session session, MessageResume method)
    {
        super.messageResume(session, method);
    }

    @Override
    public void messageSubscribe(Session session, MessageSubscribe method)
    {
        String destination = method.getDestination();
        String queueName = method.getQueue();
        QueueRegistry queueRegistry = getQueueRegistry(session);

        AMQQueue queue = queueRegistry.getQueue(queueName);

        //TODO null check

        FlowCreditManager creditManager = new MessageOnlyCreditManager(0L);

        // TODO filters

        Subscription_0_10 sub = new Subscription_0_10((ServerSession)session, destination,method.getAcceptMode(),method.getAcquireMode(), creditManager, null);

        _subscriptions.put(destination, sub);
        try
        {
            queue.registerSubscription(sub, method.getExclusive());
        }
        catch (AMQException e)
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }        

    }


    @Override
    public void messageTransfer(Session ssn, MessageTransfer xfr)
    {
        ExchangeRegistry exchangeRegistry = getExchangeRegistry(ssn);
        Exchange exchange;
        if(xfr.hasDestination())
        {
            exchange = exchangeRegistry.getExchange(xfr.getDestination());
        }
        else
        {
            exchange = exchangeRegistry.getDefaultExchange();
        }

        MessageTransferMessage message = new MessageTransferMessage(xfr);
        try
        {
            ArrayList<AMQQueue> queues = exchange.route(message);

            ((ServerSession) ssn).enqueue(message, queues);


            System.out.println(queues);

            ssn.processed(xfr);
        }
        catch (AMQException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


        super.messageTransfer(ssn, xfr);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void messageCancel(Session session, MessageCancel method)
    {
        super.messageCancel(session, method);
    }

    @Override
    public void messageFlush(Session session, MessageFlush method)
    {
        super.messageFlush(session, method);
    }

    @Override
    public void txSelect(Session session, TxSelect method)
    {
        super.txSelect(session, method);
    }

    @Override
    public void txCommit(Session session, TxCommit method)
    {
        super.txCommit(session, method);
    }

    @Override
    public void txRollback(Session session, TxRollback method)
    {
        super.txRollback(session, method);
    }

    @Override
    public void dtxSelect(Session session, DtxSelect method)
    {
        super.dtxSelect(session, method);
    }

    @Override
    public void dtxStart(Session session, DtxStart method)
    {
        super.dtxStart(session, method);
    }

    @Override
    public void dtxEnd(Session session, DtxEnd method)
    {
        super.dtxEnd(session, method);
    }

    @Override
    public void dtxCommit(Session session, DtxCommit method)
    {
        super.dtxCommit(session, method);
    }

    @Override
    public void dtxForget(Session session, DtxForget method)
    {
        super.dtxForget(session, method);
    }

    @Override
    public void dtxGetTimeout(Session session, DtxGetTimeout method)
    {
        super.dtxGetTimeout(session, method);
    }

    @Override
    public void dtxPrepare(Session session, DtxPrepare method)
    {
        super.dtxPrepare(session, method);
    }

    @Override
    public void dtxRecover(Session session, DtxRecover method)
    {
        super.dtxRecover(session, method);
    }

    @Override
    public void dtxRollback(Session session, DtxRollback method)
    {
        super.dtxRollback(session, method);
    }

    @Override
    public void dtxSetTimeout(Session session, DtxSetTimeout method)
    {
        super.dtxSetTimeout(session, method);
    }

    @Override
    public void exchangeDeclare(Session session, ExchangeDeclare method)
    {
        String exchangeName = method.getExchange();

        Exchange exchange = getExchange(session, exchangeName);

        if(method.getPassive())
        {
            if(exchange == null)
            {
                ExecutionException ex = new ExecutionException();
                ex.setErrorCode(ExecutionErrorCode.NOT_FOUND);
                ex.setCommandId(method.getId());

                ex.setDescription("not-found: exchange-name '"+exchangeName+"'");

                session.invoke(ex);
                session.close();
            }

        }
        else
        {
            // TODO
        }
        super.exchangeDeclare(session, method);
    }

    private Exchange getExchange(Session session, String exchangeName)
    {
        ExchangeRegistry exchangeRegistry = getExchangeRegistry(session);
        return exchangeRegistry.getExchange(exchangeName);
    }

    private ExchangeRegistry getExchangeRegistry(Session session)
    {
        VirtualHost virtualHost = getVirtualHost(session);
        return virtualHost.getExchangeRegistry();

    }

    private VirtualHost getVirtualHost(Session session)
    {
        ServerConnection conn = getServerConnection(session);
        VirtualHost vhost = conn.getVirtualHost();
        return vhost;
    }

    private ServerConnection getServerConnection(Session session)
    {
        ServerConnection conn = (ServerConnection) session.getConnection();
        return conn;
    }

    @Override
    public void exchangeDelete(Session session, ExchangeDelete method)
    {
        super.exchangeDelete(session, method);
    }

    @Override
    public void exchangeQuery(Session session, ExchangeQuery method)
    {
        super.exchangeQuery(session, method);

    }

    @Override
    public void exchangeBind(Session session, ExchangeBind method)
    {
        super.exchangeBind(session, method);
    }

    @Override
    public void exchangeUnbind(Session session, ExchangeUnbind method)
    {
        super.exchangeUnbind(session, method);
    }

    @Override
    public void exchangeBound(Session session, ExchangeBound method)
    {


        ExchangeBoundResult result = new ExchangeBoundResult();
        if(method.hasExchange())
        {
            Exchange exchange = getExchange(session, method.getExchange());

            if(exchange == null)
            {
                result.setExchangeNotFound(true);
            }

            if(method.hasQueue())
            {

                AMQQueue queue = getQueue(session, method.getQueue());
                if(queue == null)
                {
                    result.setQueueNotFound(true);
                }

                if(exchange != null && queue != null)
                {

                    if(method.hasBindingKey())
                    {

                        if(method.hasArguments())
                        {
                            // TODO
                        }
                        result.setKeyNotMatched(!exchange.isBound(method.getBindingKey(), queue));

                    }

                    result.setQueueNotMatched(!exchange.isBound(queue));

                }
            }
            else if(exchange != null && method.hasBindingKey())
            {
                if(method.hasArguments())
                {
                    // TODO
                }
                result.setKeyNotMatched(!exchange.isBound(method.getBindingKey()));

            }

        }
        else if(method.hasQueue())
        {
            AMQQueue queue = getQueue(session, method.getQueue());
            if(queue == null)
            {
                result.setQueueNotFound(true);
            }
            else
            {
                if(method.hasBindingKey())
                {
                    if(method.hasArguments())
                    {
                        // TODO
                    }

                    // TODO
                }
            }

        }


        session.executionResult((int) method.getId(), result);
        super.exchangeBound(session, method);
    }

    private AMQQueue getQueue(Session session, String queue)
    {
        QueueRegistry queueRegistry = getQueueRegistry(session);
        return queueRegistry.getQueue(queue);
    }

    private QueueRegistry getQueueRegistry(Session session)
    {
        return getVirtualHost(session).getQueueRegistry();
    }

    @Override
    public void queueDeclare(Session session, QueueDeclare method)
    {
        super.queueDeclare(session, method);
    }

    @Override
    public void queueDelete(Session session, QueueDelete method)
    {
        super.queueDelete(session, method);
    }

    @Override
    public void queuePurge(Session session, QueuePurge method)
    {
        super.queuePurge(session, method);
    }

    @Override
    public void queueQuery(Session session, QueueQuery method)
    {
        super.queueQuery(session, method);
    }


    @Override
    public void messageFlow(Session ssn, MessageFlow flow)
    {
        String destination = flow.getDestination();

        Subscription_0_10 sub = _subscriptions.get(destination);

        FlowCreditManager creditManager = sub.getCreditManager();

        if(flow.getUnit() == MessageCreditUnit.MESSAGE)
        {
            creditManager.addCredit(flow.getValue(), 0L);
        }

    }
}
