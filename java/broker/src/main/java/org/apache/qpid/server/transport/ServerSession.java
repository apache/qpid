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

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.*;
import static org.apache.qpid.util.Serial.*;

import java.lang.ref.WeakReference;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ConnectionConfig;
import org.apache.qpid.server.configuration.SessionConfig;
import org.apache.qpid.server.configuration.SessionConfigType;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.security.PrincipalHolder;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Binary;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.security.auth.UserPrincipal;

public class ServerSession extends Session implements PrincipalHolder, SessionConfig, AMQSessionModel, LogSubject
{
    private static final Logger _logger = LoggerFactory.getLogger(ServerSession.class);
    
    private static final String NULL_DESTINTATION = UUID.randomUUID().toString();

    private final UUID _id;
    private ConnectionConfig _connectionConfig;
    private long _createTime = System.currentTimeMillis();
    private LogActor _actor = GenericActor.getInstance(this);

    public static interface MessageDispositionChangeListener
    {
        public void onAccept();

        public void onRelease();

        public void onReject();

        public boolean acquire();


    }

    public static interface Task
    {
        public void doTask(ServerSession session);
    }


    private final SortedMap<Integer, MessageDispositionChangeListener> _messageDispositionListenerMap =
            new ConcurrentSkipListMap<Integer, MessageDispositionChangeListener>();

    private ServerTransaction _transaction;
    
    private final AtomicLong _txnStarts = new AtomicLong(0);
    private final AtomicLong _txnCommits = new AtomicLong(0);
    private final AtomicLong _txnRejects = new AtomicLong(0);
    private final AtomicLong _txnCount = new AtomicLong(0);
    private final AtomicLong _txnUpdateTime = new AtomicLong(0);

    private Principal _principal;

    private Map<String, Subscription_0_10> _subscriptions = new ConcurrentHashMap<String, Subscription_0_10>();

    private final List<Task> _taskList = new CopyOnWriteArrayList<Task>();

    private final WeakReference<Session> _reference;

    ServerSession(Connection connection, SessionDelegate delegate, Binary name, long expiry)
    {
        this(connection, delegate, name, expiry, ((ServerConnection)connection).getConfig());
    }

    protected void setState(State state)
    {
        super.setState(state);

        if (state == State.OPEN)
        {
	        _actor.message(ChannelMessages.CREATE());
        }
    }

    public ServerSession(Connection connection, SessionDelegate delegate, Binary name, long expiry, ConnectionConfig connConfig)
    {
        super(connection, delegate, name, expiry);
        _connectionConfig = connConfig;        
        _transaction = new AutoCommitTransaction(this.getMessageStore());
        _principal = new UserPrincipal(connection.getAuthorizationID());
        _reference = new WeakReference<Session>(this);
        _id = getConfigStore().createId();
        getConfigStore().addConfiguredObject(this);
    }

    private ConfigStore getConfigStore()
    {
        return getConnectionConfig().getConfigStore();
    }


    @Override
    protected boolean isFull(int id)
    {
        return isCommandsFull(id);
    }

    public void enqueue(final ServerMessage message, final ArrayList<? extends BaseQueue> queues)
    {
        getConnectionModel().registerMessageReceived(message.getSize(), message.getArrivalTime());
        _transaction.enqueue(queues,message, new ServerTransaction.Action()
            {

                BaseQueue[] _queues = queues.toArray(new BaseQueue[queues.size()]);

                public void postCommit()
                {
                    for(int i = 0; i < _queues.length; i++)
                    {
                        try
                        {
                            _queues[i].enqueue(message);
                        }
                        catch (AMQException e)
                        {
                            // TODO
                            throw new RuntimeException(e);
                        }
                    }
                }

                public void onRollback()
                {
                    // NO-OP
                }
            });

            incrementOutstandingTxnsIfNecessary();
            updateTransactionalActivity();
    }


    public void sendMessage(MessageTransfer xfr,
                            Runnable postIdSettingAction)
    {
        invoke(xfr, postIdSettingAction);
        getConnectionModel().registerMessageDelivered(xfr.getBodySize());
    }

    public void onMessageDispositionChange(MessageTransfer xfr, MessageDispositionChangeListener acceptListener)
    {
        _messageDispositionListenerMap.put(xfr.getId(), acceptListener);
    }


    private static interface MessageDispositionAction
    {
        void performAction(MessageDispositionChangeListener  listener);
    }

    public void accept(RangeSet ranges)
    {
        dispositionChange(ranges, new MessageDispositionAction()
                                      {
                                          public void performAction(MessageDispositionChangeListener listener)
                                          {
                                              listener.onAccept();
                                          }
                                      });
    }


