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
package org.apache.qpid.server.protocol.v0_10;

import java.nio.ByteBuffer;
import java.security.AccessControlException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.filter.AMQInvalidArgumentException;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.NoFactoryForTypeException;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UnknownConfiguredObjectException;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
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
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ExchangeIsAlternateException;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.RequiredExchangeException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.*;

public class ServerSessionDelegate extends SessionDelegate
{
    private static final Logger LOGGER = Logger.getLogger(ServerSessionDelegate.class);

    public ServerSessionDelegate()
    {

    }

    @Override
    public void command(Session session, Method method)
    {
        try
        {
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
            if(e instanceof ServerScopedRuntimeException)
            {
                throw e;
            }
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
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "queue not supplied");
        }
        else
        {
            String destination = method.getDestination();

            if(((ServerSession)session).getSubscription(destination)!=null)
            {
                exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Subscription already exists with destination '"+destination+"'");
            }
            else
            {
                String queueName = method.getQueue();
                VirtualHostImpl<?,?,?> vhost = getVirtualHost(session);

                final Collection<MessageSource> sources = new HashSet<>();
                final MessageSource queue = vhost.getMessageSource(queueName);
                if(queue != null)
                {
                    sources.add(queue);
                }
                else if(vhost.getContextValue(Boolean.class, "qpid.enableMultiQueueConsumers")
                        && method.getArguments() != null
                        && method.getArguments().get("x-multiqueue") instanceof Collection)
                {
                    for(Object object : (Collection<Object>)method.getArguments().get("x-multiqueue"))
                    {
                        String sourceName = String.valueOf(object);
                        sourceName = sourceName.trim();
                        if(sourceName.length() != 0)
                        {
                            MessageSource source = vhost.getMessageSource(sourceName);
                            if(source == null)
                            {
                                sources.clear();
                                break;
                            }
                            else
                            {
                                sources.add(source);
                            }
                        }
                    }
                    queueName = method.getArguments().get("x-multiqueue").toString();
                }

                if(sources.isEmpty())
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "Queue: " + queueName + " not found");
                }
                else if(!verifySessionAccess((ServerSession) session, sources))
                {
                    exception(session,method,ExecutionErrorCode.RESOURCE_LOCKED, "Exclusive Queue: " + queueName + " owned exclusively by another session");
                }
                else
                {

                    FlowCreditManager_0_10 creditManager = new WindowCreditManager(0L,0L);

                    FilterManager filterManager = null;
                    try
                    {
                        filterManager = FilterManagerFactory.createManager(method.getArguments());
                    }
                    catch (AMQInvalidArgumentException amqe)
                    {
                        exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "Exception Creating FilterManager");
                        return;
                    }

                    ConsumerTarget_0_10 target = new ConsumerTarget_0_10((ServerSession)session, destination,
                                                                                 method.getAcceptMode(),
                                                                                 method.getAcquireMode(),
                                                                                 MessageFlowMode.WINDOW,
                                                                                 creditManager,
                                                                                 method.getArguments()
                    );

