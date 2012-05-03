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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RA default properties - these are set in the ra.xml file
 *
 */
public class QpidRAProperties extends ConnectionFactoryProperties implements Serializable
{
   private static final long serialVersionUID = -4823893873707374791L;

   private static final Logger _log = LoggerFactory.getLogger(QpidRAProperties.class);

   private static final int DEFAULT_SETUP_ATTEMPTS = 10;

   private static final long DEFAULT_SETUP_INTERVAL = 2 * 1000;

   private int _setupAttempts = DEFAULT_SETUP_ATTEMPTS;

   private long _setupInterval = DEFAULT_SETUP_INTERVAL;

   /** Class used to locate the Transaction Manager. */
   private String _transactionManagerLocatorClass ;

   /** Method used to locate the TM */
   private String _transactionManagerLocatorMethod ;

   private boolean _useConnectionPerHandler = true;
   
   /**
    * Constructor
    */
   public QpidRAProperties()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor()");
      }
   }

   public void setTransactionManagerLocatorClass(final String transactionManagerLocatorClass)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setTransactionManagerLocatorClass(" + transactionManagerLocatorClass + ")");
      }

      this._transactionManagerLocatorClass = transactionManagerLocatorClass;
   }

   public String getTransactionManagerLocatorClass()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTransactionManagerLocatorClass()");
      }

      return _transactionManagerLocatorClass;
   }

   public String getTransactionManagerLocatorMethod()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTransactionManagerLocatorMethod()");
      }

      return _transactionManagerLocatorMethod;
   }

   public void setTransactionManagerLocatorMethod(final String transactionManagerLocatorMethod)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setTransactionManagerLocatorMethod(" + transactionManagerLocatorMethod + ")");
      }

      this._transactionManagerLocatorMethod = transactionManagerLocatorMethod;
   }

   public int getSetupAttempts()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSetupAttempts()");
      }

      return _setupAttempts;
   }

   public void setSetupAttempts(int setupAttempts)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSetupAttempts(" + setupAttempts + ")");
      }

      this._setupAttempts = setupAttempts;
   }

   public long getSetupInterval()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSetupInterval()");
      }

      return _setupInterval;
   }

   public void setSetupInterval(long setupInterval)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSetupInterval(" + setupInterval + ")");
      }

      this._setupInterval = setupInterval;
   }

   public boolean isUseConnectionPerHandler()
   {
       return _useConnectionPerHandler;
   }
   
   public void setUseConnectionPerHandler(boolean connectionPerHandler)
   {
       this._useConnectionPerHandler = connectionPerHandler;                       
   }
   
   @Override
   public String toString()
   {
      return "QpidRAProperties[" +
            ", transactionManagerLocatorClass=" + _transactionManagerLocatorClass +
            ", transactionManagerLocatorMethod=" + _transactionManagerLocatorMethod +
            ", setupAttempts=" + _setupAttempts +
            ", setupInterval=" + _setupInterval + "]";
   }
}
