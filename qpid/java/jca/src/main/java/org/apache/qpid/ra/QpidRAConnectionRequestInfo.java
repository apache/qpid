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

import javax.jms.Session;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;

import org.apache.qpid.jms.ConnectionURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection request information
 *
 */
public class QpidRAConnectionRequestInfo implements ConnectionRequestInfo
{
   /** The logger */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAConnectionRequestInfo.class);

   /** The user name */
   private String _userName;

   /** The password */
   private String _password;

   /** The client id */
   private String _clientID;

   /** The type */
   private final int _type;

   /** Use transactions */
   private final boolean _transacted;

   /** The acknowledge mode */
   private final int _acknowledgeMode;

   /**
    * Constructor
    * @param ra The resource adapter.
    * @param type The connection type
    * @throws ResourceException
    */
   public QpidRAConnectionRequestInfo(final QpidResourceAdapter ra, final int type)
      throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + ra + ")");
      }

      final QpidRAProperties properties = ra.getProperties() ;
      if (properties.getConnectionURL() != null)
      {
         final ConnectionURL connectionURL = ra.getDefaultAMQConnectionFactory().getConnectionURL() ;
         _userName = connectionURL.getUsername();
         _password = connectionURL.getPassword();
         _clientID = connectionURL.getClientName();
      }
      else
      {
         _userName = ra.getDefaultUserName();
         _password = ra.getDefaultPassword();
         _clientID = ra.getClientId();
      }
      this._type = type;
      _transacted = true;
      _acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
   }

   /**
    * Constructor
    * @param type The connection type
    */
   public QpidRAConnectionRequestInfo(final int type)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + type + ")");
      }

      this._type = type;
      _transacted = true;
      _acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
   }

   /**
    * Constructor
    * @param transacted Use transactions
    * @param acknowledgeMode The acknowledge mode
    * @param type The connection type
    */
   public QpidRAConnectionRequestInfo(final boolean transacted, final int acknowledgeMode, final int type)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor(" + transacted +
                                                  ", " +
                                                  acknowledgeMode +
                                                  ", " +
                                                  type +
                                                  ")");
      }

      this._transacted = transacted;
      this._acknowledgeMode = acknowledgeMode;
      this._type = type;
   }

   /**
    * Fill in default values if they are missing
    * @param connectionURL The connection URL
    */
   public void setDefaults(final ConnectionURL connectionURL)
   {
      if (_userName == null)
      {
         _userName = connectionURL.getUsername();
      }
      if (_password == null)
      {
         _password = connectionURL.getPassword();
      }
      if (_clientID == null)
      {
         _clientID = connectionURL.getClientName();
      }
   }

   /**
    * Fill in default values if they are missing
    * @param ra The resource adapter
    * @throws ResourceException
    */
   public void setDefaults(final QpidResourceAdapter ra)
      throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDefaults(" + ra + ")");
      }

      final QpidRAProperties properties = ra.getProperties() ;
      if (properties.getConnectionURL() != null)
      {
         setDefaults(ra.getDefaultAMQConnectionFactory().getConnectionURL()) ;
      }
      else
      {
         if (_userName == null)
         {
            _userName = ra.getDefaultUserName();
         }
         if (_password == null)
         {
            _password = ra.getDefaultPassword();
         }
         if (_clientID == null)
         {
            _clientID = ra.getClientId();
         }
      }
   }

   /**
    * Get the user name
    * @return The value
    */
   public String getUserName()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getUserName()");
      }

      return _userName;
   }

   /**
    * Set the user name
    * @param userName The value
    */
   public void setUserName(final String userName)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setUserName(" + userName + ")");
      }

      this._userName = userName;
   }

   /**
    * Get the password
    * @return The value
    */
   public String getPassword()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPassword()");
      }

      return _password;
   }

   /**
    * Set the password
    * @param password The value
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
    * Get the client id
    * @return The value
    */
   public String getClientID()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getClientID()");
      }

      return _clientID;
   }

   /**
    * Set the client id
    * @param clientID The value
    */
   public void setClientID(final String clientID)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setClientID(" + clientID + ")");
      }

      this._clientID = clientID;
   }

   /**
    * Get the connection type
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
    * Use transactions
    * @return True if transacted; otherwise false
    */
   public boolean isTransacted()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("isTransacted() " + _transacted);
      }

      return _transacted;
   }

   /**
    * Get the acknowledge mode
    * @return The mode
    */
   public int getAcknowledgeMode()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getAcknowledgeMode()");
      }

      return _acknowledgeMode;
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    * @param obj Object with which to compare
    * @return True if this object is the same as the obj argument; false otherwise.
    */
   @Override
   public boolean equals(final Object obj)
   {
      if (obj instanceof QpidRAConnectionRequestInfo)
      {
         QpidRAConnectionRequestInfo you = (QpidRAConnectionRequestInfo)obj;
         return Util.compare(_userName, you.getUserName()) && Util.compare(_password, you.getPassword()) &&
                Util.compare(_clientID, you.getClientID()) &&
                _type == you.getType() &&
                _transacted == you.isTransacted() &&
                _acknowledgeMode == you.getAcknowledgeMode();
      }
      else
      {
         return false;
      }
   }

   /**
    * Return the hash code for the object
    * @return The hash code
    */
   @Override
   public int hashCode()
   {
      int hash = 7;

      hash += 31 * hash + (_userName != null ? _userName.hashCode() : 0);
      hash += 31 * hash + (_password != null ? _password.hashCode() : 0);
      hash += 31 * hash + (_clientID != null ? _clientID.hashCode() : 0);
      hash += 31 * hash + _type;
      hash += 31 * hash + (_transacted ? 1 : 0);
      hash += 31 * hash + _acknowledgeMode;

      return hash;
   }

   @Override
   public String toString()
   {
      return "QpidRAConnectionRequestInfo[type=" + _type +
         ", transacted=" + _transacted + ", acknowledgeMode=" + _acknowledgeMode +
         ", clientID=" + _clientID + ", userName=" + _userName + ((_password != null) ? ", password=********]" :"]");
   }
}
