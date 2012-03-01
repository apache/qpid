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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QpidRAXAResource.
 *
 */
public class QpidRAXAResource implements XAResource
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAXAResource.class);

   /** The managed connection */
   private final QpidRAManagedConnection _managedConnection;

   /** The resource */
   private final XAResource _xaResource;

   /**
    * Create a new QpidRAXAResource.
    * @param managedConnection the managed connection
    * @param xaResource the xa resource
    */
   public QpidRAXAResource(final QpidRAManagedConnection managedConnection, final XAResource xaResource)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + managedConnection + ", " + Util.asString(xaResource) + ")");
      }

      this._managedConnection = managedConnection;
      this._xaResource = xaResource;
   }

   /**
    * Start
    * @param xid A global transaction identifier
    * @param flags One of TMNOFLAGS, TMJOIN, or TMRESUME
    * @exception XAException An error has occurred
    */
   public void start(final Xid xid, final int flags) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("start(" + xid + ", " + flags + ")");
      }

      _managedConnection.lock();
      try
      {
         _xaResource.start(xid, flags);
      }
      finally
      {
         _managedConnection.setInManagedTx(true);
         _managedConnection.unlock();
      }
   }

   /**
    * End
    * @param xid A global transaction identifier
    * @param flags One of TMSUCCESS, TMFAIL, or TMSUSPEND.
    * @exception XAException An error has occurred
    */
   public void end(final Xid xid, final int flags) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("end(" + xid + ", " + flags + ")");
      }

      _managedConnection.lock();
      try
      {
         _xaResource.end(xid, flags);
      }
      finally
      {
         _managedConnection.setInManagedTx(false);
         _managedConnection.unlock();
      }
   }

   /**
    * Prepare
    * @param xid A global transaction identifier
    * @return XA_RDONLY or XA_OK
    * @exception XAException An error has occurred
    */
   public int prepare(final Xid xid) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("prepare(" + xid + ")");
      }

      return _xaResource.prepare(xid);
   }

   /**
    * Commit
    * @param xid A global transaction identifier
    * @param onePhase If true, the resource manager should use a one-phase commit protocol to commit the work done on behalf of xid.
    * @exception XAException An error has occurred
    */
   public void commit(final Xid xid, final boolean onePhase) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("commit(" + xid + ", " + onePhase + ")");
      }

      _xaResource.commit(xid, onePhase);
   }

   /**
    * Rollback
    * @param xid A global transaction identifier
    * @exception XAException An error has occurred
    */
   public void rollback(final Xid xid) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("rollback(" + xid + ")");
      }

      _xaResource.rollback(xid);
   }

   /**
    * Forget
    * @param xid A global transaction identifier
    * @exception XAException An error has occurred
    */
   public void forget(final Xid xid) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("forget(" + xid + ")");
      }

      _managedConnection.lock();
      try
      {
         _xaResource.forget(xid);
      }
      finally
      {
         _managedConnection.setInManagedTx(false);
         _managedConnection.unlock();
      }
   }

   /**
    * IsSameRM
    * @param xaRes An XAResource object whose resource manager instance is to be compared with the resource manager instance of the target object.
    * @return True if its the same RM instance; otherwise false.
    * @exception XAException An error has occurred
    */
   public boolean isSameRM(final XAResource xaRes) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("isSameRM(" + xaRes + ")");
      }

      return _xaResource.isSameRM(xaRes);
   }

   /**
    * Recover
    * @param flag One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS
    * @return Zero or more XIDs
    * @exception XAException An error has occurred
    */
   public Xid[] recover(final int flag) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("recover(" + flag + ")");
      }

      return _xaResource.recover(flag);
   }

   /**
    * Get the transaction timeout in seconds
    * @return The transaction timeout
    * @exception XAException An error has occurred
    */
   public int getTransactionTimeout() throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTransactionTimeout()");
      }

      return _xaResource.getTransactionTimeout();
   }

   /**
    * Set the transaction timeout
    * @param seconds The number of seconds
    * @return True if the transaction timeout value is set successfully; otherwise false.
    * @exception XAException An error has occurred
    */
   public boolean setTransactionTimeout(final int seconds) throws XAException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setTransactionTimeout(" + seconds + ")");
      }

      return _xaResource.setTransactionTimeout(seconds);
   }
}
