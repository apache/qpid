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

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionMetaData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Managed connection meta data
 *
 */
public class QpidRAMetaData implements ManagedConnectionMetaData
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAMetaData.class);

   /** The managed connection */
   private final QpidRAManagedConnection _mc;

   /**
    * Constructor
    * @param mc The managed connection
    */
   public QpidRAMetaData(final QpidRAManagedConnection mc)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + mc + ")");
      }

      this._mc = mc;
   }

   /**
    * Get the EIS product name
    * @return The name
    * @exception ResourceException Thrown if operation fails
    */
   public String getEISProductName() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getEISProductName()");
      }

      return "Qpid";
   }

   /**
    * Get the EIS product version
    * @return The version
    * @exception ResourceException Thrown if operation fails
    */
   public String getEISProductVersion() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getEISProductVersion()");
      }

      return "2.0";
   }

   /**
    * Get the user name
    * @return The user name
    * @exception ResourceException Thrown if operation fails
    */
   public String getUserName() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getUserName()");
      }

      return _mc.getUserName();
   }

   /**
     * Get the maximum number of connections -- RETURNS 0
     * @return The number
     * @exception ResourceException Thrown if operation fails
     */
   public int getMaxConnections() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMaxConnections()");
      }

      return 0;
   }

}
