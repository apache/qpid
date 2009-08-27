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
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeInUseException;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.flow.*;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.framing.ExchangeDeleteOkBody;

import java.util.ArrayList;
import java.util.Map;

public class ServerSessionDelegate extends SessionDelegate
{
    private final IApplicationRegistry _appRegistry;

    public ServerSessionDelegate(IApplicationRegistry appRegistry)
    {
        _appRegistry = appRegistry;
    }

    @Override
    public void command(Session session, Method method)
    {
        super.command(session, method);
        if (method.isSync())
        {
            session.flushProcessed();
        }
    }

    @Override
    public void messageAccept(Session session, MessageAccept method)
    {
        ((ServerSession)session).accept(method.getTransfers());
    }



    @Override
    public void messageReject(Session session, MessageReject method)
    {
        ((ServerSession)session).reject(method.getTransfers());
    }

    @Override
    public void messageRelease(Session session, MessageRelease method)
    {
        ((ServerSession)session).release(method.getTransfers());
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


        FlowCreditManager_0_10 creditManager = new CreditCreditManager(0L,0L);

        // TODO filters

        Subscription_0_10 sub = new Subscription_0_10((ServerSession)session, destination,method.getAcceptMode(),method.getAcquireMode(), creditManager, null);

        ((ServerSession)session).register(destination, sub);
        try
        {
            queue.registerSubscription(sub, method.getExclusive());
        }
        catch (AMQException e)
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            throw new RuntimeException(e);
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

            ssn.processed(xfr);
        }
        catch (AMQException e)
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            throw new RuntimeException(e);
        }


        super.messageTransfer(ssn, xfr);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void messageCancel(Session session, MessageCancel method)
    {
        String destination = method.getDestination();

        Subscription_0_10 sub = ((ServerSession)session).getSubscription(destination);

        if(sub == null)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, "not-found: destination '"+destination+"'");
        }
        else
        {
            ((ServerSession)session).unregister(sub);
        }
    }

    @Override
    public void messageFlush(Session session, MessageFlush method)
    {
        String destination = method.getDestination();

        Subscription_0_10 sub = ((ServerSession)session).getSubscription(destination);

        if(sub == null)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, "not-found: destination '"+destination+"'");
        }
        else
        {

            try
            {
                sub.flush();
            }
            catch (AMQException e)
            {
                //TODO
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void txSelect(Session session, TxSelect method)
    {
        // TODO - check current tx mode
        ((ServerSession)session).selectTx();
    }

    @Override
    public void txCommit(Session session, TxCommit method)
    {
        // TODO - check current tx mode
        ((ServerSession)session).commit();
    }

    @Override
    public void txRollback(Session session, TxRollback method)
    {
        // TODO - check current tx mode
        ((ServerSession)session).rollback();
    }


    @Override
    public void exchangeDeclare(Session session, ExchangeDeclare method)
    {
        String exchangeName = method.getExchange();
        VirtualHost virtualHost = getVirtualHost(session);
        Exchange exchange = getExchange(session, exchangeName);

        if(method.getPassive())
        {
            if(exchange == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "not-found: exchange-name '"+exchangeName+"'");

            }
            else
            {
                // TODO - check exchange has same properties
            }

        }
        else
        {
            if (!virtualHost.getAccessManager().authoriseCreateExchange((ServerSession)session, method.getAutoDelete(),
                    method.getDurable(), new AMQShortString(method.getExchange()), false, false, method.getPassive(),
                    new AMQShortString(method.getType())))
            {

                ExecutionErrorCode errorCode = ExecutionErrorCode.NOT_ALLOWED;
                String description = "permission denied: exchange-name '" + exchangeName + "'";

                exception(session, method, errorCode, description);


            }
            else
            {
                ExchangeRegistry exchangeRegistry = getExchangeRegistry(session);
                ExchangeFactory exchangeFactory = virtualHost.getExchangeFactory();

                try
                {

                    exchange = exchangeFactory.createExchange(method.getExchange(),
                                                              method.getType(),
                                                              method.getDurable(),
                                                              method.getAutoDelete());

                    exchangeRegistry.registerExchange(exchange);
                }
                catch(AMQUnknownExchangeType e)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "Unknown Exchange Type: " + method.getType());
                }
                catch (AMQException e)
                {
                    //TODO
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    throw new RuntimeException(e);
                }

            }

        }
    }

    private void exception(Session session, Method method, ExecutionErrorCode errorCode, String description)
    {
        ExecutionException ex = new ExecutionException();
        ex.setErrorCode(errorCode);
        ex.setCommandId(method.getId());
        ex.setDescription(description);

        session.invoke(ex);
        session.close();
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
        VirtualHost virtualHost = getVirtualHost(session);
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();

        //Perform ACLs
        if (!virtualHost.getAccessManager().authoriseDelete((ServerSession)session,
                exchangeRegistry.getExchange(method.getExchange())))
        {
            exception(session,method, ExecutionErrorCode.NOT_ALLOWED, "Permission denied");

        }
        else
        {

            try
            {
                exchangeRegistry.unregisterExchange(method.getExchange(), method.getIfUnused());
            }
            catch (ExchangeInUseException e)
            {
                exception(session, method, ExecutionErrorCode.PRECONDITION_FAILED, "Exchange in use");
            }
            catch (AMQException e)
            {
                // TODO
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                throw new RuntimeException(e);
            }
        }

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

        VirtualHost virtualHost = getVirtualHost(session);

        String queueName = method.getQueue();

        if (!method.getPassive())
        {
            // Perform ACL if request is not passive

            if (!virtualHost.getAccessManager().authoriseCreateQueue(((ServerSession)session), method.getAutoDelete(), method.getDurable(),
                    method.getExclusive(), false, method.getPassive(), new AMQShortString(queueName)))
            {
                ExecutionErrorCode errorCode = ExecutionErrorCode.NOT_ALLOWED;
                String description = "permission denied: queue-name '" + queueName + "'";

                exception(session, method, errorCode, description);

                // TODO control flow
                return;
            }
        }


        AMQQueue queue;
        QueueRegistry queueRegistry = getQueueRegistry(session);
        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?

        synchronized (queueRegistry)
        {

            if (((queue = queueRegistry.getQueue(queueName)) == null))
            {

                if (method.getPassive())
                {
                    String description = "Queue: " + queueName + " not found on VirtualHost(" + virtualHost + ").";
                    ExecutionErrorCode errorCode = ExecutionErrorCode.NOT_FOUND;

                    exception(session, method, errorCode, description);

                    return;
                }
                else
                {
                    try
                    {
                        queue = createQueue(queueName, method, virtualHost, (ServerSession)session);

                        if (queue.isDurable() && !queue.isAutoDelete())
                        {
                            //store.createQueue(queue, body.getArguments());
                        }
                        queueRegistry.registerQueue(queue);
                        boolean autoRegister = ApplicationRegistry.getInstance().getConfiguration().getQueueAutoRegister();

                        if (autoRegister)
                        {
                            ExchangeRegistry exchangeRegistry = getExchangeRegistry(session);

                            Exchange defaultExchange = exchangeRegistry.getDefaultExchange();

                            queue.bind(defaultExchange, new AMQShortString(queueName), null);

                        }
                    }
                    catch (AMQException e)
                    {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        throw new RuntimeException(e);
                    }
                }
            }
            else if (queue.getOwner() != null && !((ServerSession)session).getPrincipal().getName().equals(queue.getOwner()))
            {

                    String description = "Cannot declare queue('" + queueName + "'),"
                                                                           + " as exclusive queue with same name "
                                                                           + "declared on another client ID('"
                                                                           + queue.getOwner() + "')";
                    ExecutionErrorCode errorCode = ExecutionErrorCode.RESOURCE_LOCKED;

                    exception(session, method, errorCode, description);

                    return;
            }

        }
    }


    protected AMQQueue createQueue(final String queueName,
                                   QueueDeclare body,
                                   VirtualHost virtualHost,
                                   final ServerSession session)
            throws AMQException
    {
        final QueueRegistry registry = virtualHost.getQueueRegistry();

        String owner = body.getExclusive() ? session.getPrincipal().getName() : null;

        final AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, body.getDurable(), owner, body.getAutoDelete(), virtualHost,
                                                                  body.getArguments());


        if (body.getExclusive() && !body.getDurable())
        {
            final ServerSession.Task deleteQueueTask =
                    new ServerSession.Task()
                    {
                        public void doTask(ServerSession session)
                        {
                            if (registry.getQueue(queueName) == queue)
                            {
                                try
                                {
                                    queue.delete();
                                }
                                catch (AMQException e)
                                {
                                    //TODO
                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    };

            session.addSessionCloseTask(deleteQueueTask);

            queue.addQueueDeleteTask(new AMQQueue.Task()
            {
                public void doTask(AMQQueue queue)
                {
                    session.removeSessionCloseTask(deleteQueueTask);
                }
            });
        }// if exclusive and not durable

        return queue;
    }

    @Override
    public void queueDelete(Session session, QueueDelete method)
    {

        String queueName = method.getQueue();
        if(queueName == null || queueName.length()==0)
        {
            exception(session, method, ExecutionErrorCode.INVALID_ARGUMENT, "No queue name supplied");

        }
        else
        {
            AMQQueue queue = getQueue(session, queueName);


            if (queue == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "No queue " + queueName + " found");
            }
            else
            {
                if (method.getIfEmpty() && !queue.isEmpty())
                {
                    exception(session, method, ExecutionErrorCode.PRECONDITION_FAILED, "Queue " + queueName + " not empty");
                }
                else if (method.getIfUnused() && !queue.isUnused())
                {
                    // TODO - Error code
                    exception(session, method, ExecutionErrorCode.PRECONDITION_FAILED, "Queue " + queueName + " in use");

                }
                else
                {
                    VirtualHost virtualHost = getVirtualHost(session);

                    //Perform ACLs
                    if (!virtualHost.getAccessManager().authoriseDelete(((ServerSession)session), queue))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Cannot delete queue " + queueName);
                    }
                    else
                    {
                        try
                        {
                            int purged = queue.delete();
                        }
                        catch (AMQException e)
                        {
                            //TODO
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            throw new RuntimeException(e);
                        }


                        /*    if (queue.isDurable())
                        {
                            store.removeQueue(queue);
                        }*/
                    }

                }
            }
        }

    }

    @Override
    public void queuePurge(Session session, QueuePurge method)
    {
        String queueName = method.getQueue();
        if(queueName == null || queueName.length()==0)
        {
            exception(session, method, ExecutionErrorCode.INVALID_ARGUMENT, "No queue name supplied");

        }
        else
        {
            AMQQueue queue = getQueue(session, queueName);


            if (queue == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "No queue " + queueName + " found");
            }
            else
            {
                //TODO
                try
                {
                    queue.clearQueue(new StoreContext());
                }
                catch (AMQException e)
                {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    throw new RuntimeException(e);
                }
            }
        }

    }

    @Override
    public void queueQuery(Session session, QueueQuery method)
    {
        super.queueQuery(session, method);
    }

    @Override
    public void messageSetFlowMode(Session session, MessageSetFlowMode sfm)
    {
        String destination = sfm.getDestination();

        Subscription_0_10 sub = ((ServerSession)session).getSubscription(destination);

        if(sub == null)
        {
            exception(session, sfm, ExecutionErrorCode.NOT_FOUND, "not-found: destination '"+destination+"'");
        }

        if(sub.isStopped())
        {
            sub.setFlowMode(sfm.getFlowMode());
        }
    }

    @Override
    public void messageStop(Session session, MessageStop stop)
    {
        String destination = stop.getDestination();

        Subscription_0_10 sub = ((ServerSession)session).getSubscription(destination);

        if(sub == null)
        {
            exception(session, stop, ExecutionErrorCode.NOT_FOUND, "not-found: destination '"+destination+"'");
        }

        sub.stop();

    }

    @Override
    public void messageFlow(Session session, MessageFlow flow)
    {
        String destination = flow.getDestination();

        Subscription_0_10 sub = ((ServerSession)session).getSubscription(destination);

        if(sub == null)
        {
            exception(session, flow, ExecutionErrorCode.NOT_FOUND, "not-found: destination '"+destination+"'");
        }

        sub.addCredit(flow.getUnit(), flow.getValue());

    }

    @Override
    public void closed(Session session)
    {
        super.closed(session);
        for(Subscription_0_10 sub : getSubscriptions(session).values())
        {
            sub.close();
        }
        ((ServerSession)session).onClose();
        ((ServerSession)session).onClose();
    }

    public Map<String, Subscription_0_10> getSubscriptions(Session session)
    {
        return ((ServerSession)session).getSubscriptions();
    }

}
