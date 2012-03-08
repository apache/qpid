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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XASession;
import javax.jms.XATopicConnection;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.IllegalStateException;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.resource.spi.SecurityException;
import javax.security.auth.Subject;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

import org.apache.qpid.client.Closeable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The managed connection
 *
 */
public class QpidRAManagedConnection implements ManagedConnection, ExceptionListener
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAManagedConnection.class);

   /** The managed connection factory */
   private final QpidRAManagedConnectionFactory _mcf;

   /** The connection request information */
   private final QpidRAConnectionRequestInfo _cri;

   /** The user name */
   private final String _userName;

   /** The password */
   private final String _password;

   /** Has the connection been destroyed */
   private final AtomicBoolean _isDestroyed = new AtomicBoolean(false);

   /** Event listeners */
   private final List<ConnectionEventListener> _eventListeners;

   /** Handles */
   private final Set<QpidRASessionImpl> _handles;

   /** Lock */
   private ReentrantLock _lock = new ReentrantLock();

   // Physical JMS connection stuff
   private Connection _connection;

   private XASession _xaSession;

   private XAResource _xaResource;

   private Session _session;

   private final TransactionManager _tm;

   private boolean _inManagedTx;

   /**
    * Constructor
    * @param mcf The managed connection factory
    * @param cri The connection request information
    * @param userName The user name
    * @param password The password
    */
   public QpidRAManagedConnection(final QpidRAManagedConnectionFactory mcf,
                                     final QpidRAConnectionRequestInfo cri,
                                     final TransactionManager tm,
                                     final String userName,
                                     final String password) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + mcf + ", " + cri + ", " + userName + ", ****)");
      }

      this._mcf = mcf;
      this._cri = cri;
      this._tm = tm;
      this._userName = userName;
      this._password = password;
      _eventListeners = Collections.synchronizedList(new ArrayList<ConnectionEventListener>());
      _handles = Collections.synchronizedSet(new HashSet<QpidRASessionImpl>());

      try
      {
         setup();
      }
      catch (Throwable t)
      {
         try
         {
            destroy();
         }
         catch (Throwable ignored)
         {
         }
         throw new ResourceException("Error during setup", t);
      }
   }

   /**
    * Get a connection
    * @param subject The security subject
    * @param cxRequestInfo The request info
    * @return The connection
    * @exception ResourceException Thrown if an error occurs
    */
   public synchronized Object getConnection(final Subject subject, final ConnectionRequestInfo cxRequestInfo) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getConnection(" + subject + ", " + cxRequestInfo + ")");
      }

      // Check user first
      QpidRACredential credential = QpidRACredential.getCredential(_mcf, subject, cxRequestInfo);

      // Null users are allowed!
      if (_userName != null && !_userName.equals(credential.getUserName()))
      {
         throw new SecurityException("Password credentials not the same, reauthentication not allowed");
      }

      if (_userName == null && credential.getUserName() != null)
      {
         throw new SecurityException("Password credentials not the same, reauthentication not allowed");
      }

      if (_isDestroyed.get())
      {
         throw new IllegalStateException("The managed connection is already destroyed");
      }

      QpidRASessionImpl session = new QpidRASessionImpl(this, (QpidRAConnectionRequestInfo)cxRequestInfo);
      _handles.add(session);
      return session;
   }

   /**
    * Destroy all handles.
    * @exception ResourceException Failed to close one or more handles.
    */
   private void destroyHandles() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("destroyHandles()");
      }

      try
      {
         if (_connection != null)
         {
            _connection.stop();
         }
      }
      catch (Throwable t)
      {
         _log.trace("Ignored error stopping connection", t);
      }

      for (QpidRASessionImpl session : _handles)
      {
         session.destroy();
      }

      _handles.clear();
   }

   /**
    * Destroy the physical connection.
    * @exception ResourceException Could not property close the session and connection.
    */
   public void destroy() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("destroy()");
      }

      if (_isDestroyed.get() ||  _connection == null)
      {
         return;
      }

      _isDestroyed.set(true);

      try
      {
         _connection.setExceptionListener(null);
      }
      catch (JMSException e)
      {
         _log.debug("Error unsetting the exception listener " + this, e);
      }

      destroyHandles();

      try
      {
         try
         {
            if (_xaSession != null)
            {
               _xaSession.close();
            }
         }
         catch (JMSException e)
         {
            _log.debug("Error closing XASession " + this, e);
         }

         try
         {
             if(_session != null)
             {
                 _session.close();
             }

         }
         catch(JMSException e)
         {
             _log.error("Error closing Session " + this, e);
         }

         if (_connection != null)
         {
            _connection.close();
         }
      }
      catch (Throwable e)
      {
         throw new ResourceException("Could not properly close the session and connection", e);
      }
   }

   /**
    * Cleanup
    * @exception ResourceException Thrown if an error occurs
    */
   public void cleanup() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("cleanup()");
      }

      if (_isDestroyed.get())
      {
         throw new IllegalStateException("ManagedConnection already destroyed");
      }

      destroyHandles();

      _inManagedTx = false;

      // I'm recreating the lock object when we return to the pool
      // because it looks too nasty to expect the connection handle
      // to unlock properly in certain race conditions
      // where the dissociation of the managed connection is "random".
      _lock = new ReentrantLock();
   }

   /**
    * Move a handler from one mc to this one.
    * @param obj An object of type QpidRASession.
    * @throws ResourceException Failed to associate connection.
    * @throws IllegalStateException ManagedConnection in an illegal state.
    */
   public void associateConnection(final Object obj) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("associateConnection(" + obj + ")");
      }

      if (!_isDestroyed.get() && obj instanceof QpidRASessionImpl)
      {
         QpidRASessionImpl h = (QpidRASessionImpl)obj;
         h.setManagedConnection(this);
         _handles.add(h);
      }
      else
      {
         throw new IllegalStateException("ManagedConnection in an illegal state");
      }
   }

   public void checkTransactionActive() throws JMSException
   {
      // don't bother looking at the transaction if there's an active XID
      if (!_inManagedTx && _tm != null)
      {
         try
         {
            Transaction tx = _tm.getTransaction();
            if (tx != null)
            {
               int status = tx.getStatus();
               // Only allow states that will actually succeed
               if (status != Status.STATUS_ACTIVE && status != Status.STATUS_PREPARING &&
                   status != Status.STATUS_PREPARED &&
                   status != Status.STATUS_COMMITTING)
               {
                  throw new javax.jms.IllegalStateException("Transaction " + tx + " not active");
               }
            }
         }
         catch (SystemException e)
         {
            JMSException jmsE = new javax.jms.IllegalStateException("Unexpected exception on the Transaction ManagerTransaction");
            jmsE.initCause(e);
            throw jmsE;
         }
      }
   }


   /**
    * Aqquire a lock on the managed connection
    */
   protected void lock()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("lock()");
      }

      _lock.lock();
   }

   /**
    * Aqquire a lock on the managed connection within the specified period
    * @exception JMSException Thrown if an error occurs
    */
   protected void tryLock() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("tryLock()");
      }

      Integer tryLock = _mcf.getUseTryLock();
      if (tryLock == null || tryLock.intValue() <= 0)
      {
         lock();
         return;
      }
      try
      {
         if (_lock.tryLock(tryLock.intValue(), TimeUnit.SECONDS) == false)
         {
            throw new ResourceAllocationException("Unable to obtain lock in " + tryLock + " seconds: " + this);
         }
      }
      catch (InterruptedException e)
      {
         throw new ResourceAllocationException("Interrupted attempting lock: " + this);
      }
   }

   /**
    * Unlock the managed connection
    */
   protected void unlock()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("unlock()");
      }

      if (_lock.isHeldByCurrentThread())
      {
         _lock.unlock();
      }
   }

   /**
    * Add a connection event listener.
    * @param l The connection event listener to be added.
    */
   public void addConnectionEventListener(final ConnectionEventListener l)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("addConnectionEventListener(" + l + ")");
      }

      _eventListeners.add(l);
   }

   /**
    * Remove a connection event listener.
    * @param l The connection event listener to be removed.
    */
   public void removeConnectionEventListener(final ConnectionEventListener l)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("removeConnectionEventListener(" + l + ")");
      }

      _eventListeners.remove(l);
   }

   /**
    * Get the XAResource for the connection.
    * @return The XAResource for the connection.
    * @exception ResourceException XA transaction not supported
    */
   public XAResource getXAResource() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getXAResource()");
      }

      //
      // Spec says a mc must allways return the same XA resource,
      // so we cache it.
      //
      if (_xaResource == null)
      {
            _xaResource = new QpidRAXAResource(this, _xaSession.getXAResource());
      }

      if (_log.isTraceEnabled())
      {
         _log.trace("XAResource=" + _xaResource);
      }

      return _xaResource;
   }

   /**
    * Get the location transaction for the connection.
    * @return The local transaction for the connection.
    * @exception ResourceException Thrown if operation fails.
    */
   public LocalTransaction getLocalTransaction() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getLocalTransaction()");
      }

      LocalTransaction tx = new QpidRALocalTransaction(this);

      if (_log.isTraceEnabled())
      {
         _log.trace("LocalTransaction=" + tx);
      }

      return tx;
   }

   /**
    * Get the meta data for the connection.
    * @return The meta data for the connection.
    * @exception ResourceException Thrown if the operation fails.
    * @exception IllegalStateException Thrown if the managed connection already is destroyed.
    */
   public ManagedConnectionMetaData getMetaData() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMetaData()");
      }

      if (_isDestroyed.get())
      {
         throw new IllegalStateException("The managed connection is already destroyed");
      }

      return new QpidRAMetaData(this);
   }

   /**
    * Set the log writer -- NOT SUPPORTED
    * @param out The log writer
    * @exception ResourceException If operation fails
    */
   public void setLogWriter(final PrintWriter out) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setLogWriter(" + out + ")");
      }
   }

   /**
    * Get the log writer -- NOT SUPPORTED
    * @return Always null
    * @exception ResourceException If operation fails
    */
   public PrintWriter getLogWriter() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getLogWriter()");
      }

      return null;
   }

   /**
    * Notifies user of a JMS exception.
    * @param exception The JMS exception
    */
   public void onException(final JMSException exception)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("onException(" + exception + ")");
      }

      if (_isDestroyed.get())
      {
         if (_log.isTraceEnabled())
         {
            _log.trace("Ignoring error on already destroyed connection " + this, exception);
         }
         return;
      }

      _log.warn("Handling JMS exception failure: " + this, exception);

      try
      {
         _connection.setExceptionListener(null);
      }
      catch (JMSException e)
      {
         _log.debug("Unable to unset exception listener", e);
      }

      ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_ERROR_OCCURRED, exception);
      sendEvent(event);
   }

   /**
    * Get the session for this connection.
    * @return The session
    * @throws JMSException
    */
   protected Session getSession() throws JMSException
   {
      if(_xaSession != null && !_mcf.getUseLocalTx() && _inManagedTx)
      {
          if (_log.isTraceEnabled())
          {
              _log.trace("getSession() -> XA session " + Util.asString(_xaSession));
          }

          return _xaSession;
      }
      else
      {
          if (_log.isTraceEnabled())
          {
              _log.trace("getSession() -> session " + Util.asString(_session));
          }

          return _session;
      }
   }

   /**
    * Send an event.
    * @param event The event to send.
    */
   protected void sendEvent(final ConnectionEvent event)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("sendEvent(" + event + ")");
      }

      int type = event.getId();

      // convert to an array to avoid concurrent modification exceptions
      ConnectionEventListener[] list = _eventListeners.toArray(new ConnectionEventListener[_eventListeners.size()]);

      for (ConnectionEventListener l : list)
      {
         switch (type)
         {
            case ConnectionEvent.CONNECTION_CLOSED:
               l.connectionClosed(event);
               break;

            case ConnectionEvent.LOCAL_TRANSACTION_STARTED:
               l.localTransactionStarted(event);
               break;

            case ConnectionEvent.LOCAL_TRANSACTION_COMMITTED:
               l.localTransactionCommitted(event);
               break;

            case ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK:
               l.localTransactionRolledback(event);
               break;

            case ConnectionEvent.CONNECTION_ERROR_OCCURRED:
               l.connectionErrorOccurred(event);
               break;

            default:
               throw new IllegalArgumentException("Illegal eventType: " + type);
         }
      }
   }

   /**
    * Remove a handle from the handle map.
    * @param handle The handle to remove.
    */
   protected void removeHandle(final QpidRASessionImpl handle)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("removeHandle(" + handle + ")");
      }

      _handles.remove(handle);
   }

   /**
    * Get the request info for this connection.
    * @return The connection request info for this connection.
    */
   protected QpidRAConnectionRequestInfo getCRI()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getCRI()");
      }

      return _cri;
   }

   /**
    * Get the connection factory for this connection.
    * @return The connection factory for this connection.
    */
   protected QpidRAManagedConnectionFactory getManagedConnectionFactory()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getManagedConnectionFactory()");
      }

      return _mcf;
   }

   /**
    * Start the connection
    * @exception JMSException Thrown if the connection cant be started
    */
   void start() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("start()");
      }

      if (_connection != null)
      {
         _connection.start();
      }
   }

   /**
    * Stop the connection
    * @exception JMSException Thrown if the connection cant be stopped
    */
   void stop() throws JMSException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("stop()");
      }

      if (_connection != null)
      {
         _connection.stop();
      }
   }

   /**
    * Get the user name
    * @return The user name
    */
   protected String getUserName()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getUserName()");
      }

      return _userName;
   }

   /**
    * Setup the connection.
    * @exception ResourceException Thrown if a connection couldnt be created
    */
   private void setup() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setup()");
      }

      try
      {
         boolean transacted = _cri.isTransacted() || _mcf.getUseLocalTx();
         int acknowledgeMode =  (transacted) ? Session.SESSION_TRANSACTED : _cri.getAcknowledgeMode();

         if (_cri.getType() == QpidRAConnectionFactory.TOPIC_CONNECTION)
         {
            if (_userName != null && _password != null)
            {
                _connection = _mcf.getCleanAMQConnectionFactory().createXATopicConnection(_userName, _password);
            }
            else
            {
                _connection = _mcf.getDefaultAMQConnectionFactory().createXATopicConnection();
            }

            _xaSession = ((XATopicConnection)_connection).createXATopicSession();
            _session =  ((TopicConnection)_connection).createTopicSession(transacted, acknowledgeMode);

         }
         else if (_cri.getType() == QpidRAConnectionFactory.QUEUE_CONNECTION)
         {
            if (_userName != null && _password != null)
            {
                _connection = _mcf.getCleanAMQConnectionFactory().createXAQueueConnection(_userName, _password);
            }
            else
            {
                _connection = _mcf.getDefaultAMQConnectionFactory().createXAQueueConnection();
            }

            _xaSession = ((XAQueueConnection)_connection).createXAQueueSession();
            _session =  ((QueueConnection)_connection).createQueueSession(transacted, acknowledgeMode);

         }
         else
         {
            if (_userName != null && _password != null)
            {
                _connection = _mcf.getCleanAMQConnectionFactory().createXAConnection(_userName, _password);
            }
            else
            {
                _connection = _mcf.getDefaultAMQConnectionFactory().createXAConnection();
            }
            _xaSession = ((XAConnection)_connection).createXASession();
            _session =  _connection.createSession(transacted, acknowledgeMode);
         }

        _connection.setExceptionListener(this);

      }
      catch (JMSException je)
      {
         _log.error(je.getMessage(), je);
         throw new ResourceException(je.getMessage(), je);
      }
   }

   protected void setInManagedTx(boolean inManagedTx)
   {
      this._inManagedTx = inManagedTx;
   }

   public boolean isConnectionClosed()
   {
       Closeable c = (Closeable)_connection;
       return (c == null || c.isClosed() || c.isClosing());
   }
}
