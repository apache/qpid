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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class ConnectionFactoryProperties
{
   /**
    * The logger
    */
   private static final Logger _log = LoggerFactory.getLogger(ConnectionFactoryProperties.class);

   private boolean _hasBeenUpdated = false;

   private String _clientId;

   private String _connectionURL;

   private String _userName;

   private String _password;

   private String _host;

   private Integer _port;

   private String _path;

   private Boolean _localTx = Boolean.FALSE;

   public String getClientId()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getClientID()");
      }
      return _clientId;
   }

   public void setClientId(final String clientID)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setClientID(" + clientID + ")");
      }
      _hasBeenUpdated = true;
      this._clientId = clientID;
   }

   public boolean isHasBeenUpdated()
   {
      return _hasBeenUpdated;
   }

   public String getConnectionURL()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getConnectionURL()");
      }
      return _connectionURL;
   }

   public void setConnectionURL(final String connectionURL)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setConnectionURL(" + Util.maskUrlForLog(connectionURL) + ")");
      }

      _hasBeenUpdated = true;
      this._connectionURL = connectionURL;
   }

   public String getPassword()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getDefaultPassword()");
      }
      return _password;
   }

   public void setPassword(final String defaultPassword)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDefaultPassword(" + defaultPassword + ")");
      }
      _hasBeenUpdated = true;
      this._password = defaultPassword;
   }

   public String getUserName()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getDefaultUsername()");
      }
      return _userName;
   }

   public void setUserName(final String defaultUsername)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDefaultUsername(" + defaultUsername + ")");
      }
      _hasBeenUpdated = true;
      this._userName = defaultUsername;
   }

   public String getHost()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getHost()");
      }
      return _host;
   }

   public void setHost(final String host)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setHost(" + host + ")");
      }
      _hasBeenUpdated = true;
      this._host = host;
   }

   public Integer getPort()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPort()");
      }
      return _port;
   }

   public void setPort(final Integer port)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPort(" + port + ")");
      }
      _hasBeenUpdated = true;
      this._port = port;
   }

   public String getPath()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPath()");
      }
      return _path;
   }

   public void setPath(final String path)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPath(" + path + ")");
      }
      _hasBeenUpdated = true;
      this._path = path;
   }

   public Boolean isUseLocalTx()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("isUseLocalTx()");
      }
       return _localTx;
   }

   public void setUseLocalTx(Boolean localTx)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setUseLocalTx(" + localTx + ")");
      }

      if(localTx != null)
      {
        _hasBeenUpdated = true;
        this._localTx = localTx;
      }

   }

}
