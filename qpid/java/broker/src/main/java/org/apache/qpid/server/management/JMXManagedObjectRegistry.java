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
package org.apache.qpid.server.management;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.AuthorizeCallback;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HashedInitialiser;

/**
 * This class starts up an MBeanserver. If out of the box agent is being used then there are no security features
 * implemented. To use the security features like user authentication, turn off the jmx options in the "QPID_OPTS" env
 * variable and use JMXMP connector server. If JMXMP connector is not available, then the standard JMXConnector will be
 * used, which again doesn't have user authentication.
 */
public class JMXManagedObjectRegistry implements ManagedObjectRegistry
{
    private static final Logger _log = Logger.getLogger(JMXManagedObjectRegistry.class);

    private final MBeanServer _mbeanServer;
    private Registry _rmiRegistry;
    private JMXServiceURL _jmxURL;

    public JMXManagedObjectRegistry() throws AMQException
    {
        _log.info("Initialising managed object registry using platform MBean server");
        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();

        // Retrieve the config parameters
        boolean platformServer = appRegistry.getConfiguration().getBoolean("management.platform-mbeanserver", true);

        _mbeanServer =
                platformServer ? ManagementFactory.getPlatformMBeanServer()
                : MBeanServerFactory.createMBeanServer(ManagedObject.DOMAIN);
    }


    public void start() throws IOException
    {
        // Check if the "QPID_OPTS" is set to use Out of the Box JMXAgent
        if (areOutOfTheBoxJMXOptionsSet())
        {
            _log.info("JMX: Using the out of the box JMX Agent");
            return;
        }

        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();

        boolean security = appRegistry.getConfiguration().getBoolean("management.security-enabled", false);
        int port = appRegistry.getConfiguration().getInt("management.jmxport", 8999);

        if (security)
        {
            // For SASL using JMXMP
            _jmxURL = new JMXServiceURL("jmxmp", null, port);

            Map env = new HashMap();
            Map<String, PrincipalDatabase> map = appRegistry.getDatabaseManager().getDatabases();
            PrincipalDatabase db = null;

            for (Map.Entry<String, PrincipalDatabase> entry : map.entrySet())
            {
                if (entry.getValue() instanceof Base64MD5PasswordFilePrincipalDatabase)
                {
                    db = entry.getValue();
                    break;
                }
                else if (entry.getValue() instanceof PlainPasswordFilePrincipalDatabase)
                {
                    db = entry.getValue();
                }
            }

            if (db instanceof Base64MD5PasswordFilePrincipalDatabase)
            {
                env.put("jmx.remote.profiles", "SASL/CRAM-MD5");
                CRAMMD5HashedInitialiser initialiser = new CRAMMD5HashedInitialiser();
                initialiser.initialise(db);
                env.put("jmx.remote.sasl.callback.handler", initialiser.getCallbackHandler());
            }
            else if (db instanceof PlainPasswordFilePrincipalDatabase)
            {
                env.put("jmx.remote.profiles", "SASL/PLAIN");
                env.put("jmx.remote.sasl.callback.handler", new UserCallbackHandler(db));
            }

            // Enable the SSL security and server authentication
            /*
           SslRMIClientSocketFactory csf = new SslRMIClientSocketFactory();
           SslRMIServerSocketFactory ssf = new SslRMIServerSocketFactory();
           env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, csf);
           env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, ssf);
            */

            JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(_jmxURL, env, _mbeanServer);
            MBeanServerForwarder mbsf = MBeanInvocationHandlerImpl.newProxyInstance();
            cs.setMBeanServerForwarder(mbsf);
            cs.start();
            _log.warn("JMX: Started JMXConnector server with SASL");

        }
        else
        {
            startJMXConnectorServer(port);
            _log.warn("JMX: Started JMXConnector server with security disabled");
        }
    }

    /**
     * Starts up an RMIRegistry at configured port and attaches a JMXConnectorServer to it.
     *
     * @param port
     *
     * @throws IOException
     */
    private void startJMXConnectorServer(int port) throws IOException
    {
        startRMIRegistry(port);
        _jmxURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi");
        JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(_jmxURL, null, _mbeanServer);
        cs.start();
    }

    public void registerObject(ManagedObject managedObject) throws JMException
    {
        _mbeanServer.registerMBean(managedObject, managedObject.getObjectName());
    }

    public void unregisterObject(ManagedObject managedObject) throws JMException
    {
        _mbeanServer.unregisterMBean(managedObject.getObjectName());
    }

    /**
     * Checks is the "QPID_OPTS" env variable is set to use the out of the box JMXAgent.
     *
     * @return
     */
    private boolean areOutOfTheBoxJMXOptionsSet()
    {
        if (System.getProperty("com.sun.management.jmxremote") != null)
        {
            return true;
        }

        if (System.getProperty("com.sun.management.jmxremote.port") != null)
        {
            return true;
        }

        return false;
    }

    /**
     * Starts the rmi registry at given port
     *
     * @param port
     *
     * @throws RemoteException
     */
    private void startRMIRegistry(int port) throws RemoteException
    {
        System.setProperty("java.rmi.server.randomIDs", "true");
        _rmiRegistry = LocateRegistry.createRegistry(port);
    }

    // stops the RMIRegistry, if it was running and bound to a port
    public void close() throws RemoteException
    {
        if (_rmiRegistry != null)
        {
            // Stopping the RMI registry
            UnicastRemoteObject.unexportObject(_rmiRegistry, true);
        }
    }

    /** This class is used for SASL enabled JMXConnector for performing user authentication. */
    private class UserCallbackHandler implements CallbackHandler
    {
        private final PrincipalDatabase _principalDatabase;

        protected UserCallbackHandler(PrincipalDatabase database)
        {
            _principalDatabase = database;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
        {
            // Retrieve callbacks
            NameCallback ncb = null;
            PasswordCallback pcb = null;
            for (int i = 0; i < callbacks.length; i++)
            {
                if (callbacks[i] instanceof NameCallback)
                {
                    ncb = (NameCallback) callbacks[i];
                }
                else if (callbacks[i] instanceof PasswordCallback)
                {
                    pcb = (PasswordCallback) callbacks[i];
                }
                else if (callbacks[i] instanceof AuthorizeCallback)
                {
                    ((AuthorizeCallback) callbacks[i]).setAuthorized(true);
                }
                else
                {
                    throw new UnsupportedCallbackException(callbacks[i]);
                }
            }

            boolean authorized = false;
            // Process retrieval of password; can get password if username is available in NameCallback
            if ((ncb != null) && (pcb != null))
            {
                String username = ncb.getDefaultName();
                try
                {
                    authorized = _principalDatabase.verifyPassword(username, pcb.getPassword());
                }
                catch (AccountNotFoundException e)
                {
                    IOException ioe = new IOException("User not authorized.  " + e);
                    ioe.initCause(e);
                    throw ioe;
                }
            }

            if (!authorized)
            {
                throw new IOException("User not authorized.");
            }
        }
    }
}
