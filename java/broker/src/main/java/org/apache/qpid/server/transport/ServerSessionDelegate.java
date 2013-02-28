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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeInUseException;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.HeadersExchange;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.flow.FlowCreditManager_0_10;
import org.apache.qpid.server.flow.WindowCreditManager;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.message.MessageMetaData_0_10;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.subscription.SubscriptionFactoryImpl;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.txn.AlreadyKnownDtxException;
import org.apache.qpid.server.txn.DtxNotSelectedException;
import org.apache.qpid.server.txn.IncorrectDtxStateException;
import org.apache.qpid.server.txn.JoinAndResumeDtxException;
import org.apache.qpid.server.txn.NotAssociatedDtxException;
import org.apache.qpid.server.txn.RollbackOnlyDtxException;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.txn.SuspendAndFailDtxException;
import org.apache.qpid.server.txn.TimeoutDtxException;
import org.apache.qpid.server.txn.UnknownDtxBranchException;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.*;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ServerSessionDelegate extends SessionDelegate
{
    private static final Logger LOGGER = Logger.getLogger(ServerSessionDelegate.class);

    /**
     * No-local queue argument is used to support the no-local feature of Durable Subscribers.
     */
    private static final String QUEUE_ARGUMENT_NO_LOCAL = "no-local";

    public ServerSessionDelegate()
    {

    }

    @Override
    public void command(Session session, Method method)
    {
        try
        {
            setThreadSubject(session);

            if(!session.isClosing())
            {
                Object asyncCommandMark = ((ServerSession)session).getAsyncCommandMark();
                super.command(session, method, false);
                Object newOutstanding = ((ServerSession)session).getAsyncCommandMark();
                if(newOutstanding == null || newOutstanding == asyncCommandMark)
                {
                    session.processed(method);
                }

                if(newOutstanding != null)
                {
                    ((ServerSession)session).completeAsyncCommands();
                }

                if (method.isSync())
                {
                    ((ServerSession)session).awaitCommandCompletion();
                    session.flushProcessed();
                }
            }
        }
        catch(RuntimeException e)
        {
            LOGGER.error("Exception processing command", e);
            exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, "Exception processing command: " + e);
        }
    }

    @Override
    public void messageAccept(Session session, MessageAccept method)
    {
        final ServerSession serverSession = (ServerSession) session;
        serverSession.accept(method.getTransfers());
        if(!serverSession.isTransactional())
        {
            serverSession.recordFuture(StoreFuture.IMMEDIATE_FUTURE,
                                       new CommandProcessedAction(serverSession, method));
        }
    }

    @Override
    public void messageReject(Session session, MessageReject method)
    {
        ((ServerSession)session).reject(method.getTransfers());
    }

    @Override
    public void messageRelease(Session session, MessageRelease method)
    {
        ((ServerSession)session).release(method.getTransfers(), method.getSetRedelivered());
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
        /*
          TODO - work around broken Python tests
          Correct code should read like
          if not hasAcceptMode() exception ILLEGAL_ARGUMENT "Accept-mode not supplied"
          else if not method.hasAcquireMode() exception ExecutionErrorCode.ILLEGAL_ARGUMENT, "Acquire-mode not supplied"
        */
        if(!method.hasAcceptMode())
        {
            method.setAcceptMode(MessageAcceptMode.EXPLICIT);
        }
        if(!method.hasAcquireMode())
        {
            method.setAcquireMode(MessageAcquireMode.PRE_ACQUIRED);

        }

        if(!method.hasQueue())
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
                else if(queue.getAuthorizationHolder() != null && queue.getAuthorizationHolder() != session)
                {
                    exception(session,method,ExecutionErrorCode.RESOURCE_LOCKED, "Exclusive Queue: " + queueName + " owned exclusively by another session");
                }
                else if(queue.isExclusive() && queue.getExclusiveOwningSession() != null && queue.getExclusiveOwningSession() != session)
                {
                    exception(session,method,ExecutionErrorCode.RESOURCE_LOCKED, "Exclusive Queue: " + queueName + " owned exclusively by another session");
                }
                else
                {
                    if(queue.isExclusive())
                    {
                        ServerSession s = (ServerSession) session;
                        queue.setExclusiveOwningSession(s);
                        if(queue.getAuthorizationHolder() == null)
                        {
                            queue.setAuthorizationHolder(s);
                            queue.setExclusiveOwningSession(s);
                            ((ServerSession) session).addSessionCloseTask(new ServerSession.Task()
                            {
                                public void doTask(ServerSession session)
                                {
                                    if(queue.getAuthorizationHolder() == session)
                                    {
                                        queue.setAuthorizationHolder(null);
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

                    Subscription_0_10 sub = SubscriptionFactoryImpl.INSTANCE.createSubscription((ServerSession)session,
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
    public void messageTransfer(Session ssn, final MessageTransfer xfr)
    {
        final Exchange exchange = getExchangeForMessage(ssn, xfr);

        DeliveryProperties delvProps = null;
        if(xfr.getHeader() != null && (delvProps = xfr.getHeader().getDeliveryProperties()) != null && delvProps.hasTtl() && !delvProps
                .hasExpiration())
        {
            delvProps.setExpiration(System.currentTimeMillis() + delvProps.getTtl());
        }

        final MessageMetaData_0_10 messageMetaData = new MessageMetaData_0_10(xfr);
        messageMetaData.setConnectionReference(((ServerSession)ssn).getReference());

        if (!getVirtualHost(ssn).getSecurityManager().authorisePublish(messageMetaData.isImmediate(), messageMetaData.getRoutingKey(), exchange.getName()))
        {
            ExecutionErrorCode errorCode = ExecutionErrorCode.UNAUTHORIZED_ACCESS;
            String description = "Permission denied: exchange-name '" + exchange.getName() + "'";
            exception(ssn, xfr, errorCode, description);

            return;
        }

        final Exchange exchangeInUse;
        List<? extends BaseQueue> queues = exchange.route(messageMetaData);
        if(queues.isEmpty() && exchange.getAlternateExchange() != null)
        {
            final Exchange alternateExchange = exchange.getAlternateExchange();
            queues = alternateExchange.route(messageMetaData);
            if (!queues.isEmpty())
            {
                exchangeInUse = alternateExchange;
            }
            else
            {
                exchangeInUse = exchange;
            }
        }
        else
        {
            exchangeInUse = exchange;
        }

        final ServerSession serverSession = (ServerSession) ssn;
        if(!queues.isEmpty())
        {
            final MessageStore store = getVirtualHost(ssn).getMessageStore();
            final StoredMessage<MessageMetaData_0_10> storeMessage = createStoreMessage(xfr, messageMetaData, store);
            MessageTransferMessage message = new MessageTransferMessage(storeMessage, serverSession.getReference());
            serverSession.enqueue(message, queues);
            storeMessage.flushToStore();
        }
        else
        {
            if((delvProps == null || !delvProps.getDiscardUnroutable()) && xfr.getAcceptMode() == MessageAcceptMode.EXPLICIT)
            {
                RangeSet rejects = RangeSetFactory.createRangeSet();
                rejects.add(xfr.getId());
                MessageReject reject = new MessageReject(rejects, MessageRejectCode.UNROUTABLE, "Unroutable");
                ssn.invoke(reject);
            }
            else
            {
                serverSession.getLogActor().message(ExchangeMessages.DISCARDMSG(exchangeInUse.getName(), messageMetaData.getRoutingKey()));
            }
        }


        if(serverSession.isTransactional())
        {
            serverSession.processed(xfr);
        }
        else
        {
            serverSession.recordFuture(StoreFuture.IMMEDIATE_FUTURE, new CommandProcessedAction(serverSession, xfr));
        }
    }

    private StoredMessage<MessageMetaData_0_10> createStoreMessage(final MessageTransfer xfr,
                                                                   final MessageMetaData_0_10 messageMetaData, final MessageStore store)
    {
        final StoredMessage<MessageMetaData_0_10> storeMessage = store.addMessage(messageMetaData);
        ByteBuffer body = xfr.getBody();
        if(body != null)
        {
            storeMessage.addContent(0, body);
        }
        return storeMessage;
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
                queue.setAuthorizationHolder(null);
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
    public void dtxSelect(Session session, DtxSelect method)
    {
        // TODO - check current tx mode
        ((ServerSession)session).selectDtx();
    }

    @Override
    public void dtxStart(Session session, DtxStart method)
    {
        XaResult result = new XaResult();
        result.setStatus(DtxXaStatus.XA_OK);
        try
        {
            ((ServerSession)session).startDtx(method.getXid(), method.getJoin(), method.getResume());
            session.executionResult(method.getId(), result);
        }
        catch(JoinAndResumeDtxException e)
        {
            exception(session, method, ExecutionErrorCode.COMMAND_INVALID, e.getMessage());
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Unknown xid " + method.getXid());
        }
        catch(AlreadyKnownDtxException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Xid already started an neither join nor " +
                                                                       "resume set" + method.getXid());
        }
        catch(DtxNotSelectedException e)
        {
            exception(session, method, ExecutionErrorCode.COMMAND_INVALID, e.getMessage());
        }

    }

    @Override
    public void dtxEnd(Session session, DtxEnd method)
    {
        XaResult result = new XaResult();
        result.setStatus(DtxXaStatus.XA_OK);
        try
        {
            try
            {
                ((ServerSession)session).endDtx(method.getXid(), method.getFail(), method.getSuspend());
            }
            catch (TimeoutDtxException e)
            {
                result.setStatus(DtxXaStatus.XA_RBTIMEOUT);
            }
            session.executionResult(method.getId(), result);
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_STATE, e.getMessage());
        }
        catch(NotAssociatedDtxException e)
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_STATE, e.getMessage());
        }
        catch(DtxNotSelectedException e)
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_STATE, e.getMessage());
        }
        catch(SuspendAndFailDtxException e)
        {
            exception(session, method, ExecutionErrorCode.COMMAND_INVALID, e.getMessage());
        }

    }

    @Override
    public void dtxCommit(Session session, DtxCommit method)
    {
        XaResult result = new XaResult();
        result.setStatus(DtxXaStatus.XA_OK);
        try
        {
            try
            {
                ((ServerSession)session).commitDtx(method.getXid(), method.getOnePhase());
            }
            catch (RollbackOnlyDtxException e)
            {
                result.setStatus(DtxXaStatus.XA_RBROLLBACK);
            }
            catch (TimeoutDtxException e)
            {
                result.setStatus(DtxXaStatus.XA_RBTIMEOUT);
            }
            session.executionResult(method.getId(), result);
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, e.getMessage());
        }
        catch(IncorrectDtxStateException e)
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_STATE, e.getMessage());
        }
        catch(AMQStoreException e)
        {
            exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void dtxForget(Session session, DtxForget method)
    {
        try
        {
            ((ServerSession)session).forgetDtx(method.getXid());
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, e.getMessage());
        }
        catch(IncorrectDtxStateException e)
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_STATE, e.getMessage());
        }

    }

    @Override
    public void dtxGetTimeout(Session session, DtxGetTimeout method)
    {
        GetTimeoutResult result = new GetTimeoutResult();
        try
        {
            result.setTimeout(((ServerSession) session).getTimeoutDtx(method.getXid()));
            session.executionResult(method.getId(), result);
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, e.getMessage());
        }
    }

    @Override
    public void dtxPrepare(Session session, DtxPrepare method)
    {
        XaResult result = new XaResult();
        result.setStatus(DtxXaStatus.XA_OK);
        try
        {
            try
            {
                ((ServerSession)session).prepareDtx(method.getXid());
            }
            catch (RollbackOnlyDtxException e)
            {
                result.setStatus(DtxXaStatus.XA_RBROLLBACK);
            }
            catch (TimeoutDtxException e)
            {
                result.setStatus(DtxXaStatus.XA_RBTIMEOUT);
            }
            session.executionResult((int) method.getId(), result);
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, e.getMessage());
        }
        catch(IncorrectDtxStateException e)
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_STATE, e.getMessage());
        }
        catch(AMQStoreException e)
        {
            exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void dtxRecover(Session session, DtxRecover method)
    {
        RecoverResult result = new RecoverResult();
        List inDoubt = ((ServerSession)session).recoverDtx();
        result.setInDoubt(inDoubt);
        session.executionResult(method.getId(), result);
    }

    @Override
    public void dtxRollback(Session session, DtxRollback method)
    {

        XaResult result = new XaResult();
        result.setStatus(DtxXaStatus.XA_OK);
        try
        {
            try
            {
                ((ServerSession)session).rollbackDtx(method.getXid());
            }
            catch (TimeoutDtxException e)
            {
                result.setStatus(DtxXaStatus.XA_RBTIMEOUT);
            }
            session.executionResult(method.getId(), result);
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, e.getMessage());
        }
        catch(IncorrectDtxStateException e)
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_STATE, e.getMessage());
        }
        catch(AMQStoreException e)
        {
            exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void dtxSetTimeout(Session session, DtxSetTimeout method)
    {
        try
        {
            ((ServerSession)session).setTimeoutDtx(method.getXid(), method.getTimeout());
        }
        catch(UnknownDtxBranchException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, e.getMessage());
        }
    }

    @Override
    public void executionSync(final Session ssn, final ExecutionSync sync)
    {
        ((ServerSession)ssn).awaitCommandCompletion();
        super.executionSync(ssn, sync);
    }

    @Override
    public void exchangeDeclare(Session session, ExchangeDeclare method)
    {
        String exchangeName = method.getExchange();
        VirtualHost virtualHost = getVirtualHost(session);
        ExchangeRegistry exchangeRegistry = getExchangeRegistry(session);

        //we must check for any unsupported arguments present and throw not-implemented
        if(method.hasArguments())
        {
            Map<String,Object> args = method.getArguments();
            //QPID-3392: currently we don't support any!
            if(!args.isEmpty())
            {
                exception(session, method, ExecutionErrorCode.NOT_IMPLEMENTED, "Unsupported exchange argument(s) found " + args.keySet().toString());
                return;
            }
        }
        synchronized(exchangeRegistry)
        {
            Exchange exchange = getExchange(session, exchangeName);

            if(method.getPassive())
            {
                if(exchange == null)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "not-found: exchange-name '" + exchangeName + "'");
                }
                else
                {
                    if (!exchange.getTypeShortString().toString().equals(method.getType())
                            && (method.getType() != null && method.getType().length() > 0))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Attempt to redeclare exchange: "
                                + exchangeName + " of type " + exchange.getTypeShortString() + " to " + method.getType() + ".");
                    }
                }
            }
            else
            {
                if (exchange == null)
                {
                    if (exchangeName.startsWith("amq."))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Attempt to declare exchange: "
                                + exchangeName + " which begins with reserved prefix 'amq.'.");
                    }
                    else if (exchangeName.startsWith("qpid."))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Attempt to declare exchange: "
                                + exchangeName + " which begins with reserved prefix 'qpid.'.");
                    }
                    else
                    {
                        ExchangeFactory exchangeFactory = virtualHost.getExchangeFactory();
                        try
                        {
                            exchange = exchangeFactory.createExchange(method.getExchange(),
                                                                      method.getType(),
                                                                      method.getDurable(),
                                                                      method.getAutoDelete());
                            String alternateExchangeName = method.getAlternateExchange();
                            boolean validAlternate;
                            if(alternateExchangeName != null && alternateExchangeName.length() != 0)
                            {
                                Exchange alternate = getExchange(session, alternateExchangeName);
                                if(alternate == null)
                                {
                                    validAlternate = false;
                                }
                                else
                                {
                                    exchange.setAlternateExchange(alternate);
                                    validAlternate = true;
                                }
                            }
                            else
                            {
                                validAlternate = true;
                            }
                            if(validAlternate)
                            {
                                if (exchange.isDurable())
                                {
                                    DurableConfigurationStore store = virtualHost.getMessageStore();
                                    store.createExchange(exchange);
                                }
                                exchangeRegistry.registerExchange(exchange);
                            }
                            else
                            {
                                exception(session, method, ExecutionErrorCode.NOT_FOUND,
                                            "Unknown alternate exchange " + alternateExchangeName);
                            }
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
                }
                else
                {
                    if(!exchange.getTypeShortString().toString().equals(method.getType()))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED,
                                "Attempt to redeclare exchange: " + exchangeName
                                        + " of type " + exchange.getTypeShortString()
                                        + " to " + method.getType() +".");
                    }
                    else if(method.hasAlternateExchange()
                              && (exchange.getAlternateExchange() == null ||
                                  !method.getAlternateExchange().equals(exchange.getAlternateExchange().getName())))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED,
                                "Attempt to change alternate exchange of: " + exchangeName
                                        + " from " + exchange.getAlternateExchange()
                                        + " to " + method.getAlternateExchange() +".");
                    }
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

    private Exchange getExchangeForMessage(Session ssn, MessageTransfer xfr)
    {
        final ExchangeRegistry exchangeRegistry = getExchangeRegistry(ssn);
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
        return exchange;
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
            if (nameNullOrEmpty(method.getExchange()))
            {
                exception(session, method, ExecutionErrorCode.INVALID_ARGUMENT, "Delete not allowed for default exchange");
                return;
            }

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
                    DurableConfigurationStore store = virtualHost.getMessageStore();
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

    private boolean nameNullOrEmpty(String name)
    {
        if(name == null || name.length() == 0)
        {
            return true;
        }

        return false;
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
        else if (nameNullOrEmpty(method.getExchange()))
        {
            exception(session, method, ExecutionErrorCode.INVALID_ARGUMENT, "Bind not allowed for default exchange");
        }
        else
        {
            //TODO - here because of non-compiant python tests
            // should raise exception ILLEGAL_ARGUMENT "binding-key not set"
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
                if (!exchange.isBound(method.getBindingKey(), method.getArguments(), queue))
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
        else if (nameNullOrEmpty(method.getExchange()))
        {
            exception(session, method, ExecutionErrorCode.INVALID_ARGUMENT, "Unbind not allowed for default exchange");
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

                    if(queueMatched)
                    {
                        result.setKeyNotMatched(!exchange.isBound(method.getBindingKey(), queue));
                    }
                    else
                    {
                        result.setKeyNotMatched(!exchange.isBound(method.getBindingKey()));
                    }

                    if(method.hasArguments())
                    {
                        result.setArgsNotMatched(!exchange.isBound(result.getKeyNotMatched() ? null : method.getBindingKey(), method.getArguments(), queueMatched ? queue : null));
                    }

                }
                else if (method.hasArguments())
                {
                    result.setArgsNotMatched(!exchange.isBound(null, method.getArguments(), queueMatched ? queue : null));
                }

            }
            else if(exchange != null && method.hasBindingKey())
            {
                result.setKeyNotMatched(!exchange.isBound(method.getBindingKey()));

                if(method.hasArguments())
                {
                    result.setArgsNotMatched(!exchange.isBound(result.getKeyNotMatched() ? null : method.getBindingKey(), method.getArguments(), queue));
                }


            }

        }
        else if(exchange != null && method.hasBindingKey())
        {
            if(method.hasArguments())
            {
                result.setArgsNotMatched(!exchange.isBound(method.getBindingKey(), method.getArguments(), null));
            }
            result.setKeyNotMatched(!exchange.isBound(method.getBindingKey()));

        }
        else if(exchange != null && method.hasArguments())
        {
            result.setArgsNotMatched(!exchange.isBound(null, method.getArguments(), null));
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
        DurableConfigurationStore store = virtualHost.getMessageStore();

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
                        if(!method.getExclusive() && method.getAutoDelete())
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
                            if(method.getArguments().containsKey(QUEUE_ARGUMENT_NO_LOCAL))
                            {
                                Object noLocal = method.getArguments().get(QUEUE_ARGUMENT_NO_LOCAL);
                                queue.setNoLocal(convertBooleanValue(noLocal));
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

                        ExchangeRegistry exchangeRegistry = getExchangeRegistry(session);

                        Exchange defaultExchange = exchangeRegistry.getDefaultExchange();

                        virtualHost.getBindingFactory().addBinding(queueName, queue, defaultExchange, null);

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
                                    q.setAuthorizationHolder(null);
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

    /**
     * Converts a queue argument into a boolean value.  For compatibility with the C++
     * and the clients, accepts with Boolean, String, or Number types.
     * @param argValue  argument value.
     *
     * @return true if set
     */
    private boolean convertBooleanValue(Object argValue)
    {
        if(argValue instanceof Boolean && ((Boolean)argValue))
        {
            return true;
        }
        else if (argValue instanceof String && Boolean.parseBoolean((String)argValue))
        {
            return true;
        }
        else if (argValue instanceof Number && ((Number)argValue).intValue() != 0)
        {
            return true;
        }
        return false;
    }

    protected AMQQueue createQueue(final String queueName,
                                   final QueueDeclare body,
                                   VirtualHost virtualHost,
                                   final ServerSession session)
            throws AMQException
    {
        String owner = body.getExclusive() ? session.getClientID() : null;

        final AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateQueueUUID(queueName, virtualHost.getName()), queueName, body.getDurable(), owner,
                                                                  body.getAutoDelete(), body.getExclusive(), virtualHost, body.getArguments());

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
                if(queue.getAuthorizationHolder() != null && queue.getAuthorizationHolder() != session)
                {
                    exception(session,method,ExecutionErrorCode.RESOURCE_LOCKED, "Exclusive Queue: " + queueName + " owned exclusively by another session");
                }
                else if(queue.isExclusive() && queue.getExclusiveOwningSession()  != null && queue.getExclusiveOwningSession() != session)
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
                            DurableConfigurationStore store = virtualHost.getMessageStore();
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
        setThreadSubject(session);

        ServerSession serverSession = (ServerSession)session;

        serverSession.stopSubscriptions();
        serverSession.onClose();
        serverSession.unregisterSubscriptions();
    }

    @Override
    public void detached(Session session)
    {
        closed(session);
    }

    private void setThreadSubject(Session session)
    {
        final ServerConnection scon = (ServerConnection) session.getConnection();
        SecurityManager.setThreadSubject(scon.getAuthorizedSubject());
    }

    private static class CommandProcessedAction implements ServerTransaction.Action
    {
        private final ServerSession _serverSession;
        private final Method _method;

        public CommandProcessedAction(final ServerSession serverSession, final Method xfr)
        {
            _serverSession = serverSession;
            _method = xfr;
        }

        public void postCommit()
        {
            _serverSession.processed(_method);
        }

        public void onRollback()
        {
        }
    }
}
