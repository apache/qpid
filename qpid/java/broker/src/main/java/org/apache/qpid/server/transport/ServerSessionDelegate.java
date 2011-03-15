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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.*;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.flow.FlowCreditManager_0_10;
import org.apache.qpid.server.flow.WindowCreditManager;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.message.MessageMetaData_0_10;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Acquired;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.ExchangeBind;
import org.apache.qpid.transport.ExchangeBound;
import org.apache.qpid.transport.ExchangeBoundResult;
import org.apache.qpid.transport.ExchangeDeclare;
import org.apache.qpid.transport.ExchangeDelete;
import org.apache.qpid.transport.ExchangeQuery;
import org.apache.qpid.transport.ExchangeQueryResult;
import org.apache.qpid.transport.ExchangeUnbind;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.ExecutionException;
import org.apache.qpid.transport.MessageAccept;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquire;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCancel;
import org.apache.qpid.transport.MessageFlow;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageFlush;
import org.apache.qpid.transport.MessageReject;
import org.apache.qpid.transport.MessageRejectCode;
import org.apache.qpid.transport.MessageRelease;
import org.apache.qpid.transport.MessageResume;
import org.apache.qpid.transport.MessageSetFlowMode;
import org.apache.qpid.transport.MessageStop;
import org.apache.qpid.transport.MessageSubscribe;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.QueueDeclare;
import org.apache.qpid.transport.QueueDelete;
import org.apache.qpid.transport.QueuePurge;
import org.apache.qpid.transport.QueueQuery;
import org.apache.qpid.transport.QueueQueryResult;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionDelegate;
import org.apache.qpid.transport.TxCommit;
import org.apache.qpid.transport.TxRollback;
import org.apache.qpid.transport.TxSelect;

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
        SecurityManager.setThreadPrincipal(session.getConnection().getAuthorizationID());

        if(!session.isClosing())
        {
            super.command(session, method);
            if (method.isSync())
            {
                session.flushProcessed();
            }
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
        RangeSet acquiredRanges = ((ServerSession)session).acquire(method.getTransfers());

        Acquired result = new Acquired(acquiredRanges);


        session.executionResult((int) method.getId(), result);


    }

    @Override
    public void messageResume(Session session, MessageResume method)
    {
        super.messageResume(session, method);
    }

    @Override
    public void messageSubscribe(Session session, MessageSubscribe method)
    {

        //TODO - work around broken Python tests
        if(!method.hasAcceptMode())
        {
            method.setAcceptMode(MessageAcceptMode.EXPLICIT);
        }
        if(!method.hasAcquireMode())
        {
            method.setAcquireMode(MessageAcquireMode.PRE_ACQUIRED);

        }

       /* if(!method.hasAcceptMode())
        {
            exception(session,method,ExecutionErrorCode.ILLEGAL_ARGUMENT, "Accept-mode not supplied");
        }
        else if(!method.hasAcquireMode())
        {
            exception(session,method,ExecutionErrorCode.ILLEGAL_ARGUMENT, "Acquire-mode not supplied");
        }
        else */if(!method.hasQueue())
        {
            exception(session,method,ExecutionErrorCode.ILLEGAL_ARGUMENT, "queue not supplied");
        }
        else
        {
            String destination = method.getDestination();

            if(((ServerSession)session).getSubscription(destination)!=null)
            {
                exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Subscription already exists with destaination: '"+destination+"'");
            }
            else
            {
                String queueName = method.getQueue();
                QueueRegistry queueRegistry = getQueueRegistry(session);


                final AMQQueue queue = queueRegistry.getQueue(queueName);

                if(queue == null)
                {
                    exception(session,method,ExecutionErrorCode.NOT_FOUND, "Queue: " + queueName + " not found");
                }
                else if(queue.getPrincipalHolder() != null && queue.getPrincipalHolder() != session)
                {
                    exception(session,method,ExecutionErrorCode.RESOURCE_LOCKED, "Exclusive Queue: " + queueName + " owned exclusively by another session");
                }
                else
                {
                    if(queue.isExclusive())
                    {
                        ServerSession s = (ServerSession) session;
                        queue.setExclusiveOwningSession(s);
                        if(queue.getPrincipalHolder() == null)
                        {
                            queue.setPrincipalHolder(s);
                            queue.setExclusiveOwningSession(s);
                            ((ServerSession) session).addSessionCloseTask(new ServerSession.Task()
                            {
                                public void doTask(ServerSession session)
                                {
                                    if(queue.getPrincipalHolder() == session)
                                    {
                                        queue.setPrincipalHolder(null);
                                        queue.setExclusiveOwningSession(null);
                                    }
                                }
                            });
                        }

                    }

                    FlowCreditManager_0_10 creditManager = new WindowCreditManager(0L,0L);
                    
                    FilterManager filterManager = null;
                    try 
                    {
                        filterManager = FilterManagerFactory.createManager(method.getArguments());
                    } 
                    catch (AMQException amqe) 
                    {
                        exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "Exception Creating FilterManager");
                        return;
                    }

                    Subscription_0_10 sub = new Subscription_0_10((ServerSession)session,
                                                                  destination,
                                                                  method.getAcceptMode(),
                                                                  method.getAcquireMode(),
                                                                  MessageFlowMode.WINDOW,
                                                                  creditManager, 
                                                                  filterManager,
                                                                  method.getArguments());

                    ((ServerSession)session).register(destination, sub);
                    try
                    {
                        queue.registerSubscription(sub, method.getExclusive());
                    }
                    catch (AMQQueue.ExistingExclusiveSubscription existing)
                    {
                        exception(session, method, ExecutionErrorCode.RESOURCE_LOCKED, "Queue has an exclusive consumer");
                    }
                    catch (AMQQueue.ExistingSubscriptionPreventsExclusive exclusive)
                    {
                        exception(session, method, ExecutionErrorCode.RESOURCE_LOCKED, "Queue has an existing consumer - can't subscribe exclusively");
                    }
                    catch (AMQException e)
                    {
                        exception(session, method, e, "Cannot subscribe to '" + destination);
                    }
                }
            }
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
            if(exchange == null)
            {
                exchange = exchangeRegistry.getDefaultExchange();
            }
        }
        else
        {
            exchange = exchangeRegistry.getDefaultExchange();
        }
        

        DeliveryProperties delvProps = null;
        if(xfr.getHeader() != null && (delvProps = xfr.getHeader().get(DeliveryProperties.class)) != null && delvProps.hasTtl() && !delvProps.hasExpiration())
        {
            delvProps.setExpiration(System.currentTimeMillis() + delvProps.getTtl());
        }

        MessageMetaData_0_10 messageMetaData = new MessageMetaData_0_10(xfr);
        
        if (!getVirtualHost(ssn).getSecurityManager().authorisePublish(messageMetaData.isImmediate(), messageMetaData.getRoutingKey(), exchange.getName()))
        {
            ExecutionErrorCode errorCode = ExecutionErrorCode.UNAUTHORIZED_ACCESS;
            String description = "Permission denied: exchange-name '" + exchange.getName() + "'";
            exception(ssn, xfr, errorCode, description);
            
            return;
        }
        
        final MessageStore store = getVirtualHost(ssn).getMessageStore();
        StoredMessage<MessageMetaData_0_10> storeMessage = store.addMessage(messageMetaData);
        ByteBuffer body = xfr.getBody();
        if(body != null)
        {
            storeMessage.addContent(0, body);
        }
        storeMessage.flushToStore();
        MessageTransferMessage message = new MessageTransferMessage(storeMessage, ((ServerSession)ssn).getReference());

        ArrayList<? extends BaseQueue> queues = exchange.route(message);



        if(queues != null && queues.size() != 0)
        {
            ((ServerSession) ssn).enqueue(message, queues);
        }
        else
        {
            if(delvProps == null || !delvProps.hasDiscardUnroutable() || !delvProps.getDiscardUnroutable())
            {
                if(xfr.getAcceptMode() == MessageAcceptMode.EXPLICIT)
                {
                    RangeSet rejects = new RangeSet();
                    rejects.add(xfr.getId());
                    MessageReject reject = new MessageReject(rejects, MessageRejectCode.UNROUTABLE, "Unroutable");
                    ssn.invoke(reject);
                }
                else
                {
                    Exchange alternate = exchange.getAlternateExchange();
                    if(alternate != null)
                    {
                        queues = alternate.route(message);
                        if(queues != null && queues.size() != 0)
                        {
                            ((ServerSession) ssn).enqueue(message, queues);
                        }
                        else
                        {
                            //TODO - log the message discard
                        }
                    }
                    else
                    {
                        //TODO - log the message discard
                    }


                }
            }


        }

        ssn.processed(xfr);
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
            AMQQueue queue = sub.getQueue();
            ((ServerSession)session).unregister(sub);
            if(!queue.isDeleted() && queue.isExclusive() && queue.getConsumerCount() == 0)
            {
                queue.setPrincipalHolder(null);
            }
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
                exception(session, method, e, "Cannot flush subscription '" + destination);
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
                if(!exchange.getTypeShortString().toString().equals(method.getType()))
                {
                    exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Cannot redeclare with a different exchange type");
                }
            }

        }
        else
        {
            if (exchange == null)
            {
                ExchangeRegistry exchangeRegistry = getExchangeRegistry(session);
                ExchangeFactory exchangeFactory = virtualHost.getExchangeFactory();



                try
                {

                    exchange = exchangeFactory.createExchange(method.getExchange(),
                                                              method.getType(),
                                                              method.getDurable(),
                                                              method.getAutoDelete());

                    String alternateExchangeName = method.getAlternateExchange();
                    if(alternateExchangeName != null && alternateExchangeName.length() != 0)
                    {
                        Exchange alternate = getExchange(session, alternateExchangeName);
                        exchange.setAlternateExchange(alternate);
                    }

                    if (exchange.isDurable())
                    {
                        DurableConfigurationStore store = virtualHost.getDurableConfigurationStore();
                        store.createExchange(exchange);
                    }

                    exchangeRegistry.registerExchange(exchange);
                }
                catch(AMQUnknownExchangeType e)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "Unknown Exchange Type: " + method.getType());
                }
                catch (AMQException e)
                {
                    exception(session, method, e, "Cannot declare exchange '" + exchangeName);
                }
            }
            else
            {
                if(!exchange.getTypeShortString().toString().equals(method.getType()))
                {
                    exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Cannot redeclare with a different exchange type");
                }
            }

        }
    }

    // TODO decouple AMQException and AMQConstant error codes 
    private void exception(Session session, Method method, AMQException exception, String message)
    {
        ExecutionErrorCode errorCode = ExecutionErrorCode.INTERNAL_ERROR;
        if (exception.getErrorCode() != null)
        {
            try
            {
                errorCode = ExecutionErrorCode.get(exception.getErrorCode().getCode());
            }
            catch (IllegalArgumentException iae)
            {
                // ignore, already set to INTERNAL_ERROR
            }
        }
        String description = message + "': " + exception.getMessage();
        
        exception(session, method, errorCode, description);
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

        try
        {
            Exchange exchange = getExchange(session, method.getExchange());

            if(exchange == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "No such exchange '" + method.getExchange() + "'");
            }
            else if(exchange.hasReferrers())
            {
                exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Exchange in use as an alternate exchange");
            }
            else if(isStandardExchange(exchange, virtualHost.getExchangeFactory().getRegisteredTypes()))
            {
                exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Exchange '"+method.getExchange()+"' cannot be deleted");
            }
            else
            {
                exchangeRegistry.unregisterExchange(method.getExchange(), method.getIfUnused());

                if (exchange.isDurable() && !exchange.isAutoDelete())
                {
                    DurableConfigurationStore store = virtualHost.getDurableConfigurationStore();
                    store.removeExchange(exchange);
                }
            }
        }
        catch (ExchangeInUseException e)
        {
            exception(session, method, ExecutionErrorCode.PRECONDITION_FAILED, "Exchange in use");
        }
        catch (AMQException e)
        {
            exception(session, method, e, "Cannot delete exchange '" + method.getExchange() );
        }
    }

    private boolean isStandardExchange(Exchange exchange, Collection<ExchangeType<? extends Exchange>> registeredTypes)
    {
        for(ExchangeType type : registeredTypes)
        {
            if(type.getDefaultExchangeName().toString().equals( exchange.getName() ))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void exchangeQuery(Session session, ExchangeQuery method)
    {

        ExchangeQueryResult result = new ExchangeQueryResult();

        Exchange exchange = getExchange(session, method.getName());

        if(exchange != null)
        {
            result.setDurable(exchange.isDurable());
            result.setType(exchange.getTypeShortString().toString());
            result.setNotFound(false);
        }
        else
        {
            result.setNotFound(true);
        }

        session.executionResult((int) method.getId(), result);
    }

    @Override
    public void exchangeBind(Session session, ExchangeBind method)
    {

        VirtualHost virtualHost = getVirtualHost(session);
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

        if (!method.hasQueue())
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "queue not set");
        }
        else if (!method.hasExchange())
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "exchange not set");
        }
