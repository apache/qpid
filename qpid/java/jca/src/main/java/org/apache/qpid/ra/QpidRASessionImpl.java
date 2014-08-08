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

package org.apache.qpid.ra;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.jms.TransactionInProgressException;
import javax.jms.XAQueueSession;
import javax.jms.XASession;
import javax.jms.XATopicSession;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ManagedConnection;
import javax.transaction.xa.XAResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A joint interface for JMS sessions
 *
 */
public class QpidRASessionImpl implements Session, QueueSession, TopicSession, XASession, XAQueueSession, XATopicSession, QpidRASession
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRASessionImpl.class);

   /** The managed connection */
   private volatile QpidRAManagedConnection _mc;
   /** The locked managed connection */
   private QpidRAManagedConnection _lockedMC;

   /** The connection request info */
   private final QpidRAConnectionRequestInfo _cri;

   /** The session factory */
   private QpidRASessionFactory _sf;

   /** The message consumers */
   private final Set<MessageConsumer> _consumers;

   /** The message producers */
   private final Set<MessageProducer> _producers;

   /** The queue browsers */
   private final Set<QueueBrowser> _browsers;

   /** Are we started */
   private AtomicBoolean _started = new AtomicBoolean(false) ;

   /**
    * Constructor
    * @param mc The managed connection
    * @param cri The connection request info
    */
   public QpidRASessionImpl(final QpidRAManagedConnection mc, final QpidRAConnectionRequestInfo cri)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + mc + ", " + cri + ")");
      }

      this._mc = mc;
      this._cri = cri;
      _sf = null;
      _consumers = new HashSet<MessageConsumer>();
      _producers = new HashSet<MessageProducer>();
      _browsers = new HashSet<QueueBrowser>();
   }

   /**
    * Set the session factory
    * @param sf The session factory
    */
   public void setQpidSessionFactory(final QpidRASessionFactory sf)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setQpidSessionFactory(" + sf + ")");
      }

      _started.set(false) ;
      this._sf = sf;
   }

   /**
    * Lock
    * @exception JMSException Thrown if an error occurs
    * @exception IllegalStateException The session is closed
    */
   protected void lock() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("lock()");
      }

      final QpidRAManagedConnection mc = this._mc;
      if (mc != null)
      {
         mc.tryLock();
         _lockedMC = mc ;
      }
      else
      {
         throw new IllegalStateException("Connection is not associated with a managed connection. " + this);
      }
   }

   /**
    * Unlock
    */
   protected void unlock()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("unlock()");
      }

      if (_lockedMC != null)
      {
         try
         {
            _lockedMC.unlock();
         }
         finally
         {
            _lockedMC = null ;
         }
      }

      // We recreate the lock when returned to the pool
      // so missing the unlock after disassociation is not important
   }

   /**
    * Create a bytes message
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public BytesMessage createBytesMessage() throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createBytesMessage" + Util.asString(session));
      }

      return session.createBytesMessage();
   }

   /**
    * Create a map message
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public MapMessage createMapMessage() throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createMapMessage" + Util.asString(session));
      }

      return session.createMapMessage();
   }

   /**
    * Create a message
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public Message createMessage() throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createMessage" + Util.asString(session));
      }

      return session.createMessage();
   }

   /**
    * Create an object message
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public ObjectMessage createObjectMessage() throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createObjectMessage" + Util.asString(session));
      }

      return session.createObjectMessage();
   }

   /**
    * Create an object message
    * @param object The object
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public ObjectMessage createObjectMessage(final Serializable object) throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createObjectMessage(" + object + ")" + Util.asString(session));
      }

      return session.createObjectMessage(object);
   }

   /**
    * Create a stream message
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public StreamMessage createStreamMessage() throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createStreamMessage" + Util.asString(session));
      }

      return session.createStreamMessage();
   }

   /**
    * Create a text message
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public TextMessage createTextMessage() throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createTextMessage" + Util.asString(session));
      }

      return session.createTextMessage();
   }

   /**
    * Create a text message
    * @param string The text
    * @return The message
    * @exception JMSException Thrown if an error occurs
    */
   public TextMessage createTextMessage(final String string) throws JMSException
   {
      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createTextMessage(" + string + ")" + Util.asString(session));
      }

      return session.createTextMessage(string);
   }

   /**
    * Get transacted
    * @return True if transacted; otherwise false
    * @exception JMSException Thrown if an error occurs
    */
   public boolean getTransacted() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTransacted()");
      }

      getSessionInternal();
      return _cri.isTransacted();
   }

   /**
    * Get the message listener -- throws IllegalStateException
    * @return The message listener
    * @exception JMSException Thrown if an error occurs
    */
   public MessageListener getMessageListener() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMessageListener()");
      }

      throw new IllegalStateException("Method not allowed");
   }

   /**
    * Set the message listener -- Throws IllegalStateException
    * @param listener The message listener
    * @exception JMSException Thrown if an error occurs
    */
   public void setMessageListener(final MessageListener listener) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setMessageListener(" + listener + ")");
      }

      throw new IllegalStateException("Method not allowed");
   }

   /**
    * Always throws an Error.
    * @exception Error Method not allowed.
    */
   public void run()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("run()");
      }

      throw new Error("Method not allowed");
   }

   /**
    * Closes the session. Sends a ConnectionEvent.CONNECTION_CLOSED to the
    * managed connection.
    * @exception JMSException Failed to close session.
    */
   public void close() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("close()");
      }

      _sf.closeSession(this);
      closeSession();
   }

   /**
    * Commit
    * @exception JMSException Failed to close session.
    */
   public void commit() throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.XA_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_QUEUE_CONNECTION ||
          _cri.getType() == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new TransactionInProgressException("XA connection");
      }

      lock();
      try
      {

         if (_cri.isTransacted() == false)
         {
            throw new IllegalStateException("Session is not transacted");
         }

         if(_lockedMC.isConnectionClosed())
         {
             throw new IllegalStateException("Attempting to call commit when the underlying connection has been closed.");
         }

         if (_log.isTraceEnabled())
         {
            _log.trace("Commit session " + this);
         }

         getSessionInternal().commit();

      }
      finally
      {
         unlock();
      }
   }

   /**
    * Rollback
    * @exception JMSException Failed to close session.
    */
   public void rollback() throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.XA_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_QUEUE_CONNECTION ||
          _cri.getType() == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new TransactionInProgressException("XA connection");
      }

      lock();
      try
      {
         Session session = getSessionInternal();

         if (_cri.isTransacted() == false)
         {
            throw new IllegalStateException("Session is not transacted");
         }

         if (_log.isTraceEnabled())
         {
            _log.trace("Rollback session " + this);
         }

         session.rollback();
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Recover
    * @exception JMSException Failed to close session.
    */
   public void recover() throws JMSException
   {
      lock();
      try
      {
         Session session = getSessionInternal();

         if (_cri.isTransacted())
         {
            throw new IllegalStateException("Session is transacted");
         }

         if (_log.isTraceEnabled())
         {
            _log.trace("Recover session " + this);
         }

         session.recover();
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a topic
    * @param topicName The topic name
    * @return The topic
    * @exception JMSException Thrown if an error occurs
    */
   public Topic createTopic(final String topicName) throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_QUEUE_CONNECTION)
      {
         throw new IllegalStateException("Cannot create topic for javax.jms.QueueSession");
      }

      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createTopic " + Util.asString(session) + " topicName=" + topicName);
      }

      Topic result = session.createTopic(topicName);

      if (_log.isTraceEnabled())
      {
         _log.trace("createdTopic " + Util.asString(session) + " topic=" + result);
      }

      return result;
   }

   /**
    * Create a topic subscriber
    * @param topic The topic
    * @return The subscriber
    * @exception JMSException Thrown if an error occurs
    */
   public TopicSubscriber createSubscriber(final Topic topic) throws JMSException
   {
      lock();
      try
      {
         TopicSession session = getTopicSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createSubscriber " + Util.asString(session) + " topic=" + topic);
         }

         TopicSubscriber result = session.createSubscriber(topic);
         result = new QpidRATopicSubscriber(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdSubscriber " + Util.asString(session) + " QpidRATopicSubscriber=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a topic subscriber
    * @param topic The topic
    * @param messageSelector The message selector
    * @param noLocal If true inhibits the delivery of messages published by its own connection
    * @return The subscriber
    * @exception JMSException Thrown if an error occurs
    */
   public TopicSubscriber createSubscriber(final Topic topic, final String messageSelector, final boolean noLocal) throws JMSException
   {
      lock();
      try
      {
         TopicSession session = getTopicSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createSubscriber " + Util.asString(session) +
                                       " topic=" +
                                       topic +
                                       " selector=" +
                                       messageSelector +
                                       " noLocal=" +
                                       noLocal);
         }

         TopicSubscriber result = session.createSubscriber(topic, messageSelector, noLocal);
         result = new QpidRATopicSubscriber(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdSubscriber " + Util.asString(session) + " QpidRATopicSubscriber=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a durable topic subscriber
    * @param topic The topic
    * @param name The name
    * @return The subscriber
    * @exception JMSException Thrown if an error occurs
    */
   public TopicSubscriber createDurableSubscriber(final Topic topic, final String name) throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_QUEUE_CONNECTION)
      {
         throw new IllegalStateException("Cannot create durable subscriber from javax.jms.QueueSession");
      }

      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createDurableSubscriber " + Util.asString(session) + " topic=" + topic + " name=" + name);
         }

         TopicSubscriber result = session.createDurableSubscriber(topic, name);
         result = new QpidRATopicSubscriber(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdDurableSubscriber " + Util.asString(session) + " QpidRATopicSubscriber=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a topic subscriber
    * @param topic The topic
    * @param name The name
    * @param messageSelector The message selector
    * @param noLocal If true inhibits the delivery of messages published by its own connection
    * @return The subscriber
    * @exception JMSException Thrown if an error occurs
    */
   public TopicSubscriber createDurableSubscriber(final Topic topic,
                                                  final String name,
                                                  final String messageSelector,
                                                  final boolean noLocal) throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_QUEUE_CONNECTION)
      {
         throw new IllegalStateException("Cannot create durable subscriber from javax.jms.QueueSession");
      }

      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createDurableSubscriber " + Util.asString(session) +
                                       " topic=" +
                                       topic +
                                       " name=" +
                                       name +
                                       " selector=" +
                                       messageSelector +
                                       " noLocal=" +
                                       noLocal);
         }

         TopicSubscriber result = session.createDurableSubscriber(topic, name, messageSelector, noLocal);
         result = new QpidRATopicSubscriber(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdDurableSubscriber " + Util.asString(session) + " QpidRATopicSubscriber=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a topic publisher
    * @param topic The topic
    * @return The publisher
    * @exception JMSException Thrown if an error occurs
    */
   public TopicPublisher createPublisher(final Topic topic) throws JMSException
   {
      lock();
      try
      {
         TopicSession session = getTopicSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createPublisher " + Util.asString(session) + " topic=" + topic);
         }

         TopicPublisher result = session.createPublisher(topic);
         result = new QpidRATopicPublisher(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdPublisher " + Util.asString(session) + " publisher=" + result);
         }

         addProducer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a temporary topic
    * @return The temporary topic
    * @exception JMSException Thrown if an error occurs
    */
   public TemporaryTopic createTemporaryTopic() throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_QUEUE_CONNECTION)
      {
         throw new IllegalStateException("Cannot create temporary topic for javax.jms.QueueSession");
      }

      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createTemporaryTopic " + Util.asString(session));
         }

         TemporaryTopic temp = session.createTemporaryTopic();

         if (_log.isTraceEnabled())
         {
            _log.trace("createdTemporaryTopic " + Util.asString(session) + " temp=" + temp);
         }

         _sf.addTemporaryTopic(temp);

         return temp;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Unsubscribe
    * @param name The name
    * @exception JMSException Thrown if an error occurs
    */
   public void unsubscribe(final String name) throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_QUEUE_CONNECTION)
      {
         throw new IllegalStateException("Cannot unsubscribe for javax.jms.QueueSession");
      }

      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("unsubscribe " + Util.asString(session) + " name=" + name);
         }

         session.unsubscribe(name);
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a browser
    * @param queue The queue
    * @return The browser
    * @exception JMSException Thrown if an error occurs
    */
   public QueueBrowser createBrowser(final Queue queue) throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Cannot create browser for javax.jms.TopicSession");
      }

      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createBrowser " + Util.asString(session) + " queue=" + queue);
      }

      QueueBrowser result = session.createBrowser(queue);

      if (_log.isTraceEnabled())
      {
         _log.trace("createdBrowser " + Util.asString(session) + " browser=" + result);
      }

      return result;
   }

   /**
    * Create a browser
    * @param queue The queue
    * @param messageSelector The message selector
    * @return The browser
    * @exception JMSException Thrown if an error occurs
    */
   public QueueBrowser createBrowser(final Queue queue, final String messageSelector) throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Cannot create browser for javax.jms.TopicSession");
      }

      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createBrowser " + Util.asString(session) + " queue=" + queue + " selector=" + messageSelector);
      }

      QueueBrowser result = session.createBrowser(queue, messageSelector);
      result = new QpidRAQueueBrowser(result, this);
      addQueueBrowser(result) ;

      if (_log.isTraceEnabled())
      {
         _log.trace("createdBrowser " + Util.asString(session) + " browser=" + result);
      }

      return result;
   }

   /**
    * Create a queue
    * @param queueName The queue name
    * @return The queue
    * @exception JMSException Thrown if an error occurs
    */
   public Queue createQueue(final String queueName) throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Cannot create browser or javax.jms.TopicSession");
      }

      Session session = getSessionInternal();

      if (_log.isTraceEnabled())
      {
         _log.trace("createQueue " + Util.asString(session) + " queueName=" + queueName);
      }

      Queue result = session.createQueue(queueName);

      if (_log.isTraceEnabled())
      {
         _log.trace("createdQueue " + Util.asString(session) + " queue=" + result);
      }

      return result;
   }

   /**
    * Create a queue receiver
    * @param queue The queue
    * @return The queue receiver
    * @exception JMSException Thrown if an error occurs
    */
   public QueueReceiver createReceiver(final Queue queue) throws JMSException
   {
      lock();
      try
      {
         QueueSession session = getQueueSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createReceiver " + Util.asString(session) + " queue=" + queue);
         }

         QueueReceiver result = session.createReceiver(queue);
         result = new QpidRAQueueReceiver(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdReceiver " + Util.asString(session) + " receiver=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a queue receiver
    * @param queue The queue
    * @param messageSelector
    * @return The queue receiver
    * @exception JMSException Thrown if an error occurs
    */
   public QueueReceiver createReceiver(final Queue queue, final String messageSelector) throws JMSException
   {
      lock();
      try
      {
         QueueSession session = getQueueSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createReceiver " + Util.asString(session) + " queue=" + queue + " selector=" + messageSelector);
         }

         QueueReceiver result = session.createReceiver(queue, messageSelector);
         result = new QpidRAQueueReceiver(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdReceiver " + Util.asString(session) + " receiver=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a queue sender
    * @param queue The queue
    * @return The queue sender
    * @exception JMSException Thrown if an error occurs
    */
   public QueueSender createSender(final Queue queue) throws JMSException
   {
      lock();
      try
      {
         QueueSession session = getQueueSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createSender " + Util.asString(session) + " queue=" + queue);
         }

         QueueSender result = session.createSender(queue);
         result = new QpidRAQueueSender(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdSender " + Util.asString(session) + " sender=" + result);
         }

         addProducer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a temporary queue
    * @return The temporary queue
    * @exception JMSException Thrown if an error occurs
    */
   public TemporaryQueue createTemporaryQueue() throws JMSException
   {
      if (_cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION || _cri.getType() == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Cannot create temporary queue for javax.jms.TopicSession");
      }

      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createTemporaryQueue " + Util.asString(session));
         }

         TemporaryQueue temp = session.createTemporaryQueue();

         if (_log.isTraceEnabled())
         {
            _log.trace("createdTemporaryQueue " + Util.asString(session) + " temp=" + temp);
         }

         _sf.addTemporaryQueue(temp);

         return temp;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a message consumer
    * @param destination The destination
    * @return The message consumer
    * @exception JMSException Thrown if an error occurs
    */
   public MessageConsumer createConsumer(final Destination destination) throws JMSException
   {
      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createConsumer " + Util.asString(session) + " dest=" + destination);
         }

         MessageConsumer result = session.createConsumer(destination);
         result = new QpidRAMessageConsumer(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdConsumer " + Util.asString(session) + " consumer=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a message consumer
    * @param destination The destination
    * @param messageSelector The message selector
    * @return The message consumer
    * @exception JMSException Thrown if an error occurs
    */
   public MessageConsumer createConsumer(final Destination destination, final String messageSelector) throws JMSException
   {
      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createConsumer " + Util.asString(session) +
                                       " dest=" +
                                       destination +
                                       " messageSelector=" +
                                       messageSelector);
         }

         MessageConsumer result = session.createConsumer(destination, messageSelector);
         result = new QpidRAMessageConsumer(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdConsumer " + Util.asString(session) + " consumer=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a message consumer
    * @param destination The destination
    * @param messageSelector The message selector
    * @param noLocal If true inhibits the delivery of messages published by its own connection
    * @return The message consumer
    * @exception JMSException Thrown if an error occurs
    */
   public MessageConsumer createConsumer(final Destination destination,
                                         final String messageSelector,
                                         final boolean noLocal) throws JMSException
   {
      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createConsumer " + Util.asString(session) +
                                       " dest=" +
                                       destination +
                                       " messageSelector=" +
                                       messageSelector +
                                       " noLocal=" +
                                       noLocal);
         }

         MessageConsumer result = session.createConsumer(destination, messageSelector, noLocal);
         result = new QpidRAMessageConsumer(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdConsumer " + Util.asString(session) + " consumer=" + result);
         }

         addConsumer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Create a message producer
    * @param destination The destination
    * @return The message producer
    * @exception JMSException Thrown if an error occurs
    */
   public MessageProducer createProducer(final Destination destination) throws JMSException
   {
      lock();
      try
      {
         Session session = getSessionInternal();

         if (_log.isTraceEnabled())
         {
            _log.trace("createProducer " + Util.asString(session) + " dest=" + destination);
         }

         MessageProducer result = session.createProducer(destination);
         result = new QpidRAMessageProducer(result, this);

         if (_log.isTraceEnabled())
         {
            _log.trace("createdProducer " + Util.asString(session) + " producer=" + result);
         }

         addProducer(result);

         return result;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Get the acknowledge mode
    * @return The mode
    * @exception JMSException Thrown if an error occurs
    */
   public int getAcknowledgeMode() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getAcknowledgeMode()");
      }

      getSessionInternal();
      return _cri.getAcknowledgeMode();
   }

   /**
    * Get the XA resource
    * @return The XA resource
    */
   public XAResource getXAResource()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getXAResource()");
      }

      if (_cri.getType() == QpidRAConnectionFactory.CONNECTION || _cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION ||
          _cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION)
      {
         return null;
      }

      try
      {
         lock();

         return getXAResourceInternal();
      }
      catch (Throwable t)
      {
         return null;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Get the session
    * @return The session
    * @exception JMSException Thrown if an error occurs
    */
   public Session getSession() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSession()");
      }

      if (_cri.getType() == QpidRAConnectionFactory.CONNECTION || _cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION ||
          _cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Non XA connection");
      }

      lock();
      try
      {
         return this;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Get the queue session
    * @return The queue session
    * @exception JMSException Thrown if an error occurs
    */
   public QueueSession getQueueSession() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getQueueSession()");
      }

      if (_cri.getType() == QpidRAConnectionFactory.CONNECTION || _cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION ||
          _cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Non XA connection");
      }

      lock();
      try
      {
         return this;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Get the topic session
    * @return The topic session
    * @exception JMSException Thrown if an error occurs
    */
   public TopicSession getTopicSession() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTopicSession()");
      }

      if (_cri.getType() == QpidRAConnectionFactory.CONNECTION || _cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION ||
          _cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Non XA connection");
      }

      lock();
      try
      {
         return this;
      }
      finally
      {
         unlock();
      }
   }

   /**
    * Set the managed connection
    * @param managedConnection The managed connection
    */
   void setManagedConnection(final QpidRAManagedConnection managedConnection)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setManagedConnection(" + managedConnection + ")");
      }

      final QpidRAManagedConnection mc = this._mc;
      if (mc != null)
      {
         mc.removeHandle(this);
      }

      this._mc = managedConnection;
   }

   /** for tests only */
   public ManagedConnection getManagedConnection()
   {
      return _mc;
   }

   /**
    * Destroy
    */
   void destroy()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("destroy()");
      }

      _mc = null;
   }

   /**
    * Start
    * @exception JMSException Thrown if an error occurs
    */
   public void start() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("start()");
      }

      final QpidRAManagedConnection mc = this._mc;
      if (mc != null)
      {
         _started.set(true) ;
         mc.start();
      }
   }

   /**
    * Stop
    * @exception JMSException Thrown if an error occurs
    */
   void stop() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("stop()");
      }

      final QpidRAManagedConnection mc = this._mc;
      if (mc != null)
      {
         mc.stop();
         _started.set(false) ;
      }
   }

   /**
    * Check strict
    * @exception JMSException Thrown if an error occurs
    */
   void checkStrict() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("checkStrict()");
      }

      if (_mc != null)
      {
         throw new IllegalStateException(QpidRASessionFactory.ISE);
      }
   }

   /**
    * Close session
    * @exception JMSException Thrown if an error occurs
    */
   public void closeSession() throws JMSException
   {
      final QpidRAManagedConnection mc = this._mc;
      if (mc != null)
      {
         _log.trace("Closing session");

         try
         {
            mc.stop();
         }
         catch (Throwable t)
         {
            _log.trace("Error stopping managed connection", t);
         }

         synchronized (_consumers)
         {
            for (Iterator<MessageConsumer> i = _consumers.iterator(); i.hasNext();)
            {
               QpidRAMessageConsumer consumer = (QpidRAMessageConsumer)i.next();
               try
               {
                  consumer.closeConsumer();
               }
               catch (Throwable t)
               {
                  _log.trace("Error closing consumer", t);
               }
               i.remove();
            }
         }

         synchronized (_producers)
         {
            for (Iterator<MessageProducer> i = _producers.iterator(); i.hasNext();)
            {
               QpidRAMessageProducer producer = (QpidRAMessageProducer)i.next();
               try
               {
                  producer.closeProducer();
               }
               catch (Throwable t)
               {
                  _log.trace("Error closing producer", t);
               }
               i.remove();
            }
         }

         synchronized (_browsers)
         {
            for (Iterator<QueueBrowser> i = _browsers.iterator(); i.hasNext();)
            {
               QpidRAQueueBrowser browser = (QpidRAQueueBrowser)i.next();
               try
               {
                  browser.close();
               }
               catch (Throwable t)
               {
                  _log.trace("Error closing browser", t);
               }
               i.remove();
            }
         }

         mc.removeHandle(this);
         ConnectionEvent ev = new ConnectionEvent(mc, ConnectionEvent.CONNECTION_CLOSED);
         ev.setConnectionHandle(this);
         mc.sendEvent(ev);
         this._mc = null;
      }
   }

   /**
    * Add consumer
    * @param consumer The consumer
    */
   void addConsumer(final MessageConsumer consumer)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("addConsumer(" + consumer + ")");
      }

      synchronized (_consumers)
      {
         _consumers.add(consumer);
      }
   }

   /**
    * Remove consumer
    * @param consumer The consumer
    */
   void removeConsumer(final MessageConsumer consumer)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("removeConsumer(" + consumer + ")");
      }

      synchronized (_consumers)
      {
         _consumers.remove(consumer);
      }
   }

   /**
    * Add producer
    * @param producer The producer
    */
   void addProducer(final MessageProducer producer)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("addProducer(" + producer + ")");
      }

      synchronized (_producers)
      {
         _producers.add(producer);
      }
   }

   /**
    * Remove producer
    * @param producer The producer
    */
   void removeProducer(final MessageProducer producer)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("removeProducer(" + producer + ")");
      }

      synchronized (_producers)
      {
         _producers.remove(producer);
      }
   }

   /**
    * Add queue browser
    * @param browser The queue browser
    */
   void addQueueBrowser(final QueueBrowser browser)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("addQueueBrowser(" + browser + ")");
      }

      synchronized (_browsers)
      {
         _browsers.add(browser);
      }
   }

   /**
    * Remove queue browser
    * @param browser The queue browser
    */
   void removeQueueBrowser(final QueueBrowser browser)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("removeQueueBrowser(" + browser + ")");
      }

      synchronized (_browsers)
      {
         _browsers.remove(browser);
      }
   }

   /**
    * Get the session and ensure that it is open
    * @return The session
    * @exception JMSException Thrown if an error occurs
    * @exception IllegalStateException The session is closed
    */
   Session getSessionInternal() throws JMSException
   {
      final QpidRAManagedConnection mc = this._mc;
      if (mc == null)
      {
         throw new IllegalStateException("The session is closed");
      }

      Session session = mc.getSession();

      if (_log.isTraceEnabled())
      {
         _log.trace("getSessionInternal " + Util.asString(session) + " for " + this);
      }

      return session;
   }

   /**
    * Get the XA resource and ensure that it is open
    * @return The XA Resource
    * @exception JMSException Thrown if an error occurs
    * @exception IllegalStateException The session is closed
    */
   XAResource getXAResourceInternal() throws JMSException
   {
      final QpidRAManagedConnection mc = this._mc;
      if (mc == null)
      {
         throw new IllegalStateException("The session is closed");
      }

      try
      {
         XAResource xares = mc.getXAResource();

         if (_log.isTraceEnabled())
         {
            _log.trace("getXAResourceInternal " + xares + " for " + this);
         }

         return xares;
      }
      catch (ResourceException e)
      {
         JMSException jmse = new JMSException("Unable to get XA Resource");
         jmse.initCause(e);
         throw jmse;
      }
   }

   /**
    * Get the queue session
    * @return The queue session
    * @exception JMSException Thrown if an error occurs
    * @exception IllegalStateException The session is closed
    */
   QueueSession getQueueSessionInternal() throws JMSException
   {
      Session s = getSessionInternal();
      if (!(s instanceof QueueSession))
      {
         throw new InvalidDestinationException("Attempting to use QueueSession methods on: " + this);
      }
      return (QueueSession)s;
   }

   /**
    * Get the topic session
    * @return The topic session
    * @exception JMSException Thrown if an error occurs
    * @exception IllegalStateException The session is closed
    */
   TopicSession getTopicSessionInternal() throws JMSException
   {
      Session s = getSessionInternal();
      if (!(s instanceof TopicSession))
      {
         throw new InvalidDestinationException("Attempting to use TopicSession methods on: " + this);
      }
      return (TopicSession)s;
   }

   /**
    * throws SystemException
    * throws RollbackException
    *
    */
   public void checkState() throws JMSException
   {
      final QpidRAManagedConnection mc = this._mc;
      if (mc != null)
      {
         mc.checkTransactionActive();
      }
   }

   /**
    * Has this session been started?
    * @return true if started, false if stopped.
    */
   public boolean isStarted()
   {
      return _started.get();
   }
}