                    ((ServerSession)session).register(destination, target);
                    try
                    {
                        EnumSet<ConsumerImpl.Option> options = EnumSet.noneOf(ConsumerImpl.Option.class);
                        if(method.getAcquireMode() == MessageAcquireMode.PRE_ACQUIRED)
                        {
                            options.add(ConsumerImpl.Option.ACQUIRES);
                        }
                        if(method.getAcquireMode() != MessageAcquireMode.NOT_ACQUIRED || method.getAcceptMode() == MessageAcceptMode.EXPLICIT)
                        {
                            options.add(ConsumerImpl.Option.SEES_REQUEUES);
                        }
                        if(method.getExclusive())
                        {
                            options.add(ConsumerImpl.Option.EXCLUSIVE);
                        }
                        for(MessageSource source : sources)
                        {
                            ((ServerSession) session).register(
                                    source.addConsumer(target,
                                                      filterManager,
                                                      MessageTransferMessage.class,
                                                      destination,
                                                      options));
                        }
                    }
                    catch (AMQQueue.ExistingExclusiveConsumer existing)
                    {
                        exception(session, method, ExecutionErrorCode.RESOURCE_LOCKED, "Queue has an exclusive consumer");
                    }
                    catch (AMQQueue.ExistingConsumerPreventsExclusive exclusive)
                    {
                        exception(session, method, ExecutionErrorCode.RESOURCE_LOCKED, "Queue has an existing consumer - can't subscribe exclusively");
                    }
                    catch (AccessControlException e)
                    {
                        exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
                    }
                    catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
                    {
                        exception(session, method, ExecutionErrorCode.RESOURCE_LOCKED, "Queue has an incompatible exclusivity policy");
                    }
                }
            }
        }
    }

    protected boolean verifySessionAccess(final ServerSession session, final Collection<MessageSource> queues)
    {
        for(MessageSource source : queues)
        {
            if(!verifySessionAccess(session, source))
            {
                return false;
            }
        }
        return true;
    }

    protected boolean verifySessionAccess(final ServerSession session, final MessageSource queue)
    {
        return queue.verifySessionAccess(session);
    }

    @Override
    public void messageTransfer(Session ssn, final MessageTransfer xfr)
    {
        if(((ServerSession)ssn).blockingTimeoutExceeded())
        {
            getVirtualHost(ssn).getEventLogger().message(ChannelMessages.FLOW_CONTROL_IGNORED());

            ((ServerSession) ssn).close(AMQConstant.MESSAGE_TOO_LARGE,
                                        "Session flow control was requested, but not enforced by sender");
        }
        else
        {
            final MessageDestination exchange = getDestinationForMessage(ssn, xfr);

            final DeliveryProperties delvProps =
                    xfr.getHeader() == null ? null : xfr.getHeader().getDeliveryProperties();
            if (delvProps != null && delvProps.hasTtl() && !delvProps.hasExpiration())
            {
                delvProps.setExpiration(System.currentTimeMillis() + delvProps.getTtl());
            }

            final MessageMetaData_0_10 messageMetaData = new MessageMetaData_0_10(xfr);

            final VirtualHostImpl virtualHost = getVirtualHost(ssn);
            try
            {
                virtualHost.getSecurityManager()
                        .authorisePublish(messageMetaData.isImmediate(),
                                          messageMetaData.getRoutingKey(),
                                          exchange.getName(),
                                          virtualHost.getName());
            }
            catch (AccessControlException e)
            {
                ExecutionErrorCode errorCode = ExecutionErrorCode.UNAUTHORIZED_ACCESS;
                exception(ssn, xfr, errorCode, e.getMessage());

                return;
            }

            final MessageStore store = virtualHost.getMessageStore();
            final StoredMessage<MessageMetaData_0_10> storeMessage = createStoreMessage(xfr, messageMetaData, store);
            final ServerSession serverSession = (ServerSession) ssn;
            final MessageTransferMessage message =
                    new MessageTransferMessage(storeMessage, serverSession.getReference());
            MessageReference<MessageTransferMessage> reference = message.newReference();

            final InstanceProperties instanceProperties = new InstanceProperties()
            {
                @Override
                public Object getProperty(final Property prop)
                {
                    switch (prop)
                    {
                        case EXPIRATION:
                            return message.getExpiration();
                        case IMMEDIATE:
                            return message.isImmediate();
                        case MANDATORY:
                            return (delvProps == null || !delvProps.getDiscardUnroutable())
                                   && xfr.getAcceptMode() == MessageAcceptMode.EXPLICIT;
                        case PERSISTENT:
                            return message.isPersistent();
                        case REDELIVERED:
                            return delvProps.getRedelivered();
                    }
                    return null;
                }
            };

            int enqueues = serverSession.enqueue(message, instanceProperties, exchange);

            if (enqueues == 0)
            {
                if ((delvProps == null || !delvProps.getDiscardUnroutable())
                    && xfr.getAcceptMode() == MessageAcceptMode.EXPLICIT)
                {
                    RangeSet rejects = RangeSetFactory.createRangeSet();
                    rejects.add(xfr.getId());
                    MessageReject reject = new MessageReject(rejects, MessageRejectCode.UNROUTABLE, "Unroutable");
                    ssn.invoke(reject);
                }
                else
                {
                    virtualHost.getEventLogger().message(ExchangeMessages.DISCARDMSG(exchange.getName(),
                                                                                     messageMetaData.getRoutingKey()));
                }
            }

            if (serverSession.isTransactional())
            {
                serverSession.processed(xfr);
            }
            else
            {
                serverSession.recordFuture(StoreFuture.IMMEDIATE_FUTURE,
                                           new CommandProcessedAction(serverSession, xfr));
            }
            reference.release();
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

        ConsumerTarget_0_10 sub = ((ServerSession)session).getSubscription(destination);

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

        ConsumerTarget_0_10 sub = ((ServerSession)session).getSubscription(destination);

        if(sub == null)
        {
            exception(session, method, ExecutionErrorCode.NOT_FOUND, "not-found: destination '"+destination+"'");
        }
        else
        {
            sub.flush();
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
        catch(StoreException e)
        {
            exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, e.getMessage());
            throw e;
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
        catch(StoreException e)
        {
            exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, e.getMessage());
            throw e;
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
        catch(StoreException e)
        {
            exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, e.getMessage());
            throw e;
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
        VirtualHostImpl virtualHost = getVirtualHost(session);

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
        if(nameNullOrEmpty(method.getExchange()))
        {
            // special case handling to fake the existence of the default exchange for 0-10
            if(!ExchangeDefaults.DIRECT_EXCHANGE_CLASS.equals(method.getType()))
            {
                exception(session, method, ExecutionErrorCode.NOT_ALLOWED,
                          "Attempt to redeclare default exchange "
                          + " of type " + ExchangeDefaults.DIRECT_EXCHANGE_CLASS
                          + " to " + method.getType() +".");
            }
            if(!nameNullOrEmpty(method.getAlternateExchange()))
            {
                exception(session, method, ExecutionErrorCode.NOT_ALLOWED,
                          "Attempt to set alternate exchange of the default exchange "
                          + " to " + method.getAlternateExchange() +".");
            }
        }
        else
        {
            if(method.getPassive())
            {

                ExchangeImpl exchange = getExchange(session, exchangeName);

                if(exchange == null)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "not-found: exchange-name '" + exchangeName + "'");
                }
                else
                {
                    if (!exchange.getType().equals(method.getType())
                            && (method.getType() != null && method.getType().length() > 0))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Attempt to redeclare exchange: "
                                + exchangeName + " of type " + exchange.getType() + " to " + method.getType() + ".");
                    }
                }
            }
            else
            {

                try
                {
                    Map<String,Object> attributes = new HashMap<String, Object>();

                    attributes.put(org.apache.qpid.server.model.Exchange.ID, null);
                    attributes.put(org.apache.qpid.server.model.Exchange.NAME, method.getExchange());
                    attributes.put(org.apache.qpid.server.model.Exchange.TYPE, method.getType());
                    attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, method.getDurable());
                    attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                                   method.getAutoDelete() ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
                    attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, method.getAlternateExchange());
                    virtualHost.createExchange(attributes);
                }
                catch(ReservedExchangeNameException e)
                {
                    exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Attempt to declare exchange: "
                                                + exchangeName + " which begins with reserved name or prefix.");
                }
                catch(UnknownConfiguredObjectException e)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND,
                                                                "Unknown alternate exchange " + e.getName());
                }
                catch(NoFactoryForTypeException e)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "Unknown Exchange Type: " + method.getType());
                }
                catch(ExchangeExistsException e)
                {
                    ExchangeImpl exchange = e.getExistingExchange();
                    if(!exchange.getType().equals(method.getType()))
                    {
                        exception(session, method, ExecutionErrorCode.NOT_ALLOWED,
                                "Attempt to redeclare exchange: " + exchangeName
                                        + " of type " + exchange.getType()
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
                catch (AccessControlException e)
                {
                    exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
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

        ((ServerSession)session).close(errorCode.getValue(), description);
    }

    private ExchangeImpl getExchange(Session session, String exchangeName)
    {
        return getVirtualHost(session).getExchange(exchangeName);
    }

    private MessageDestination getDestinationForMessage(Session ssn, MessageTransfer xfr)
    {
        VirtualHostImpl virtualHost = getVirtualHost(ssn);

        MessageDestination destination;
        if(xfr.hasDestination())
        {
            destination = virtualHost.getMessageDestination(xfr.getDestination());
            if(destination == null)
            {
                destination = virtualHost.getDefaultDestination();
            }
        }
        else
        {
            destination = virtualHost.getDefaultDestination();
        }
        return destination;
    }

    private VirtualHostImpl<?,?,?> getVirtualHost(Session session)
    {
        ServerConnection conn = getServerConnection(session);
        return conn.getVirtualHost();
    }

    private ServerConnection getServerConnection(Session session)
    {
        return (ServerConnection) session.getConnection();
    }

    @Override
    public void exchangeDelete(Session session, ExchangeDelete method)
    {
        VirtualHostImpl virtualHost = getVirtualHost(session);

        try
        {
            if (nameNullOrEmpty(method.getExchange()))
            {
                exception(session, method, ExecutionErrorCode.INVALID_ARGUMENT, "Delete not allowed for default exchange");
                return;
            }

            ExchangeImpl exchange = getExchange(session, method.getExchange());

            if(exchange == null)
            {
                exception(session, method, ExecutionErrorCode.NOT_FOUND, "No such exchange '" + method.getExchange() + "'");
            }
            else
            {
                virtualHost.removeExchange(exchange, !method.getIfUnused());
            }
        }
        catch (ExchangeIsAlternateException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Exchange in use as an alternate exchange");
        }
        catch (RequiredExchangeException e)
        {
            exception(session, method, ExecutionErrorCode.NOT_ALLOWED, "Exchange '"+method.getExchange()+"' cannot be deleted");
        }
        catch (AccessControlException e)
        {
            exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
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

    @Override
    public void exchangeQuery(Session session, ExchangeQuery method)
    {

        ExchangeQueryResult result = new ExchangeQueryResult();


        final String exchangeName = method.getName();

        if(nameNullOrEmpty(exchangeName))
        {
            // Fake the existence of the "default" exchange for 0-10
            result.setDurable(true);
            result.setType(ExchangeDefaults.DIRECT_EXCHANGE_CLASS);
            result.setNotFound(false);
        }
        else
        {
            ExchangeImpl exchange = getExchange(session, exchangeName);

            if(exchange != null)
            {
                result.setDurable(exchange.isDurable());
                result.setType(exchange.getType());
                result.setNotFound(false);
            }
            else
            {
                result.setNotFound(true);
            }
        }
        session.executionResult((int) method.getId(), result);
    }

    @Override
    public void exchangeBind(Session session, ExchangeBind method)
    {

        VirtualHostImpl virtualHost = getVirtualHost(session);

        if (!method.hasQueue())
        {
            exception(session, method, ExecutionErrorCode.ILLEGAL_ARGUMENT, "queue not set");
        }
        else
        {
            final String exchangeName = method.getExchange();
            if (nameNullOrEmpty(exchangeName))
            {
                exception(session, method, ExecutionErrorCode.INVALID_ARGUMENT, "Bind not allowed for default exchange");
            }
            else
            {
                //TODO - here because of non-compliant python tests
                // should raise exception ILLEGAL_ARGUMENT "binding-key not set"
                if (!method.hasBindingKey())
                {
                    method.setBindingKey(method.getQueue());
                }
                AMQQueue queue = virtualHost.getQueue(method.getQueue());
                ExchangeImpl exchange = virtualHost.getExchange(exchangeName);
                if(queue == null)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "Queue: '" + method.getQueue() + "' not found");
                }
                else if(exchange == null)
                {
                    exception(session, method, ExecutionErrorCode.NOT_FOUND, "Exchange: '" + exchangeName + "' not found");
                }
                else if(exchange.getType().equals(ExchangeDefaults.HEADERS_EXCHANGE_CLASS) && (!method.hasArguments() || method.getArguments() == null || !method.getArguments().containsKey("x-match")))
                {
                    exception(session, method, ExecutionErrorCode.INTERNAL_ERROR, "Bindings to an exchange of type " + ExchangeDefaults.HEADERS_EXCHANGE_CLASS + " require an x-match header");
                }
                else
                {
                    if (!exchange.isBound(method.getBindingKey(), method.getArguments(), queue))
                    {
                        try
                        {
                            exchange.addBinding(method.getBindingKey(), queue, method.getArguments());
                        }
                        catch (AccessControlException e)
                        {
                            exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
                        }
                    }
                    else
                    {
                        // todo
                    }
                }


            }
        }



    }

    @Override
    public void exchangeUnbind(Session session, ExchangeUnbind method)
    {
        VirtualHostImpl virtualHost = getVirtualHost(session);

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
            AMQQueue queue = virtualHost.getQueue(method.getQueue());
            ExchangeImpl exchange = virtualHost.getExchange(method.getExchange());
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
                    if(exchange.hasBinding(method.getBindingKey(), queue))
                    {
                        exchange.deleteBinding(method.getBindingKey(), queue);
                    }
                }
                catch (AccessControlException e)
                {
                    exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
                }
            }
        }
    }

    @Override
    public void exchangeBound(Session session, ExchangeBound method)
    {

        ExchangeBoundResult result = new ExchangeBoundResult();
        VirtualHostImpl virtualHost = getVirtualHost(session);
        ExchangeImpl exchange;
        AMQQueue queue;
        boolean isDefaultExchange;
        if(!nameNullOrEmpty(method.getExchange()))
        {
            isDefaultExchange = false;
            exchange = virtualHost.getExchange(method.getExchange());

            if(exchange == null)
            {
                result.setExchangeNotFound(true);
            }
        }
        else
        {
            isDefaultExchange = true;
            exchange = null;
        }

        if(isDefaultExchange)
        {
            // fake the existence of the "default" exchange for 0-10
            if(method.hasQueue())
            {
                queue = getQueue(session, method.getQueue());

                if(queue == null)
                {
                    result.setQueueNotFound(true);
                }
                else
                {
                    if(method.hasBindingKey())
                    {
                        if(!method.getBindingKey().equals(method.getQueue()))
                        {
                            result.setKeyNotMatched(true);
                        }
                    }
                }
            }
            else if(method.hasBindingKey())
            {
                if(getQueue(session, method.getBindingKey()) == null)
                {
                    result.setKeyNotMatched(true);
                }
            }

            if(method.hasArguments() && !method.getArguments().isEmpty())
            {
                result.setArgsNotMatched(true);
            }


        }
        else if(method.hasQueue())
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
                        final boolean keyMatched = exchange.isBound(method.getBindingKey(), queue);
                        result.setKeyNotMatched(!keyMatched);
                        if(method.hasArguments())
                        {
                            if(keyMatched)
                            {
                                result.setArgsNotMatched(!exchange.isBound(method.getBindingKey(), method.getArguments(), queue));
                            }
                            else
                            {
                                result.setArgsNotMatched(!exchange.isBound(method.getArguments(), queue));
                            }
                        }
                    }
                    else
                    {
                        boolean keyMatched = exchange.isBound(method.getBindingKey());
                        result.setKeyNotMatched(!keyMatched);
                        if(method.hasArguments())
                        {
                            if(keyMatched)
                            {
                                result.setArgsNotMatched(!exchange.isBound(method.getBindingKey(), method.getArguments()));
                            }
                            else
                            {
                                result.setArgsNotMatched(!exchange.isBound(method.getArguments()));
                            }
                        }
                    }

                }
                else if (method.hasArguments())
                {
                    if(queueMatched)
                    {
                        result.setArgsNotMatched(!exchange.isBound(method.getArguments(), queue));
                    }
                    else
                    {
                        result.setArgsNotMatched(!exchange.isBound(method.getArguments()));
                    }
                }

            }
            else if(exchange != null && method.hasBindingKey())
            {
                final boolean keyMatched = exchange.isBound(method.getBindingKey());
                result.setKeyNotMatched(!keyMatched);

                if(method.hasArguments())
                {
                    if(keyMatched)
                    {
                        result.setArgsNotMatched(!exchange.isBound(method.getBindingKey(), method.getArguments()));
                    }
                    else
                    {
                        result.setArgsNotMatched(!exchange.isBound(method.getArguments()));
                    }
                }


            }

        }
        else if(exchange != null && method.hasBindingKey())
        {
            final boolean keyMatched = exchange.isBound(method.getBindingKey());
            result.setKeyNotMatched(!keyMatched);

            if(method.hasArguments())
            {
                if(keyMatched)
                {
                    result.setArgsNotMatched(!exchange.isBound(method.getBindingKey(), method.getArguments()));
                }
                else
                {
                    result.setArgsNotMatched(!exchange.isBound(method.getArguments()));
                }
            }

        }
        else if(exchange != null && method.hasArguments())
        {
            result.setArgsNotMatched(!exchange.isBound(method.getArguments()));
        }


        session.executionResult((int) method.getId(), result);


    }

    private AMQQueue getQueue(Session session, String queue)
    {
        return getVirtualHost(session).getQueue(queue);
    }

    @Override
    public void queueDeclare(Session session, final QueueDeclare method)
    {

        final VirtualHostImpl virtualHost = getVirtualHost(session);
        DurableConfigurationStore store = virtualHost.getDurableConfigurationStore();

        String queueName = method.getQueue();
        AMQQueue queue;
        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?

        final boolean exclusive = method.getExclusive();
        final boolean autoDelete = method.getAutoDelete();

        if(method.getPassive())
        {
            queue = virtualHost.getQueue(queueName);

            if (queue == null)
            {
                String description = "Queue: " + queueName + " not found on VirtualHost(" + virtualHost + ").";
                ExecutionErrorCode errorCode = ExecutionErrorCode.NOT_FOUND;

                exception(session, method, errorCode, description);

            }
            else if (!verifySessionAccess((ServerSession) session, queue))
            {
                String description = "Cannot declare queue('" + queueName + "'),"
                                                                       + " as exclusive queue with same name "
                                                                       + "declared on another session";
                ExecutionErrorCode errorCode = ExecutionErrorCode.RESOURCE_LOCKED;

                exception(session, method, errorCode, description);

            }
        }
        else
        {

            try
            {

                final String alternateExchangeName = method.getAlternateExchange();


                final Map<String, Object> arguments = QueueArgumentsConverter.convertWireArgsToModel(method.getArguments());

                if(alternateExchangeName != null && alternateExchangeName.length() != 0)
                {
                    arguments.put(Queue.ALTERNATE_EXCHANGE, alternateExchangeName);
                }

                final UUID id = UUID.randomUUID();

                arguments.put(Queue.ID, id);
                arguments.put(Queue.NAME, queueName);

                LifetimePolicy lifetime;
                if(autoDelete)
                {
                    lifetime = exclusive ? LifetimePolicy.DELETE_ON_SESSION_END
                            : LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS;
                }
                else
                {
                    lifetime = LifetimePolicy.PERMANENT;
                }

                arguments.put(Queue.LIFETIME_POLICY, lifetime);

                ExclusivityPolicy exclusivityPolicy = exclusive ? ExclusivityPolicy.SESSION : ExclusivityPolicy.NONE;


                arguments.put(Queue.DURABLE, method.getDurable());

                arguments.put(Queue.EXCLUSIVE, exclusivityPolicy);

                queue = virtualHost.createQueue(arguments);

            }
            catch(QueueExistsException qe)
            {
                queue = qe.getExistingQueue();
                if (!verifySessionAccess((ServerSession) session, queue))
                {
                    String description = "Cannot declare queue('" + queueName + "'),"
                                                                           + " as exclusive queue with same name "
                                                                           + "declared on another session";
                    ExecutionErrorCode errorCode = ExecutionErrorCode.RESOURCE_LOCKED;

                    exception(session, method, errorCode, description);
                }
            }
            catch (AccessControlException e)
            {
                exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
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
                if(!verifySessionAccess((ServerSession) session, queue))
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
                    VirtualHostImpl virtualHost = getVirtualHost(session);

                    try
                    {
                        virtualHost.removeQueue(queue);
                    }
                    catch (AccessControlException e)
                    {
                        exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
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
                catch (AccessControlException e)
                {
                    exception(session, method, ExecutionErrorCode.UNAUTHORIZED_ACCESS, e.getMessage());
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
            result.setQueue(queue.getName());
            result.setDurable(queue.isDurable());
            result.setExclusive(queue.isExclusive());
            result.setAutoDelete(queue.getLifetimePolicy() != LifetimePolicy.PERMANENT);
            Map<String, Object> arguments = new LinkedHashMap<String, Object>();
            Collection<String> availableAttrs = queue.getAvailableAttributes();

            for(String attrName : availableAttrs)
            {
                arguments.put(attrName, queue.getAttribute(attrName));
            }
            result.setArguments(QueueArgumentsConverter.convertModelArgsToWire(arguments));
            result.setMessageCount(queue.getQueueDepthMessages());
            result.setSubscriberCount(queue.getConsumerCount());

        }


        session.executionResult((int) method.getId(), result);

    }

    @Override
    public void messageSetFlowMode(Session session, MessageSetFlowMode sfm)
    {
        String destination = sfm.getDestination();

        ConsumerTarget_0_10 sub = ((ServerSession)session).getSubscription(destination);

        if(sub == null)
        {
            exception(session, sfm, ExecutionErrorCode.NOT_FOUND, "not-found: destination '" + destination + "'");
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

        ConsumerTarget_0_10 sub = ((ServerSession)session).getSubscription(destination);

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

        ConsumerTarget_0_10 sub = ((ServerSession)session).getSubscription(destination);

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