/*
        else if (!method.hasBindingKey())
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "binding-key not set");
        }
*/
        else
        {
            //TODO - here because of non-compiant python tests
            if (!method.hasBindingKey())
            {
                method.setBindingKey(method.getQueue());
            }
            AMQQueue queue = queueRegistry.getQueue(method.getQueue());
            Exchange exchange = exchangeRegistry.getExchange(method.getExchange());
            if(queue == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "Queue: '" + method.getQueue() + "' not found");
            }
            else if(exchange == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "Exchange: '" + method.getExchange() + "' not found");
            }
            else if(exchange.getTypeShortString().equals(HeadersExchange.TYPE.getName()) && (!method.hasArguments() || method.getArguments() == null || !method.getArguments().containsKey("x-match")))
            {
                exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, "Bindings to an exchange of type " + HeadersExchange.TYPE.getName() + " require an x-match header");
            }
            else
            {
                AMQShortString routingKey = new AMQShortString(method.getBindingKey());
                FieldTable fieldTable = FieldTable.convertToFieldTable(method.getArguments());

                if (!exchange.isBound(routingKey, fieldTable, queue))
                {
                    try
                    {
                        virtualHost.getBindingFactory().addBinding(method.getBindingKey(), queue, exchange, method.getArguments());
                    }
                    catch (AMQException e)
                    {
                        exception(session, method, e, "Cannot add binding '" + method.getBindingKey());
                    }
                }
                else
                {
                    // todo
                }
            }


        }



    }

    @Override
    public void exchangeUnbind(Session session, ExchangeUnbind method)
    {
        VirtualHost virtualHost = getVirtualHost(session);
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

        if (!method.hasQueue())
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "queue not set");
        }
        else if (!method.hasExchange())
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "exchange not set");
        }
        else if (!method.hasBindingKey())
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "binding-key not set");
        }
        else
        {
            AMQQueue queue = queueRegistry.getQueue(method.getQueue());
            Exchange exchange = exchangeRegistry.getExchange(method.getExchange());
            if(queue == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "Queue: '" + method.getQueue() + "' not found");
            }
            else if(exchange == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "Exchange: '" + method.getExchange() + "' not found");
            }
            else
            {
                try
                {
                    virtualHost.getBindingFactory().removeBinding(method.getBindingKey(), queue, exchange, null);
                }
                catch (AMQException e)
                {
                    exception(session, method, e, "Cannot remove binding '" + method.getBindingKey());
                }
            }
        }


        super.exchangeUnbind(session, method);
    }

    @Override
    public void exchangeBound(Session session, ExchangeBound method)
    {

        ExchangeBoundResult result = new ExchangeBoundResult();
        Exchange exchange;
        AMQQueue queue;
        if(method.hasExchange())
        {
            exchange = getExchange(session, method.getExchange());

            if(exchange == null)
            {
                result.setExchangeNotFound(true);
            }
        }
        else
        {
            exchange = getExchangeRegistry(session).getDefaultExchange();
        }


        if(method.hasQueue())
        {

            queue = getQueue(session, method.getQueue());
            if(queue == null)
            {
                result.setQueueNotFound(true);
            }


            if(exchange != null && queue != null)
            {

                boolean queueMatched = exchange.isBound(queue);

                result.setQueueNotMatched(!queueMatched);


                if(method.hasBindingKey())
                {

                    if(method.hasArguments())
                    {
                        FieldTable args = FieldTable.convertToFieldTable(method.getArguments());
                        
                        result.setArgsNotMatched(!exchange.isBound(new AMQShortString(method.getBindingKey()), args, queue));
                    }
                    if(queueMatched)
                    {
                        result.setKeyNotMatched(!exchange.isBound(method.getBindingKey(), queue));
                    }
                    else
                    {
                        result.setKeyNotMatched(!exchange.isBound(method.getBindingKey()));
                    }
                }
                else if (method.hasArguments())
                {
                    // TODO

                }

                result.setQueueNotMatched(!exchange.isBound(queue));

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
        else if(exchange != null && method.hasBindingKey())
        {
            if(method.hasArguments())
            {
                // TODO
            }
            result.setKeyNotMatched(!exchange.isBound(method.getBindingKey()));

        }


        session.executionResult((int) method.getId(), result);


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
    public void queueDeclare(Session session, final QueueDeclare method)
    {

        VirtualHost virtualHost = getVirtualHost(session);
        DurableConfigurationStore store = virtualHost.getDurableConfigurationStore();

        String queueName = method.getQueue();
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
                        if(method.getExclusive())
                        {
                            queue.setExclusive(true);
                        }
                        else if(method.getAutoDelete())
                        {
                            queue.setDeleteOnNoConsumers(true);
                        }

                        final String alternateExchangeName = method.getAlternateExchange();
                        if(alternateExchangeName != null && alternateExchangeName.length() != 0)
                        {
                            Exchange alternate = getExchange(session, alternateExchangeName);
                            queue.setAlternateExchange(alternate);
                        }

                        if(method.hasArguments()  && method.getArguments() != null)
                        {
                            if(method.getArguments().containsKey("no-local"))
                            {
                                Object no_local = method.getArguments().get("no-local");
                                if(no_local instanceof Boolean && ((Boolean)no_local))
                                {
                                    queue.setNoLocal(true);
                                }
                            }
                        }


                        if (queue.isDurable() && !queue.isAutoDelete())
                        {
                            if(method.hasArguments() && method.getArguments() != null)
                            {
                                Map<String,Object> args = method.getArguments();
                                FieldTable ftArgs = new FieldTable();
                                for(Map.Entry<String, Object> entry : args.entrySet())
                                {
                                    ftArgs.put(new AMQShortString(entry.getKey()), entry.getValue());
                                }
                                store.createQueue(queue, ftArgs);
                            }
                            else
                            {
                                store.createQueue(queue);
                            }
                        }
                        queueRegistry.registerQueue(queue);
                        boolean autoRegister = ApplicationRegistry.getInstance().getConfiguration().getQueueAutoRegister();

                        if (autoRegister)
                        {

                            ExchangeRegistry exchangeRegistry = getExchangeRegistry(session);

                            Exchange defaultExchange = exchangeRegistry.getDefaultExchange();

                            virtualHost.getBindingFactory().addBinding(queueName, queue, defaultExchange, null);

                        }

                        if (method.hasAutoDelete()
                            && method.getAutoDelete()
                            && method.hasExclusive()
                            && method.getExclusive())
                        {
                            final AMQQueue q = queue;
                            final ServerSession.Task deleteQueueTask = new ServerSession.Task()
                                {
                                    public void doTask(ServerSession session)
                                    {
                                        try
                                        {
                                            q.delete();
                                        }
                                        catch (AMQException e)
                                        {
                                            exception(session, method, e, "Cannot delete '" + method.getQueue());
                                        }
                                    }
                                };
                            final ServerSession s = (ServerSession) session;
                            s.addSessionCloseTask(deleteQueueTask);
                            queue.addQueueDeleteTask(new AMQQueue.Task()
                                {
                                    public void doTask(AMQQueue queue) throws AMQException
                                    {
                                        s.removeSessionCloseTask(deleteQueueTask);
                                    }
                                });
                        }
                        if (method.hasExclusive()
                            && method.getExclusive())
                        {
                            final AMQQueue q = queue;
                            final ServerSession.Task removeExclusive = new ServerSession.Task()
                            {
                                public void doTask(ServerSession session)
                                {
                                    q.setPrincipalHolder(null);
                                    q.setExclusiveOwningSession(null);
                                }
                            };
                            final ServerSession s = (ServerSession) session;
                            q.setExclusiveOwningSession(s);
                            s.addSessionCloseTask(removeExclusive);
                            queue.addQueueDeleteTask(new AMQQueue.Task()
                            {
                                public void doTask(AMQQueue queue) throws AMQException
                                {
                                    s.removeSessionCloseTask(removeExclusive);
                                }
                            });
                        }
                    }
                    catch (AMQException e)
                    {
                        exception(session, method, e, "Cannot declare queue '" + queueName);
                    }
                }
            }
            else if (method.getExclusive() && (queue.getExclusiveOwningSession() != null && !queue.getExclusiveOwningSession().equals(session)))
            {
                    String description = "Cannot declare queue('" + queueName + "'),"
                                                                           + " as exclusive queue with same name "
                                                                           + "declared on another session";
                    ExecutionErrorCode errorCode = ExecutionErrorCode.RESOURCE_LOCKED;
    
                    exception(session, method, errorCode, description);
    
                    return;
            }
        }
    }

    protected AMQQueue createQueue(final String queueName,
                                   final QueueDeclare body,
                                   VirtualHost virtualHost,
                                   final ServerSession session)
            throws AMQException
    {
        String owner = body.getExclusive() ? session.getClientID() : null;

        final AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, body.getDurable(), owner, body.getAutoDelete(),
                                                                  body.getExclusive(), virtualHost, body.getArguments());

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
                if(queue.getPrincipalHolder() != null && queue.getPrincipalHolder() != session)
                {
                    exception(session,method,ExecutionErrorCode.RESOURCE_LOCKED, "Exclusive Queue: " + queueName + " owned exclusively by another session");
                }
                else if (method.getIfEmpty() && !queue.isEmpty())
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
                    
                    try
                    {
                        queue.delete();
                        if (queue.isDurable() && !queue.isAutoDelete())
                        {
                            DurableConfigurationStore store = virtualHost.getDurableConfigurationStore();
                            store.removeQueue(queue);
                        }
                    }
                    catch (AMQException e)
                    {
                        exception(session, method, e, "Cannot delete queue '" + queueName);
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
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "No queue name supplied");
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
                try
                {
                    queue.clearQueue();
                }
                catch (AMQException e)
                {
                    exception(session, method, e, "Cannot purge queue '" + queueName);
                }
            }
        }
    }

    @Override
    public void queueQuery(Session session, QueueQuery method)
    {
        QueueQueryResult result = new QueueQueryResult();

        AMQQueue queue = getQueue(session, method.getQueue());

        if(queue != null)
        {
            result.setQueue(queue.getNameShortString().toString());
            result.setDurable(queue.isDurable());
            result.setExclusive(queue.isExclusive());
            result.setAutoDelete(queue.isAutoDelete());
            result.setArguments(queue.getArguments());
            result.setMessageCount(queue.getMessageCount());
            result.setSubscriberCount(queue.getConsumerCount());

        }


        session.executionResult((int) method.getId(), result);

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
        else if(sub.isStopped())
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
        else
        {
            sub.stop();
        }

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
        else
        {
            sub.addCredit(flow.getUnit(), flow.getValue());
        }

    }

    @Override
    public void closed(Session session)
    {
        for(Subscription_0_10 sub : getSubscriptions(session))
        {
            ((ServerSession)session).unregister(sub);
        }
        ((ServerSession)session).onClose();
    }

    @Override
    public void detached(Session session)
    {
        closed(session);
    }

    public Collection<Subscription_0_10> getSubscriptions(Session session)
    {
        return ((ServerSession)session).getSubscriptions();
    }

}
