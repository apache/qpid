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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Session;
import javax.jms.XASession;
import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.WorkManager;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.XAConnectionImpl;
import org.apache.qpid.ra.inflow.QpidActivation;
import org.apache.qpid.ra.inflow.QpidActivationSpec;
import org.apache.qpid.url.URLSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The resource adapter for Qpid
 *
 */
public class QpidResourceAdapter implements ResourceAdapter, Serializable
{
   private static final long serialVersionUID = -2446231446818098726L;

   private static final transient Logger _log = LoggerFactory.getLogger(QpidResourceAdapter.class);

   private BootstrapContext _ctx;

   private final QpidRAProperties _raProperties;

   private final AtomicBoolean _configured;

   private final Map<ActivationSpec, QpidActivation> _activations;

   private AMQConnectionFactory _defaultAMQConnectionFactory;

   private TransactionManager _tm;

   public QpidResourceAdapter()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor()");
      }

      _raProperties = new QpidRAProperties();
      _configured = new AtomicBoolean(false);
      _activations = new ConcurrentHashMap<ActivationSpec, QpidActivation>();
   }

   public TransactionManager getTM()
   {
      return _tm;
   }

   /**
    * Endpoint activation
    *
    * @param endpointFactory The endpoint factory
    * @param spec            The activation spec
    * @throws ResourceException Thrown if an error occurs
    */
   public void endpointActivation(final MessageEndpointFactory endpointFactory, final ActivationSpec spec) throws ResourceException
   {
      if (!_configured.getAndSet(true))
      {
         try
         {
            setup();
         }
         catch (QpidRAException e)
         {
            throw new ResourceException("Unable to create activation", e);
         }
      }
      if (_log.isTraceEnabled())
      {
         _log.trace("endpointActivation(" + endpointFactory + ", " + spec + ")");
      }

      QpidActivation activation = new QpidActivation(this, endpointFactory, (QpidActivationSpec)spec);
      _activations.put(spec, activation);
      activation.start();
   }

   /**
    * Endpoint deactivation
    *
    * @param endpointFactory The endpoint factory
    * @param spec            The activation spec
    */
   public void endpointDeactivation(final MessageEndpointFactory endpointFactory, final ActivationSpec spec)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("endpointDeactivation(" + endpointFactory + ", " + spec + ")");
      }

      QpidActivation activation = _activations.remove(spec);
      if (activation != null)
      {
         activation.stop();
      }
   }

   /**
    * Get XA resources
    *
    * @param specs The activation specs
    * @return The XA resources
    * @throws ResourceException Thrown if an error occurs or unsupported
    */
   public XAResource[] getXAResources(final ActivationSpec[] specs) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getXAResources(" + specs + ")");
      }

      return null;
   }

   /**
    * Start
    *
    * @param ctx The bootstrap context
    * @throws ResourceAdapterInternalException
    *          Thrown if an error occurs
    */
   public void start(final BootstrapContext ctx) throws ResourceAdapterInternalException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("start(" + ctx + ")");
      }

      locateTM();

      this._ctx = ctx;

      _log.info("Qpid resource adapter started");
   }

   /**
    * Stop
    */
   public void stop()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("stop()");
      }

      for (Map.Entry<ActivationSpec, QpidActivation> entry : _activations.entrySet())
      {
         try
         {
            entry.getValue().stop();
         }
         catch (Exception ignored)
         {
            _log.debug("Ignored", ignored);
         }
      }

      _activations.clear();

      _log.info("Qpid resource adapter stopped");
   }


   /**
    * Get the client ID
    *
    * @return The value
    */
   public String getClientId()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getClientID()");
      }

      return _raProperties.getClientId();
   }

   /**
    * Set the client ID
    *
    * @param clientID The client id
    */
   public void setClientId(final String clientID)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setClientID(" + clientID + ")");
      }

      _raProperties.setClientId(clientID);
   }

   /**
    * Get the host
    *
    * @return The value
    */
   public String getHost()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getHost()");
      }

      return _raProperties.getHost();
   }

   /**
    * Set the host
    *
    * @param host The host
    */
   public void setHost(final String host)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setHost(" + host + ")");
      }

      _raProperties.setHost(host);
   }

   /**
    * Get the port
    *
    * @return The value
    */
   public Integer getPort()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPort()");
      }

      return _raProperties.getPort();
   }

   /**
    * Set the client ID
    *
    * @param port The port
    */
   public void setPort(final Integer port)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPort(" + port + ")");
      }

      _raProperties.setPort(port);
   }

   /**
    * Get the connection url
    *
    * @return The value
    */
   public String getPath()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPath()");
      }

      return _raProperties.getPath();
   }

   /**
    * Set the client ID
    *
    * @param path The path
    */
   public void setPath(final String path)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPath(" + path + ")");
      }

      _raProperties.setPath(path);
   }

   public String getUserName()
   {
       return _raProperties.getUserName();
   }

   public void setUserName(String userName)
   {
      _raProperties.setUserName(userName);
   }

   public String getPassword()
   {
       return _raProperties.getPassword();
   }

   public void setPassword(String password)
   {
       _raProperties.setPassword(password);
   }

   /**
    * Get the connection url
    *
    * @return The value
    */
   public String getConnectionURL()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getConnectionURL()");
      }

      return _raProperties.getConnectionURL();
   }

   /**
    * Set the client ID
    *
    * @param connectionURL The connection url
    */
   public void setConnectionURL(final String connectionURL)
   {
      _raProperties.setConnectionURL(connectionURL);
   }

   /**
    * Get the transaction manager locator class
    *
    * @return The value
    */
   public String getTransactionManagerLocatorClass()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTransactionManagerLocatorClass()");
      }

      return _raProperties.getTransactionManagerLocatorClass();
   }

   /**
    * Set the transaction manager locator class
    *
    * @param locator The transaction manager locator class
    */
   public void setTransactionManagerLocatorClass(final String locator)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setTransactionManagerLocatorClass(" + locator + ")");
      }

      _raProperties.setTransactionManagerLocatorClass(locator);
   }

   /**
    * Get the transaction manager locator method
    *
    * @return The value
    */
   public String getTransactionManagerLocatorMethod()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getTransactionManagerLocatorMethod()");
      }

      return _raProperties.getTransactionManagerLocatorMethod();
   }

   /**
    * Set the transaction manager locator method
    *
    * @param method The transaction manager locator method
    */
   public void setTransactionManagerLocatorMethod(final String method)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setTransactionManagerLocatorMethod(" + method + ")");
      }

      _raProperties.setTransactionManagerLocatorMethod(method);
   }

   /**
    * Get the use XA flag
    *
    * @return The value
    */
   public Boolean isUseLocalTx()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getUseLocalTx()");
      }

      return _raProperties.isUseLocalTx();
   }

   /**
    * Set the use XA flag
    *
    * @param localTx The value
    */
   public void setUseLocalTx(final Boolean localTx)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setUseLocalTx(" + localTx + ")");
      }

      _raProperties.setUseLocalTx(localTx);
   }

   public Integer getSetupAttempts()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSetupAttempts()");
      }
      return _raProperties.getSetupAttempts();
   }

   public void setSetupAttempts(Integer setupAttempts)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSetupAttempts(" + setupAttempts + ")");
      }
      _raProperties.setSetupAttempts(setupAttempts);
   }

   public Long getSetupInterval()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSetupInterval()");
      }
      return _raProperties.getSetupInterval();
   }

   public void setSetupInterval(Long interval)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSetupInterval(" + interval + ")");
      }
      _raProperties.setSetupInterval(interval);
   }
   
   public Boolean isUseConnectionPerHandler()
   {
       if (_log.isTraceEnabled())
       {
          _log.trace("isConnectionPerHandler()");
       }
       
       return _raProperties.isUseConnectionPerHandler();
   }

   public void setUseConnectionPerHandler(Boolean connectionPerHandler)
   {
       if (_log.isTraceEnabled())
       {
          _log.trace("setConnectionPerHandler(" + connectionPerHandler + ")");
       }  
       
       _raProperties.setUseConnectionPerHandler(connectionPerHandler);
   }
   
   /**
    * Indicates whether some other object is "equal to" this one.
    *
    * @param obj Object with which to compare
    * @return True if this object is the same as the obj argument; false otherwise.
    */
   public boolean equals(final Object obj)
   {
      if (obj == null)
      {
         return false;
      }

      if (obj instanceof QpidResourceAdapter)
      {
         return _raProperties.equals(((QpidResourceAdapter)obj).getProperties());
      }
      else
      {
         return false;
      }
   }

   /**
    * Return the hash code for the object
    *
    * @return The hash code
    */
   public int hashCode()
   {
      return _raProperties.hashCode();
   }

   /**
    * Get the work manager
    *
    * @return The manager
    */
   public WorkManager getWorkManager()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getWorkManager()");
      }

      if (_ctx == null)
      {
         return null;
      }

      return _ctx.getWorkManager();
   }

   public XASession createXASession(final XAConnectionImpl connection)
      throws Exception
   {
      final XASession result = connection.createXASession() ;
      if (_log.isDebugEnabled())
      {
         _log.debug("Using session " + Util.asString(result));
      }
      return result ;
   }

   public Session createSession(final AMQConnection connection,
                                      final int ackMode,
                                      final boolean useLocalTx,
                                      final Integer prefetchLow,
                                      final Integer prefetchHigh) throws Exception
   {
      Session result;

      if (prefetchLow == null)
      {
         result = connection.createSession(useLocalTx, ackMode) ;
      }
      else if (prefetchHigh == null)
      {
         result = connection.createSession(useLocalTx, ackMode, prefetchLow) ;
      }
      else
      {
         result = connection.createSession(useLocalTx, ackMode, prefetchHigh, prefetchLow) ;
      }

      if (_log.isDebugEnabled())
      {
         _log.debug("Using session " + Util.asString(result));
      }

      return result;

   }

   /**
    * Get the resource adapter properties
    *
    * @return The properties
    */
   protected QpidRAProperties getProperties()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getProperties()");
      }

      return _raProperties;
   }

   /**
    * Setup the factory
    */
   protected void setup() throws QpidRAException
   {
      _defaultAMQConnectionFactory = createAMQConnectionFactory(_raProperties);
   }


   public AMQConnectionFactory getDefaultAMQConnectionFactory() throws ResourceException
   {
      if (!_configured.getAndSet(true))
      {
         try
         {
            setup();
         }
         catch (QpidRAException e)
         {
            throw new ResourceException("Unable to create activation", e);
         }
      }
      return _defaultAMQConnectionFactory;
   }

   public AMQConnectionFactory createAMQConnectionFactory(final ConnectionFactoryProperties overrideProperties)
      throws QpidRAException
   {
      try
      {
         return createFactory(overrideProperties);
      }
      catch (final URLSyntaxException urlse)
      {
         throw new QpidRAException("Unexpected exception creating connection factory", urlse) ;
      }
   }

   public Map<String, Object> overrideConnectionParameters(final Map<String, Object> connectionParams,
                                                           final Map<String, Object> overrideConnectionParams)
   {
      Map<String, Object> map = new HashMap<String, Object>();

      if(connectionParams != null)
      {
         map.putAll(connectionParams);
      }
      if(overrideConnectionParams != null)
      {
         for (Map.Entry<String, Object> stringObjectEntry : overrideConnectionParams.entrySet())
         {
            map.put(stringObjectEntry.getKey(), stringObjectEntry.getValue());
         }
      }
      return map;
   }

   private void locateTM() throws ResourceAdapterInternalException
   {
      if(_raProperties.getTransactionManagerLocatorClass() != null 
                      && _raProperties.getTransactionManagerLocatorMethod() != null)
      {

          String locatorClasses[] = _raProperties.getTransactionManagerLocatorClass().split(";");
          String locatorMethods[] = _raProperties.getTransactionManagerLocatorMethod().split(";");

          for (int i = 0 ; i < locatorClasses.length; i++)
          {
              _tm = Util.locateTM(locatorClasses[i], locatorMethods[i]);
              if (_tm != null)
              {
                  break;
              }
          }


      }

      if (_tm == null)
      {
         _log.error("It was not possible to locate javax.transaction.TransactionManager via the RA properties TransactionManagerLocatorClass and TransactionManagerLocatorMethod");
         throw new ResourceAdapterInternalException("Could not locate javax.transaction.TransactionManager");
      }
      else
      {
         if (_log.isDebugEnabled())
         {
            _log.debug("TM located = " + _tm);
         }
      }
   }


   private AMQConnectionFactory createFactory(final ConnectionFactoryProperties overrideProperties)
      throws URLSyntaxException, QpidRAException
   {
      final String overrideURL = overrideProperties.getConnectionURL() ;
      final String url = overrideURL != null ? overrideURL : _raProperties.getConnectionURL() ;

      final String overrideClientID = overrideProperties.getClientId() ;
      final String clientID = (overrideClientID != null ? overrideClientID : _raProperties.getClientId()) ;

      final String overrideDefaultPassword = overrideProperties.getPassword() ;
      final String defaultPassword = (overrideDefaultPassword != null ? overrideDefaultPassword : _raProperties.getPassword()) ;

      final String overrideDefaultUsername = overrideProperties.getUserName() ;
      final String defaultUsername = (overrideDefaultUsername != null ? overrideDefaultUsername : _raProperties.getUserName()) ;

      final String overrideHost = overrideProperties.getHost() ;
      final String host = (overrideHost != null ? overrideHost : _raProperties.getHost()) ;

      final Integer overridePort = overrideProperties.getPort() ;
      final Integer port = (overridePort != null ? overridePort : _raProperties.getPort()) ;

      final String overridePath = overrideProperties.getPath() ;
      final String path = (overridePath != null ? overridePath : _raProperties.getPath()) ;

      final AMQConnectionFactory cf ;

      if (url != null)
      {
         cf = new AMQConnectionFactory(url) ;

         if (clientID != null)
         {
            cf.getConnectionURL().setClientName(clientID) ;
         }
      }
      else
      {
         // create a URL to force the connection details
         if ((host == null) || (port == null) || (path == null))
         {
            throw new QpidRAException("Configuration requires host/port/path if connectionURL is not specified") ;
         }
         final String username = (defaultUsername != null ? defaultUsername : "") ;
         final String password = (defaultPassword != null ? defaultPassword : "") ;
         final String client = (clientID != null ? clientID : "") ;

         final String newurl = AMQConnectionURL.AMQ_PROTOCOL + "://" + username +":" + password + "@" + client + "/" + path + '?' + AMQConnectionURL.OPTIONS_BROKERLIST + "='tcp://" + host + ':' + port + '\'' ;
         
         if (_log.isDebugEnabled())
         {
            _log.debug("Initialising connectionURL to " + newurl) ;
         }

         cf = new AMQConnectionFactory(newurl) ;
      }

      return cf ;
   }
}
