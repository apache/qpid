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
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.txn.Transaction;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.PrincipalHolder;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.AMQException;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.security.Principal;

import static org.apache.qpid.util.Serial.*;
import com.sun.security.auth.UserPrincipal;

public class ServerSession extends Session implements PrincipalHolder
{


    public static interface MessageDispositionChangeListener
    {
        public void onAccept();

        public void onRelease();

        public void onReject();
    }

    public static interface Task
    {
        public void doTask(ServerSession session);
    }


    private final SortedMap<Integer, MessageDispositionChangeListener> _messageDispositionListenerMap =
            new ConcurrentSkipListMap<Integer, MessageDispositionChangeListener>();

    private Transaction _transaction;

    private Principal _principal;

    private Map<String, Subscription_0_10> _subscriptions = new HashMap<String, Subscription_0_10>();

    private final List<Task> _taskList = new CopyOnWriteArrayList<Task>();


    ServerSession(Connection connection, Binary name, long expiry)
    {
        super(connection, name, expiry);
        _transaction = new AutoCommitTransaction();
        _principal = new UserPrincipal(connection.getAuthorizationID());
    }

    ServerSession(Connection connection, SessionDelegate delegate, Binary name, long expiry)
    {
        super(connection, delegate, name, expiry);
        _transaction = new AutoCommitTransaction();
    }

    public void enqueue(final ServerMessage message, ArrayList<AMQQueue> queues)
    {

        for(final AMQQueue q : queues)
        {
            _transaction.enqueue(q,message, new Transaction.Action()
            {

                public void postCommit()
                {
                    try
                    {
                        q.enqueue(message);
                    }
                    catch (AMQException e)
                    {
                        // TODO
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            });
        }

    }

    
    public void sendMessage(MessageTransfer xfr)
    {
        invoke(xfr);
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

    public void dispositionChange(RangeSet ranges, MessageDispositionAction action)
    {
        if(!_messageDispositionListenerMap.isEmpty())
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
        for(MessageDispositionChangeListener listener : _messageDispositionListenerMap.values())
        {
            listener.onRelease();
        }
        _messageDispositionListenerMap.clear();

        for (Task task : _taskList)
        {
            task.doTask(this);
        }

    }

    public void acknowledge(final Subscription_0_10 sub, final QueueEntry entry)
    {
        _transaction.dequeue(entry.getQueue(), entry.getMessage(),
                             new Transaction.Action()
                             {

                                 public void postCommit()
                                 {
                                     sub.acknowledge(entry);
                                 }
                             });

    }

    public Map<String, Subscription_0_10> getSubscriptions()
    {
        return _subscriptions;
    }

    public void register(String destination, Subscription_0_10 sub)
    {
        _subscriptions.put(destination, sub);
    }

    public Subscription_0_10 getSubscription(String destination)
    {
        return _subscriptions.get(destination);
    }

    public void unregister(Subscription_0_10 sub)
    {
        _subscriptions.remove(sub);
        try
        {
            sub.getSendLock();
            sub.getQueue().unregisterSubscription(sub);

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

    public void selectTx()
    {
        _transaction = new LocalTransaction();
    }

    public void commit()
    {
        _transaction.commit();
    }

    public void rollback()
    {
        _transaction.rollback();
    }

    void setPrincipal(Principal principal)
    {
        _principal = principal;
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

}
