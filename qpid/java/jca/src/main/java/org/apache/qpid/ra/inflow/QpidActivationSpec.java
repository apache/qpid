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
package org.apache.qpid.ra.inflow;

import java.io.Serializable;

import javax.jms.Session;
import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ResourceAdapter;

import org.apache.qpid.ra.ConnectionFactoryProperties;
import org.apache.qpid.ra.QpidResourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The activation spec
 * These properties are set on the MDB ActivactionProperties
 *
 */
public class QpidActivationSpec extends ConnectionFactoryProperties implements ActivationSpec, Serializable
{
   private static final long serialVersionUID = 7379131936083146158L;

   private static final int DEFAULT_MAX_SESSION = 15;

   /** The logger */
   private static final transient Logger _log = LoggerFactory.getLogger(QpidActivationSpec.class);

   /** The resource adapter */
   private QpidResourceAdapter _ra;

   /** The destination */
   private String _destination;

   /** The destination type */
   private String _destinationType;

   /** The message selector */
   private String _messageSelector;

   /** The acknowledgement mode */
   private int _acknowledgeMode;

   /** The subscription durability */
   private boolean _subscriptionDurability;

   /** The subscription name */
   private String _subscriptionName;

   /** The maximum number of sessions */
   private Integer _maxSession;

   /** Transaction timeout */
   private Integer _transactionTimeout;

   /** prefetch low */
   private Integer _prefetchLow;

   /** prefetch high */
   private Integer _prefetchHigh;

   private boolean _useJNDI = true;

   // undefined by default, default is specified at the RA level in QpidRAProperties
   private Integer _setupAttempts;

   // undefined by default, default is specified at the RA level in QpidRAProperties
   private Long _setupInterval;

   private Boolean _useConnectionPerHandler;