    public void release(RangeSet ranges)
    {
        dispositionChange(ranges, new MessageDispositionAction()
                                      {
                                          public void performAction(MessageDispositionChangeListener listener)
                                          {
                                              listener.onRelease();
                                          }
                                      });
    }

    public void reject(RangeSet ranges)
    {
        dispositionChange(ranges, new MessageDispositionAction()
                                      {
                                          public void performAction(MessageDispositionChangeListener listener)
                                          {
                                              listener.onReject();
                                          }
                                      });
    }

    public RangeSet acquire(RangeSet transfers)
    {
        RangeSet acquired = new RangeSet();

        if(!_messageDispositionListenerMap.isEmpty())
        {
            Iterator<Integer> unacceptedMessages = _messageDispositionListenerMap.keySet().iterator();
            Iterator<Range> rangeIter = transfers.iterator();

            if(rangeIter.hasNext())
            {
                Range range = rangeIter.next();

                while(range != null && unacceptedMessages.hasNext())
                {
                    int next = unacceptedMessages.next();
                    while(gt(next, range.getUpper()))
                    {
                        if(rangeIter.hasNext())
                        {
                            range = rangeIter.next();
                        }
                        else
                        {
                            range = null;
                            break;
                        }
                    }
                    if(range != null && range.includes(next))
                    {
                        MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.get(next);
                        if(changeListener != null && changeListener.acquire())
                        {
                            acquired.add(next);
                        }
                    }


                }

            }


        }

        return acquired;
    }

    public void dispositionChange(RangeSet ranges, MessageDispositionAction action)
    {
        if(ranges != null && !_messageDispositionListenerMap.isEmpty())
        {
            Iterator<Integer> unacceptedMessages = _messageDispositionListenerMap.keySet().iterator();
            Iterator<Range> rangeIter = ranges.iterator();

            if(rangeIter.hasNext())
            {
                Range range = rangeIter.next();

                while(range != null && unacceptedMessages.hasNext())
                {
                    int next = unacceptedMessages.next();
                    while(gt(next, range.getUpper()))
                    {
                        if(rangeIter.hasNext())
                        {
                            range = rangeIter.next();
                        }
                        else
                        {
                            range = null;
                            break;
                        }
                    }
                    if(range != null && range.includes(next))
                    {
                        MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.remove(next);
                        action.performAction(changeListener);
                    }


                }

            }

        }
    }

    public void removeDispositionListener(Method method)                               
    {
        _messageDispositionListenerMap.remove(method.getId());
    }

    public void onClose()
    {
        _transaction.rollback();
        for(MessageDispositionChangeListener listener : _messageDispositionListenerMap.values())
        {
            listener.onRelease();
        }
        _messageDispositionListenerMap.clear();

        getConfigStore().removeConfiguredObject(this);

        for (Task task : _taskList)
        {
            task.doTask(this);
        }
        
        CurrentActor.get().message(getLogSubject(), ChannelMessages.CLOSE());
    }

    @Override
    protected void awaitClose()
    {
        // Broker shouldn't block awaiting close - thus do override this method to do nothing
    }

    public void acknowledge(final Subscription_0_10 sub, final QueueEntry entry)
    {
        _transaction.dequeue(entry.getQueue(), entry.getMessage(),
                             new ServerTransaction.Action()
                             {

                                 public void postCommit()
                                 {
                                     sub.acknowledge(entry);
                                 }

                                 public void onRollback()
                                 {
                                     entry.release();
                                 }
                             });
	    updateTransactionalActivity();
    }

    public Collection<Subscription_0_10> getSubscriptions()
    {
        return _subscriptions.values();
    }

    public void register(String destination, Subscription_0_10 sub)
    {
        _subscriptions.put(destination == null ? NULL_DESTINTATION : destination, sub);
    }

    public Subscription_0_10 getSubscription(String destination)
    {
        return _subscriptions.get(destination == null ? NULL_DESTINTATION : destination);
    }

    public void unregister(Subscription_0_10 sub)
    {
        _subscriptions.remove(sub.getConsumerTag().toString());
        try
        {
            sub.getSendLock();
            AMQQueue queue = sub.getQueue();
            if(queue != null)
            {
                queue.unregisterSubscription(sub);
            }

        }
        catch (AMQException e)
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        finally
        {
            sub.releaseSendLock();
        }
    }
    
    public boolean isTransactional()
    {
        // this does not look great but there should only be one "non-transactional"
        // transactional context, while there could be several transactional ones in
        // theory
        return !(_transaction instanceof AutoCommitTransaction);
    }
    
    public boolean inTransaction()
    {
        return isTransactional() && _txnUpdateTime.get() > 0 && _transaction.getTransactionStartTime() > 0;
    }

