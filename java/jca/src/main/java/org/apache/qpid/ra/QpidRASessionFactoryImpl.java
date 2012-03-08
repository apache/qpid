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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.XAQueueSession;
import javax.jms.XASession;
import javax.jms.XATopicSession;
import javax.naming.Reference;
import javax.resource.Referenceable;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the JMS Connection API and produces {@link QpidRASessionImpl} objects.
 *
 */
public class QpidRASessionFactoryImpl implements QpidRASessionFactory, Referenceable
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRASessionFactoryImpl.class);

   /** Are we closed? */
   private boolean _closed = false;

   /** The naming reference */
   private Reference _reference;

   /** The user name */
   private String _userName;

   /** The password */
   private String _password;

   /** The client ID */
   private String _clientID;

   /** The connection type */
   private final int _type;

   /** Whether we are started */
   private boolean _started = false;

   /** The managed connection factory */
   private final QpidRAManagedConnectionFactory _mcf;

   /** The connection manager */
   private ConnectionManager _cm;

   /** The sessions */
   private final Set<QpidRASession> _sessions = new HashSet<QpidRASession>();

   /** The temporary queues */
   private final Set<TemporaryQueue> _tempQueues = new HashSet<TemporaryQueue>();

   /** The temporary topics */
   private final Set<TemporaryTopic> _tempTopics = new HashSet<TemporaryTopic>();

   /**
    * Constructor
    * @param mcf The managed connection factory
    * @param cm The connection manager
    * @param type The connection type
    */
   public QpidRASessionFactoryImpl(final QpidRAManagedConnectionFactory mcf,
                                      final ConnectionManager cm,
                                      final int type)
   {
      this._mcf = mcf;

      if (cm == null)
      {
         this._cm = new QpidRAConnectionManager();
      }
      else
      {
         this._cm = cm;
      }

      this._type = type;

      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + mcf + ", " + cm + ", " + type);
      }
   }

   /**
    * Set the naming reference
    * @param reference The reference
    */
   public void setReference(final Reference reference)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setReference(" + reference + ")");
      }

      this._reference = reference;
   }

   /**
    * Get the naming reference
    * @return The reference
    */
   public Reference getReference()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getReference()");
      }

      return _reference;
   }

   /**
    * Set the user name
    * @param name The user name
    */
   public void setUserName(final String name)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setUserName(" + name + ")");
      }

      _userName = name;
   }

   /**
    * Set the password
    * @param password The password
    */
   public void setPassword(final String password)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPassword(****)");
      }

      this._password = password;
   }

   /**
    * Get the client ID
    * @return The client ID
    * @exception JMSException Thrown if an error occurs
    */
   public String getClientID() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getClientID()");
      }

      checkClosed();

      if (_clientID == null)
      {
         try
         {
            return _mcf.getDefaultAMQConnectionFactory().getConnectionURL().getClientName() ;
         }
         catch (final ResourceException re)
         {
            final JMSException jmse = new JMSException("Unexpected exception obtaining resource") ;
            jmse.initCause(re) ;
            throw jmse ;
         }
      }

      return _clientID;
   }

   /**
    * Set the client ID -- throws IllegalStateException
    * @param cID The client ID
    * @exception JMSException Thrown if an error occurs
    */
   public void setClientID(final String cID) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setClientID(" + cID + ")");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Create a queue session
    * @param transacted Use transactions
    * @param acknowledgeMode The acknowledge mode
    * @return The queue session
    * @exception JMSException Thrown if an error occurs
    */
   public QueueSession createQueueSession(final boolean transacted, final int acknowledgeMode) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createQueueSession(" + transacted + ", " + acknowledgeMode + ")");
      }

      checkClosed();

      if (_type == QpidRAConnectionFactory.TOPIC_CONNECTION || _type == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Can not get a queue session from a topic connection");
      }

      return (QueueSession)allocateConnection(transacted, acknowledgeMode, _type);
   }

   /**
    * Create a XA queue session
    * @return The XA queue session
    * @exception JMSException Thrown if an error occurs
    */
   public XAQueueSession createXAQueueSession() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createXAQueueSession()");
      }

      checkClosed();

      if (_type == QpidRAConnectionFactory.CONNECTION || _type == QpidRAConnectionFactory.TOPIC_CONNECTION ||
          _type == QpidRAConnectionFactory.XA_TOPIC_CONNECTION)
      {
         throw new IllegalStateException("Can not get a topic session from a queue connection");
      }

      return (XAQueueSession) allocateConnection(_type);
   }

   /**
    * Create a connection consumer -- throws IllegalStateException
    * @param queue The queue
    * @param messageSelector The message selector
    * @param sessionPool The session pool
    * @param maxMessages The number of max messages
    * @return The connection consumer
    * @exception JMSException Thrown if an error occurs
    */
   public ConnectionConsumer createConnectionConsumer(final Queue queue,
                                                      final String messageSelector,
                                                      final ServerSessionPool sessionPool,
                                                      final int maxMessages) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createConnectionConsumer(" + queue +
                                               ", " +
                                               messageSelector +
                                               ", " +
                                               sessionPool +
                                               ", " +
                                               maxMessages +
                                               ")");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Create a topic session
    * @param transacted Use transactions
    * @param acknowledgeMode The acknowledge mode
    * @return The topic session
    * @exception JMSException Thrown if an error occurs
    */
   public TopicSession createTopicSession(final boolean transacted, final int acknowledgeMode) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createTopicSession(" + transacted + ", " + acknowledgeMode + ")");
      }

      checkClosed();

      if (_type == QpidRAConnectionFactory.QUEUE_CONNECTION || _type == QpidRAConnectionFactory.XA_QUEUE_CONNECTION)
      {
         throw new IllegalStateException("Can not get a topic session from a queue connection");
      }

      return (TopicSession) allocateConnection(transacted, acknowledgeMode, _type);
   }

   /**
    * Create a XA topic session
    * @return The XA topic session
    * @exception JMSException Thrown if an error occurs
    */
   public XATopicSession createXATopicSession() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createXATopicSession()");
      }

      checkClosed();

      if (_type == QpidRAConnectionFactory.CONNECTION || _type == QpidRAConnectionFactory.QUEUE_CONNECTION ||
          _type == QpidRAConnectionFactory.XA_QUEUE_CONNECTION)
      {
         throw new IllegalStateException("Can not get a topic session from a queue connection");
      }

      return (XATopicSession) allocateConnection(_type);
   }

   /**
    * Create a connection consumer -- throws IllegalStateException
    * @param topic The topic
    * @param messageSelector The message selector
    * @param sessionPool The session pool
    * @param maxMessages The number of max messages
    * @return The connection consumer
    * @exception JMSException Thrown if an error occurs
    */
   public ConnectionConsumer createConnectionConsumer(final Topic topic,
                                                      final String messageSelector,
                                                      final ServerSessionPool sessionPool,
                                                      final int maxMessages) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createConnectionConsumer(" + topic +
                                               ", " +
                                               messageSelector +
                                               ", " +
                                               sessionPool +
                                               ", " +
                                               maxMessages +
                                               ")");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Create a durable connection consumer -- throws IllegalStateException
    * @param topic The topic
    * @param subscriptionName The subscription name
    * @param messageSelector The message selector
    * @param sessionPool The session pool
    * @param maxMessages The number of max messages
    * @return The connection consumer
    * @exception JMSException Thrown if an error occurs
    */
   public ConnectionConsumer createDurableConnectionConsumer(final Topic topic,
                                                             final String subscriptionName,
                                                             final String messageSelector,
                                                             final ServerSessionPool sessionPool,
                                                             final int maxMessages) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createConnectionConsumer(" + topic +
                                               ", " +
                                               subscriptionName +
                                               ", " +
                                               messageSelector +
                                               ", " +
                                               sessionPool +
                                               ", " +
                                               maxMessages +
                                               ")");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Create a connection consumer -- throws IllegalStateException
    * @param destination The destination
    * @param pool The session pool
    * @param maxMessages The number of max messages
    * @return The connection consumer
    * @exception JMSException Thrown if an error occurs
    */
   public ConnectionConsumer createConnectionConsumer(final Destination destination,
                                                      final ServerSessionPool pool,
                                                      final int maxMessages) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createConnectionConsumer(" + destination +
                                               ", " +
                                               pool +
                                               ", " +
                                               maxMessages +
                                               ")");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Create a connection consumer -- throws IllegalStateException
    * @param destination The destination
    * @param name The name
    * @param pool The session pool
    * @param maxMessages The number of max messages
    * @return The connection consumer
    * @exception JMSException Thrown if an error occurs
    */
   public ConnectionConsumer createConnectionConsumer(final Destination destination,
                                                      final String name,
                                                      final ServerSessionPool pool,
                                                      final int maxMessages) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createConnectionConsumer(" + destination +
                                               ", " +
                                               name +
                                               ", " +
                                               pool +
                                               ", " +
                                               maxMessages +
                                               ")");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Create a session
    * @param transacted Use transactions
    * @param acknowledgeMode The acknowledge mode
    * @return The session
    * @exception JMSException Thrown if an error occurs
    */
   public Session createSession(final boolean transacted, final int acknowledgeMode) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createSession(" + transacted + ", " + acknowledgeMode + ")");
      }

      checkClosed();
      return (Session) allocateConnection(transacted, acknowledgeMode, _type);
   }

   /**
    * Create a XA session
    * @return The XA session
    * @exception JMSException Thrown if an error occurs
    */
   public XASession createXASession() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createXASession()");
      }

      checkClosed();
      return (XASession) allocateConnection(_type);
   }

   /**
    * Get the connection metadata
    * @return The connection metadata
    * @exception JMSException Thrown if an error occurs
    */
   public ConnectionMetaData getMetaData() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMetaData()");
      }

      checkClosed();
      return _mcf.getMetaData();
   }

   /**
    * Get the exception listener -- throws IllegalStateException
    * @return The exception listener
    * @exception JMSException Thrown if an error occurs
    */
   public ExceptionListener getExceptionListener() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getExceptionListener()");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Set the exception listener -- throws IllegalStateException
    * @param listener The exception listener
    * @exception JMSException Thrown if an error occurs
    */
   public void setExceptionListener(final ExceptionListener listener) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setExceptionListener(" + listener + ")");
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Start
    * @exception JMSException Thrown if an error occurs
    */
   public void start() throws JMSException
   {
      checkClosed();

      if (_log.isTraceEnabled())
      {
         _log.trace("start() " + this);
      }

      synchronized (_sessions)
      {
         if (_started)
         {
            return;
         }
         _started = true;
         for (Iterator<QpidRASession> i = _sessions.iterator(); i.hasNext();)
         {
            QpidRASession session = (QpidRASession)i.next();
            session.start();
         }
      }
   }

   /**
    * Stop -- throws IllegalStateException
    * @exception JMSException Thrown if an error occurs
    */
   public void stop() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("stop() " + this);
      }

      throw new IllegalStateException(QpidRASessionFactory.ISE);
   }

   /**
    * Close
    * @exception JMSException Thrown if an error occurs
    */
   public void close() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("close() " + this);
      }

      if (_closed)
      {
         return;
      }

      _closed = true;

      synchronized (_sessions)
      {
         for (Iterator<QpidRASession> i = _sessions.iterator(); i.hasNext();)
         {
            QpidRASession session = (QpidRASession)i.next();
            try
            {
               session.closeSession();
            }
            catch (Throwable t)
            {
               _log.trace("Error closing session", t);
            }
            i.remove();
         }
      }

      synchronized (_tempQueues)
      {
         for (Iterator<TemporaryQueue> i = _tempQueues.iterator(); i.hasNext();)
         {
            TemporaryQueue temp = (TemporaryQueue)i.next();
            try
            {
               if (_log.isTraceEnabled())
               {
                  _log.trace("Closing temporary queue " + temp + " for " + this);
               }
               temp.delete();
            }
            catch (Throwable t)
            {
               _log.trace("Error deleting temporary queue", t);
            }
            i.remove();
         }
      }

      synchronized (_tempTopics)
      {
         for (Iterator<TemporaryTopic> i = _tempTopics.iterator(); i.hasNext();)
         {
            TemporaryTopic temp = (TemporaryTopic)i.next();
            try
            {
               if (_log.isTraceEnabled())
               {
                  _log.trace("Closing temporary topic " + temp + " for " + this);
               }
               temp.delete();
            }
            catch (Throwable t)
            {
               _log.trace("Error deleting temporary queue", t);
            }
            i.remove();
         }
      }
   }

   /**
    * Close session
    * @param session The session
    * @exception JMSException Thrown if an error occurs
    */
   public void closeSession(final QpidRASession session) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("closeSession(" + session + ")");
      }

      synchronized (_sessions)
      {
         _sessions.clear();
      }
   }

   /**
    * Add temporary queue
    * @param temp The temporary queue
    */
   public void addTemporaryQueue(final TemporaryQueue temp)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("addTemporaryQueue(" + temp + ")");
      }

      synchronized (_tempQueues)
      {
         _tempQueues.add(temp);
      }
   }

   /**
    * Add temporary topic
    * @param temp The temporary topic
    */
   public void addTemporaryTopic(final TemporaryTopic temp)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("addTemporaryTopic(" + temp + ")");
      }

      synchronized (_tempTopics)
      {
         _tempTopics.add(temp);
      }
   }

   /**
    * Allocation a connection
    * @param sessionType The session type
    * @return The session
    * @exception JMSException Thrown if an error occurs
    */
   protected QpidRASession allocateConnection(final int sessionType) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("allocateConnection(" + sessionType + ")");
      }

      try
      {
         synchronized (_sessions)
         {
            if (_sessions.isEmpty() == false)
            {
               throw new IllegalStateException("Only allowed one session per connection. See the J2EE spec, e.g. J2EE1.4 Section 6.6");
            }

            QpidRAConnectionRequestInfo info = new QpidRAConnectionRequestInfo(sessionType);
            info.setUserName(_userName);
            info.setPassword(_password);
            info.setClientID(_clientID);
            info.setDefaults(_mcf.getDefaultAMQConnectionFactory().getConnectionURL());

            if (_log.isTraceEnabled())
            {
               _log.trace("Allocating session for " + this + " with request info=" + info);
            }

            QpidRASession session = (QpidRASession)_cm.allocateConnection(_mcf, info);

            try
            {
               if (_log.isTraceEnabled())
               {
                  _log.trace("Allocated  " + this + " session=" + session);
               }

               session.setQpidSessionFactory(this);

               if (_started)
               {
                  session.start();
               }

               _sessions.add(session);

               return session;
            }
            catch (Throwable t)
            {
               try
               {
                  session.close();
               }
               catch (Throwable ignored)
               {
               }
               if (t instanceof Exception)
               {
                  throw (Exception)t;
               }
               else
               {
                  throw new RuntimeException("Unexpected error: ", t);
               }
            }
         }
      }
      catch (Exception e)
      {
         _log.error("Could not create session", e);

         JMSException je = new JMSException("Could not create a session: " + e.getMessage());
         je.setLinkedException(e);
         throw je;
      }
   }

   /**
    * Allocation a connection
    * @param transacted Use transactions
    * @param acknowledgeMode The acknowledge mode
    * @param sessionType The session type
    * @return The session
    * @exception JMSException Thrown if an error occurs
    */
   protected QpidRASession allocateConnection(final boolean transacted, int acknowledgeMode, final int sessionType) throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("allocateConnection(" + transacted +
                                               ", " +
                                               acknowledgeMode +
                                               ", " +
                                               sessionType +
                                               ")");
      }

      try
      {
         synchronized (_sessions)
         {
            if (_sessions.isEmpty() == false)
            {
               throw new IllegalStateException("Only allowed one session per connection. See the J2EE spec, e.g. J2EE1.4 Section 6.6");
            }

            if (transacted)
            {
               acknowledgeMode = Session.SESSION_TRANSACTED;
            }

            QpidRAConnectionRequestInfo info = new QpidRAConnectionRequestInfo(transacted,
                                                                                     acknowledgeMode,
                                                                                     sessionType);
            info.setUserName(_userName);
            info.setPassword(_password);
            info.setClientID(_clientID);
            info.setDefaults(_mcf.getDefaultAMQConnectionFactory().getConnectionURL());

            if (_log.isTraceEnabled())
            {
               _log.trace("Allocating session for " + this + " with request info=" + info);
            }

            QpidRASession session = (QpidRASession)_cm.allocateConnection(_mcf, info);

            try
            {
               if (_log.isTraceEnabled())
               {
                  _log.trace("Allocated  " + this + " session=" + session);
               }

               session.setQpidSessionFactory(this);

               if (_started)
               {
                  session.start();
               }

               _sessions.add(session);

               return session;
            }
            catch (Throwable t)
            {
               try
               {
                  session.close();
               }
               catch (Throwable ignored)
               {
               }
               if (t instanceof Exception)
               {
                  throw (Exception)t;
               }
               else
               {
                  throw new RuntimeException("Unexpected error: ", t);
               }
            }
         }
      }
      catch (Exception e)
      {
         _log.error("Could not create session", e);

         JMSException je = new JMSException("Could not create a session: " + e.getMessage());
         je.setLinkedException(e);
         throw je;
      }
   }

   /**
    * Check if we are closed
    * @exception IllegalStateException Thrown if closed
    */
   protected void checkClosed() throws IllegalStateException
   {
      if (_closed)
      {
         throw new IllegalStateException("The connection is closed");
      }
   }
}
