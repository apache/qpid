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
import java.util.Set;

import javax.jms.ConnectionMetaData;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterAssociation;
import javax.security.auth.Subject;

import org.apache.qpid.client.AMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Qpid ManagedConectionFactory
 *
 */
public class QpidRAManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation
{
   /**
    * Serial version UID
    */
   private static final long serialVersionUID = -8798804592247643959L;

   /**
    * The logger
    */
   private static final Logger _log = LoggerFactory.getLogger(QpidRAManagedConnectionFactory.class);

   /**
    * The resource adapter
    */
   private QpidResourceAdapter _ra;

   /**
    * Connection manager
    */
   private ConnectionManager _cm;

   /**
    * The managed connection factory properties
    */
   private final QpidRAMCFProperties _mcfProperties;

   /**
    * Connection Factory used if properties are set
    */
   private AMQConnectionFactory _connectionFactory;

   /**
    * Constructor
    */
   public QpidRAManagedConnectionFactory()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("constructor()");
      }

      _ra = null;
      _cm = null;
      _mcfProperties = new QpidRAMCFProperties();
   }

   /**
    * Creates a Connection Factory instance
    *
    * @return javax.resource.cci.ConnectionFactory instance
    * @throws ResourceException Thrown if a connection factory cant be created
    */
   public Object createConnectionFactory() throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createConnectionFactory()");
      }

      return createConnectionFactory(new QpidRAConnectionManager());
   }

   /**
    * Creates a Connection Factory instance
    *
    * @param cxManager The connection manager
    * @return javax.resource.cci.ConnectionFactory instance
    * @throws ResourceException Thrown if a connection factory cant be created
    */
   public Object createConnectionFactory(final ConnectionManager cxManager) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createConnectionFactory(" + cxManager + ")");
      }

      _cm = cxManager;

      QpidRAConnectionFactory cf = new QpidRAConnectionFactoryImpl(this, _cm);

      if (_log.isTraceEnabled())
      {
         _log.trace("Created connection factory: " + cf +
                                                     ", using connection manager: " +
                                                     _cm);
      }

      return cf;
   }

   /**
    * Creates a new physical connection to the underlying EIS resource manager.
    *
    * @param subject       Caller's security information
    * @param cxRequestInfo Additional resource adapter specific connection request information
    * @return The managed connection
    * @throws ResourceException Thrown if a managed connection cant be created
    */
   public ManagedConnection createManagedConnection(final Subject subject, final ConnectionRequestInfo cxRequestInfo) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("createManagedConnection(" + subject + ", " + cxRequestInfo + ")");
      }

      QpidRAConnectionRequestInfo cri = getCRI((QpidRAConnectionRequestInfo)cxRequestInfo);

      QpidRACredential credential = QpidRACredential.getCredential(this, subject, cri);

      if (_log.isTraceEnabled())
      {
         _log.trace("jms credential: " + credential);
      }

      QpidRAManagedConnection mc = new QpidRAManagedConnection(this,
                                                                     cri,
                                                                     _ra.getTM(),
                                                                     credential.getUserName(),
                                                                     credential.getPassword());

      if (_log.isTraceEnabled())
      {
         _log.trace("created new managed connection: " + mc);
      }

      return mc;
   }

   /**
    * Returns a matched connection from the candidate set of connections.
    *
    * @param connectionSet The candidate connection set
    * @param subject       Caller's security information
    * @param cxRequestInfo Additional resource adapter specific connection request information
    * @return The managed connection
    * @throws ResourceException Thrown if no managed connection can be found
    */
   @SuppressWarnings("rawtypes")
   public ManagedConnection matchManagedConnections(final Set connectionSet,
                                                    final Subject subject,
                                                    final ConnectionRequestInfo cxRequestInfo) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("matchManagedConnections(" + connectionSet +
                                                     ", " +
                                                     subject +
                                                     ", " +
                                                     cxRequestInfo +
                                                     ")");
      }

      QpidRAConnectionRequestInfo cri = getCRI((QpidRAConnectionRequestInfo)cxRequestInfo);
      QpidRACredential credential = QpidRACredential.getCredential(this, subject, cri);

      if (_log.isTraceEnabled())
      {
         _log.trace("Looking for connection matching credentials: " + credential);
      }

      for (final Object obj : connectionSet)
      {
         if (obj instanceof QpidRAManagedConnection)
         {
            QpidRAManagedConnection mc = (QpidRAManagedConnection)obj;
            ManagedConnectionFactory mcf = mc.getManagedConnectionFactory();

            if ((mc.getUserName() == null || mc.getUserName() != null && mc.getUserName()
                                                                           .equals(credential.getUserName())) && mcf.equals(this))
            {
               if (cri.equals(mc.getCRI()))
               {
                  if (_log.isTraceEnabled())
                  {
                     _log.trace("Found matching connection: " + mc);
                  }

                  return mc;
               }
            }
         }
      }

      if (_log.isTraceEnabled())
      {
         _log.trace("No matching connection was found");
      }

      return null;
   }

   /**
    * Set the log writer -- NOT SUPPORTED
    *
    * @param out The writer
    * @throws ResourceException Thrown if the writer cant be set
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
    *
    * @return The writer
    * @throws ResourceException Thrown if the writer cant be retrieved
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
    * Get the resource adapter
    *
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
    * Set the resource adapter
    *
    * @param ra The resource adapter
    * @throws ResourceException Thrown if incorrect resource adapter
    */
   public void setResourceAdapter(final ResourceAdapter ra) throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setResourceAdapter(" + ra + ")");
      }

      if (!(ra instanceof QpidResourceAdapter))
      {
         throw new ResourceException("Resource adapter is " + ra);
      }

      this._ra = (QpidResourceAdapter)ra;
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    *
    * @param obj Object with which to compare
    * @return True if this object is the same as the obj argument; false otherwise.
    */
   @Override
   public boolean equals(final Object obj)
   {
      if (obj instanceof QpidRAManagedConnectionFactory)
      {
         QpidRAManagedConnectionFactory other = (QpidRAManagedConnectionFactory)obj;

         return _mcfProperties.equals(other.getProperties()) && _ra.equals(other.getResourceAdapter());
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
   @Override
   public int hashCode()
   {
      int hash = _mcfProperties.hashCode();
      hash += 31 * ((_ra != null) ? _ra.hashCode() : 1);

      return hash;
   }

   /**
    * Get the default session type
    *
    * @return The value
    */
   public String getSessionDefaultType()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getSessionDefaultType()");
      }

      return _mcfProperties.getSessionDefaultType();
   }

   /**
    * Set the default session type
    *
    * @param type either javax.jms.Topic or javax.jms.Queue
    */
   public void setSessionDefaultType(final String type)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setSessionDefaultType(" + type + ")");
      }

      _mcfProperties.setSessionDefaultType(type);
   }

   public String getClientID()
   {
      return _mcfProperties.getClientId();
   }

   public void setClientID(final String clientID)
   {
      _mcfProperties.setClientId(clientID);
   }

   public String getConnectionURL()
   {
      return _mcfProperties.getConnectionURL() ;
   }

   public void setConnectionURL(final String connectionURL)
   {
      _mcfProperties.setConnectionURL(connectionURL);
   }

   public String getPassword()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getDefaultPassword()");
      }
      return _mcfProperties.getPassword();
   }

   public void setPassword(final String defaultPassword)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDefaultPassword(" + defaultPassword + ")");
      }
      _mcfProperties.setPassword(defaultPassword);
   }

   public String getUserName()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getDefaultUsername()");
      }
      return _mcfProperties.getUserName();
   }

   public void setUserName(final String defaultUsername)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setDefaultUsername(" + defaultUsername + ")");
      }
      _mcfProperties.setUserName(defaultUsername);
   }

   public String getHost()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getHost()");
      }
      return _mcfProperties.getHost();
   }

   public void setHost(final String host)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setHost(" + host + ")");
      }
      _mcfProperties.setHost(host);
   }

   public Integer getPort()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPort()");
      }
      return _mcfProperties.getPort();
   }

   public void setPort(final Integer port)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPort(" + port + ")");
      }
      _mcfProperties.setPort(port);
   }

   public String getPath()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getPath()");
      }
      return _mcfProperties.getPath();
   }

   public void setPath(final String path)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setPath(" + path + ")");
      }
      _mcfProperties.setPath(path);
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

      return _mcfProperties.getUseTryLock();
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

      _mcfProperties.setUseTryLock(useTryLock);
   }

   /**
    * Get the connection metadata
    *
    * @return The metadata
    */
   public ConnectionMetaData getMetaData()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getMetadata()");
      }

      return new QpidRAConnectionMetaData();
   }

   /**
    * Get the default connection factory
    *
    * @return The factory
    */
   protected synchronized AMQConnectionFactory getDefaultAMQConnectionFactory() throws ResourceException
   {
      if (_connectionFactory == null)
      {
         try
         {
            _connectionFactory = _ra.createAMQConnectionFactory(_mcfProperties);
         }
         catch (final QpidRAException qpidrae)
         {
            throw new ResourceException("Unexpected exception creating the connection factory", qpidrae) ;
         }
      }
      return _connectionFactory;
   }

   /**
    * Get a clean connection factory
    *
    * @return The factory
    */
   protected AMQConnectionFactory getCleanAMQConnectionFactory() throws ResourceException
   {
      try
      {
         return _ra.createAMQConnectionFactory(_mcfProperties);
      }
      catch (final QpidRAException qpidrae)
      {
         throw new ResourceException("Unexpected exception creating the connection factory", qpidrae) ;
      }
   }

   /**
    * Get the managed connection factory properties
    *
    * @return The properties
    */
   protected QpidRAMCFProperties getProperties()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getProperties()");
      }

      return _mcfProperties;
   }

   /**
    * Get a connection request info instance
    *
    * @param info The instance that should be updated; may be <code>null</code>
    * @return The instance
    * @throws ResourceException
    */
   private QpidRAConnectionRequestInfo getCRI(final QpidRAConnectionRequestInfo info)
      throws ResourceException
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getCRI(" + info + ")");
      }

      if (info == null)
      {
         // Create a default one
         return new QpidRAConnectionRequestInfo(_ra, _mcfProperties.getType());
      }
      else
      {
         // Fill the one with any defaults
         info.setDefaults(_ra);
         return info;
      }
   }

   public Boolean getUseLocalTx()
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("getUseLocalTx()");
      }

      return _mcfProperties.isUseLocalTx();
   }

   public void setUseLocalTx(final Boolean localTx)
   {
      if (_log.isTraceEnabled())
      {
         _log.trace("setUseLocalTx(" + localTx + ")");
      }

      _mcfProperties.setUseLocalTx(localTx);
   }
}