    public void selectTx()
    {
        _transaction = new LocalTransaction(this.getMessageStore());
        _txnStarts.incrementAndGet();
    }

    public void commit()
    {
        _transaction.commit();
        
        _txnCommits.incrementAndGet();
        _txnStarts.incrementAndGet();
        decrementOutstandingTxnsIfNecessary();
    }

    public void rollback()
    {
        _transaction.rollback();
        
        _txnRejects.incrementAndGet();
        _txnStarts.incrementAndGet();
        decrementOutstandingTxnsIfNecessary();
    }

    
    private void incrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 1 if 0.
            _txnCount.compareAndSet(0,1);
        }
    }
    
    private void decrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 0 if 1.
            _txnCount.compareAndSet(1,0);
        }
    }

    /**
     * Update last transaction activity timestamp
     */
    public void updateTransactionalActivity()
    {
        if (isTransactional())
        {
            _txnUpdateTime.set(System.currentTimeMillis());
        }
    }

    public Long getTxnStarts()
    {
        return _txnStarts.get();
    }

    public Long getTxnCommits()
    {
        return _txnCommits.get();
    }

    public Long getTxnRejects()
    {
        return _txnRejects.get();
    }

    public Long getTxnCount()
    {
        return _txnCount.get();
    }
    
    public Principal getPrincipal()
    {
        return _principal;
    }

    public void addSessionCloseTask(Task task)
    {
        _taskList.add(task);
    }

    public void removeSessionCloseTask(Task task)
    {
        _taskList.remove(task);
    }

    public WeakReference<Session> getReference()
     {
         return _reference;
     }

    public MessageStore getMessageStore()
    {
        return getVirtualHost().getMessageStore();
    }

    public VirtualHost getVirtualHost()
    {
        return (VirtualHost) _connectionConfig.getVirtualHost();
    }

    public UUID getId()
    {
        return _id;
    }

    public SessionConfigType getConfigType()
    {
        return SessionConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return getVirtualHost();
    }

    public boolean isDurable()
    {
        return false;
    }

    public boolean isAttached()
    {
        return true;
    }

    public long getDetachedLifespan()
    {
        return 0;
    }

    public Long getExpiryTime()
    {
        return null;
    }

    public Long getMaxClientRate()
    {
        return null;
    }

    public ConnectionConfig getConnectionConfig()
    {
        return _connectionConfig;
    }

    public String getSessionName()
    {
        return getName().toString();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public void mgmtClose()
    {
        close();
    }

    public Object getID()
    {
       return getName();
    }

    public AMQConnectionModel getConnectionModel()
    {
        return (ServerConnection) getConnection();
    }

    public String getClientID()
    {
        return getConnection().getClientId();
    }

    public LogActor getLogActor()
    {
        return _actor;
    }

    public LogSubject getLogSubject()
    {
        return (LogSubject) this;
    }

    public void checkTransactionStatus(long openWarn, long openClose, long idleWarn, long idleClose) throws AMQException
    {
        if (inTransaction())
        {
            long currentTime = System.currentTimeMillis();
            long openTime = currentTime - _transaction.getTransactionStartTime();
            long idleTime = currentTime - _txnUpdateTime.get();

            // Log a warning on idle or open transactions
            if (idleWarn > 0L && idleTime > idleWarn)
            {
                CurrentActor.get().message(getLogSubject(), ChannelMessages.IDLE_TXN(openTime));
                _logger.warn("IDLE TRANSACTION ALERT " + getLogSubject().toString() + " " + idleTime + " ms");
            }
            else if (openWarn > 0L && openTime > openWarn)
            {
                CurrentActor.get().message(getLogSubject(), ChannelMessages.OPEN_TXN(openTime));
                _logger.warn("OPEN TRANSACTION ALERT " + getLogSubject().toString() + " " + openTime + " ms");
            }

            // Close connection for idle or open transactions that have timed out
            if (idleClose > 0L && idleTime > idleClose)
            {
                getConnectionModel().closeSession(this, AMQConstant.RESOURCE_ERROR, "Idle transaction timed out");
            }
            else if (openClose > 0L && openTime > openClose)
            {
                getConnectionModel().closeSession(this, AMQConstant.RESOURCE_ERROR, "Open transaction timed out");
            }
        }
    }

    @Override
    public String toLogString()
    {
       return "[" +
               MessageFormat.format(CHANNEL_FORMAT,
                                   getConnection().getConnectionId(),
                                   getClientID(),
                                   ((ProtocolEngine) _connectionConfig).getRemoteAddress().toString(),
                                   getVirtualHost().getName(),
                                   getChannel())
            + "] ";
    }
}
