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

import javax.jms.Queue;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MCF default properties
 *
 */
public class QpidRAMCFProperties extends ConnectionFactoryProperties implements Serializable
{
   /**
    * Serial version UID
    */
   private static final long serialVersionUID = -1675836810881223064L;

   /**
    * The logger
    */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAMCFProperties.class);

   /**
    * The queue type
    */
   private static final String QUEUE_TYPE = Queue.class.getName();

   /**
    * The topic type
    */
   private static final String TOPIC_TYPE = Topic.class.getName();

   /**
    * The connection type
    */
   private int _type = QpidRAConnectionFactory.CONNECTION;

   /**
    * Use tryLock
    */
   private Integer _useTryLock;

   /**
    * Constructor
    */
   public QpidRAMCFProperties()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor()");
      }

      _useTryLock = null;
   }

   /**
    * Get the connection type
    *
    * @return The type
    */
   public int getType()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getType()");
      }

      return _type;
   }

   /**
    * Set the default session type.
    *
    * @param defaultType either javax.jms.Topic or javax.jms.Queue
    */
   public void setSessionDefaultType(final String defaultType)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSessionDefaultType(" + _type + ")");
      }

      if (defaultType.equals(QpidRAMCFProperties.QUEUE_TYPE))
      {
         _type = QpidRAConnectionFactory.QUEUE_CONNECTION;
      }
      else if (defaultType.equals(QpidRAMCFProperties.TOPIC_TYPE))
      {
         _type = QpidRAConnectionFactory.TOPIC_CONNECTION;
      }
      else
      {
         _type = QpidRAConnectionFactory.CONNECTION;
      }
   }

   /**
    * Get the default session type.
    *
    * @return The default session type
    */
   public String getSessionDefaultType()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSessionDefaultType()");
      }

      if (_type == QpidRAConnectionFactory.CONNECTION)
      {
         return "BOTH";
      }
      else if (_type == QpidRAConnectionFactory.QUEUE_CONNECTION)
      {
         return QpidRAMCFProperties.TOPIC_TYPE;
      }
      else
      {
         return QpidRAMCFProperties.QUEUE_TYPE;
      }
   }

   /**
    * Get the useTryLock.
    *
    * @return the useTryLock.
    */
   public Integer getUseTryLock()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getUseTryLock()");
      }

      return _useTryLock;
   }

   /**
    * Set the useTryLock.
    *
    * @param useTryLock the useTryLock.
    */
   public void setUseTryLock(final Integer useTryLock)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setUseTryLock(" + useTryLock + ")");
      }

      this._useTryLock = useTryLock;
   }
}