   /**
    * Constructor
    */
   public QpidActivationSpec()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor()");
      }

      _acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
      _maxSession = DEFAULT_MAX_SESSION;
      _transactionTimeout = 0;
   }

   /**
    * Get the resource adapter
    * @return The resource adapter
    */
   public ResourceAdapter getResourceAdapter()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getResourceAdapter()");
      }

      return _ra;
   }

   /**
    * @return the useJNDI
    */
   public boolean isUseJNDI()
   {
      return _useJNDI;
   }

   /**
    * @param value the useJNDI to set
    */
   public void setUseJNDI(final boolean value)
   {
      _useJNDI = value;
   }

   /**
    * Set the resource adapter
    * @param ra The resource adapter
    * @exception ResourceException Thrown if incorrect resource adapter
    */
   public void setResourceAdapter(final ResourceAdapter ra) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setResourceAdapter(" + ra + ")");
      }

      if (ra == null || !(ra instanceof QpidResourceAdapter))
      {
         throw new ResourceException("Resource adapter is " + ra);
      }

      this._ra = (QpidResourceAdapter)ra;
   }

   /**
    * Get the destination
    * @return The value
    */
   public String getDestination()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getDestination()");
      }

      return _destination;
   }

   /**
    * Set the destination
    * @param value The value
    */
   public void setDestination(final String value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDestination(" + value + ")");
      }

      _destination = value;
   }

   /**
    * Get the destination type
    * @return The value
    */
   public String getDestinationType()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getDestinationType()");
      }

      return _destinationType;
   }

   /**
    * Set the destination type
    * @param value The value
    */
   public void setDestinationType(final String value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDestinationType(" + value + ")");
      }

      _destinationType = value;
   }

   /**
    * Get the message selector
    * @return The value
    */
   public String getMessageSelector()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMessageSelector()");
      }

      return _messageSelector;
   }

   /**
    * Set the message selector
    * @param value The value
    */
   public void setMessageSelector(final String value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setMessageSelector(" + value + ")");
      }

      _messageSelector = value;
   }

   /**
    * Get the acknowledge mode
    * @return The value
    */
   public String getAcknowledgeMode()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getAcknowledgeMode()");
      }

      if (Session.DUPS_OK_ACKNOWLEDGE == _acknowledgeMode)
      {
         return "Dups-ok-acknowledge";
      }
      else
      {
         return "Auto-acknowledge";
      }
   }

   /**
    * Set the acknowledge mode
    * @param value The value
    */
   public void setAcknowledgeMode(final String value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setAcknowledgeMode(" + value + ")");
      }

      if ("DUPS_OK_ACKNOWLEDGE".equalsIgnoreCase(value) || "Dups-ok-acknowledge".equalsIgnoreCase(value))
      {
         _acknowledgeMode = Session.DUPS_OK_ACKNOWLEDGE;
      }
      else if ("AUTO_ACKNOWLEDGE".equalsIgnoreCase(value) || "Auto-acknowledge".equalsIgnoreCase(value))
      {
         _acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
      }
      else
      {
         throw new IllegalArgumentException("Unsupported acknowledgement mode " + value);
      }
   }

   /**
    * @return the acknowledgement mode
    */
   public int getAcknowledgeModeInt()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getAcknowledgeMode()");
      }

      return _acknowledgeMode;
   }

   /**
    * Get the subscription durability
    * @return The value
    */
   public String getSubscriptionDurability()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSubscriptionDurability()");
      }

      if (_subscriptionDurability)
      {
         return "Durable";
      }
      else
      {
         return "NonDurable";
      }
   }

   /**
    * Set the subscription durability
    * @param value The value
    */
   public void setSubscriptionDurability(final String value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSubscriptionDurability(" + value + ")");
      }

      _subscriptionDurability = "Durable".equals(value);
   }

   /**
    * Get the status of subscription durability
    * @return The value
    */
   public boolean isSubscriptionDurable()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("isSubscriptionDurable()");
      }

      return _subscriptionDurability;
   }

   /**
    * Get the subscription name
    * @return The value
    */
   public String getSubscriptionName()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSubscriptionName()");
      }

      return _subscriptionName;
   }

   /**
    * Set the subscription name
    * @param value The value
    */
   public void setSubscriptionName(final String value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSubscriptionName(" + value + ")");
      }

      _subscriptionName = value;
   }

   /**
    * Get the number of max session
    * @return The value
    */
   public Integer getMaxSession()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMaxSession()");
      }

      if (_maxSession == null)
      {
         return DEFAULT_MAX_SESSION;
      }

      return _maxSession;
   }

   /**
    * Set the number of max session
    * @param value The value
    */
   public void setMaxSession(final Integer value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setMaxSession(" + value + ")");
      }

      _maxSession = value;
   }

   /**
    * Get the transaction timeout
    * @return The value
    */
   public Integer getTransactionTimeout()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTransactionTimeout()");
      }

      return _transactionTimeout;
   }

   /**
    * Set the transaction timeout
    * @param value The value
    */
   public void setTransactionTimeout(final Integer value)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setTransactionTimeout(" + value + ")");
      }

      _transactionTimeout = value;
   }

   /**
    * Get the prefetch low
    * @return The value
    */
   public Integer getPrefetchLow()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPrefetchLow()");
      }

      return _prefetchLow;
   }

   /**
    * Set the prefetch low
    * @param prefetchLow The value
    */
   public void setPrefetchLow(final Integer prefetchLow)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPrefetchLow(" + prefetchLow + ")");
      }

      this._prefetchLow = prefetchLow;
   }

   /**
    * Get the prefetch high
    * @return The value
    */
   public Integer getPrefetchHigh()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPrefetchHigh()");
      }

      return _prefetchHigh;
   }

   /**
    * Set the prefetch high
    * @param prefetchHigh The value
    */
   public void setPrefetchHigh(final Integer prefetchHigh)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPrefetchHigh(" + prefetchHigh + ")");
      }

      this._prefetchHigh = prefetchHigh;
   }

   public int getSetupAttempts()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSetupAttempts()");
      }

      if (_setupAttempts == null)
      {
         return _ra.getSetupAttempts();
      }
      else
      {
         return _setupAttempts;
      }
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

      if (_setupInterval == null)
      {
         return _ra.getSetupInterval();
      }
      else
      {
         return _setupInterval;
      }
   }

   public void setSetupInterval(long setupInterval)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSetupInterval(" + setupInterval + ")");
      }

      this._setupInterval = setupInterval;
   }

   public Boolean isUseConnectionPerHandler()
   {              
       return (_useConnectionPerHandler == null) ? _ra.isUseConnectionPerHandler() : _useConnectionPerHandler;
   }
   
   public void setUseConnectionPerHandler(Boolean connectionPerHandler)
   {
       this._useConnectionPerHandler = connectionPerHandler;                       
   }

   /**
    * Validate
    * @exception InvalidPropertyException Thrown if a validation exception occurs
    */
   public void validate() throws InvalidPropertyException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("validate()");
      }

      if (_destination == null || _destination.trim().equals(""))
      {
         throw new InvalidPropertyException("Destination is mandatory");
      }
   }

   
   /**
    * Get a string representation
    * @return The value
    */
   @Override
   public String toString()
   {
      StringBuffer buffer = new StringBuffer();
      buffer.append(QpidActivationSpec.class.getName()).append('(');
      buffer.append("ra=").append(_ra);
      buffer.append(" destination=").append(_destination);
      buffer.append(" destinationType=").append(_destinationType);
      
      if (_messageSelector != null)
      {
         buffer.append(" selector=").append(_messageSelector);
      }
      
      buffer.append(" ack=").append(getAcknowledgeMode());
      buffer.append(" durable=").append(_subscriptionDurability);
      buffer.append(" clientID=").append(getClientId());
      
      if (_subscriptionName != null)
      {
         buffer.append(" subscription=").append(_subscriptionName);
      }
      
      buffer.append(" user=").append(getUserName());
      
      if (getPassword() != null)
      {
         buffer.append(" password=").append("********");
      }
      
      buffer.append(" maxSession=").append(_maxSession);
      
      if (_prefetchLow != null)
      {
         buffer.append(" prefetchLow=").append(_prefetchLow);
      }
      if (_prefetchHigh != null)
      {
         buffer.append(" prefetchHigh=").append(_prefetchHigh);
      }
      
      buffer.append(" connectionPerHandler=").append(isUseConnectionPerHandler());
      buffer.append(')');

      return buffer.toString();
   }
}
