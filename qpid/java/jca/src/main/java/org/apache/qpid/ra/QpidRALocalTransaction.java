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

import javax.jms.JMSException;
import javax.resource.ResourceException;
import javax.resource.spi.LocalTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMS Local transaction
 *
 */
public class QpidRALocalTransaction implements LocalTransaction
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRALocalTransaction.class);

   /** The managed connection */
   private final QpidRAManagedConnection _mc;

   /**
    * Constructor
    * @param mc The managed connection
    */
   public QpidRALocalTransaction(final QpidRAManagedConnection mc)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + mc + ")");
      }

      this._mc = mc;
   }

   /**
    * Begin
    * @exception ResourceException Thrown if the operation fails
    */
   public void begin() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("begin()");
      }
   }

   /**
    * Commit
    * @exception ResourceException Thrown if the operation fails
    */
   public void commit() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("commit()");
      }

      _mc.lock();

      try
      {
         if (_mc.getSession() == null)
         {
            throw new ResourceException("Could not commit LocalTransaction: null Session.");
         }

         _mc.getSession().commit();
      }
      catch (JMSException e)
      {
         throw new ResourceException("Could not commit LocalTransaction", e);
      }
      finally
      {
         _mc.unlock();
      }
   }

   /**
    * Rollback
    * @exception ResourceException Thrown if the operation fails
    */
   public void rollback() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("rollback()");
      }

      _mc.lock();
      try
      {
         if (_mc.getSession() != null && _mc.getSession().getTransacted())
         {
            _mc.getSession().rollback();
         }
      }
      catch (JMSException ex)
      {
         throw new ResourceException("Could not rollback LocalTransaction", ex);
      }
      finally
      {
         _mc.unlock();
      }
   }
}
