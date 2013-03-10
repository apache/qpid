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
package org.apache.qpid.server.jmx;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;

import org.apache.qpid.server.security.auth.rmi.RMIPasswordAuthenticator;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

/**
 * This class starts up an MBeanserver. If out of the box agent has been enabled then there are no
 * security features implemented like user authentication and authorisation.
 */
public class JMXManagedObjectRegistry implements ManagedObjectRegistry
{
    private static final Logger _log = Logger.getLogger(JMXManagedObjectRegistry.class);

    private static final String OPERATIONAL_LOGGING_NAME = "JMX";

    private final MBeanServer _mbeanServer;

    private JMXConnectorServer _cs;
    private Registry _rmiRegistry;

    private final Broker _broker;
    private final Port _registryPort;
    private final Port _connectorPort;

     public JMXManagedObjectRegistry(
            Broker broker,
            Port connectorPort, Port registryPort,
            JMXManagement jmxManagement)
    {
        _broker = broker;
        _registryPort = registryPort;
        _connectorPort = connectorPort;

        boolean usePlatformServer = (Boolean)jmxManagement.getAttribute(JMXManagement.USE_PLATFORM_MBEAN_SERVER);

        _mbeanServer =
                usePlatformServer ? ManagementFactory.getPlatformMBeanServer()
                : MBeanServerFactory.createMBeanServer(ManagedObject.DOMAIN);
     }

    @Override
    public void start() throws IOException
    {
        CurrentActor.get().message(ManagementConsoleMessages.STARTUP(OPERATIONAL_LOGGING_NAME));

        //check if system properties are set to use the JVM's out-of-the-box JMXAgent
        if (areOutOfTheBoxJMXOptionsSet())
        {
            CurrentActor.get().message(ManagementConsoleMessages.READY(OPERATIONAL_LOGGING_NAME));
        }
        else
        {
            startRegistryAndConnector();
        }
    }

    private void startRegistryAndConnector() throws IOException
    {
        //Socket factories for the RMIConnectorServer, either default or SSL depending on configuration
        RMIClientSocketFactory csf;
        RMIServerSocketFactory ssf;

        //check ssl enabled option on connector port (note we don't provide ssl for registry server at
        //moment).
        boolean connectorSslEnabled = _connectorPort.getTransports().contains(Transport.SSL);

        if (connectorSslEnabled)
        {
            String keyStorePath = System.getProperty("javax.net.ssl.keyStore");
            String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");

            validateKeyStoreProperties(keyStorePath, keyStorePassword);

            CurrentActor.get().message(ManagementConsoleMessages.SSL_KEYSTORE(keyStorePath));

            //create the SSL RMI socket factories
            csf = new SslRMIClientSocketFactory();
            ssf = new SslRMIServerSocketFactory();
        }
        else
        {
            //Do not specify any specific RMI socket factories, resulting in use of the defaults.
            csf = null;
            ssf = null;
        }

        int jmxPortRegistryServer = _registryPort.getPort();
        int jmxPortConnectorServer = _connectorPort.getPort();

        //add a JMXAuthenticator implementation the env map to authenticate the RMI based JMX connector server
        RMIPasswordAuthenticator rmipa = new RMIPasswordAuthenticator(_broker, new InetSocketAddress(jmxPortConnectorServer));
        HashMap<String,Object> connectorEnv = new HashMap<String,Object>();
        connectorEnv.put(JMXConnectorServer.AUTHENTICATOR, rmipa);

        System.setProperty("java.rmi.server.randomIDs", "true");
        boolean useCustomSocketFactory = Boolean.parseBoolean(System.getProperty(BrokerProperties.PROPERTY_USE_CUSTOM_RMI_SOCKET_FACTORY, Boolean.TRUE.toString()));

        /*
         * Start a RMI registry on the management port, to hold the JMX RMI ConnectorServer stub.
         * Using custom socket factory to prevent anyone (including us unfortunately) binding to the registry using RMI.
         * As a result, only binds made using the object reference will succeed, thus securing it from external change.
         */
        _rmiRegistry = createRmiRegistry(jmxPortRegistryServer, useCustomSocketFactory);

        /*
         * We must now create the RMI ConnectorServer manually, as the JMX Factory methods use RMI calls
         * to bind the ConnectorServer to the registry, which will now fail as for security we have
         * locked it from any RMI based modifications, including our own. Instead, we will manually bind
         * the RMIConnectorServer stub to the registry using its object reference, which will still succeed.
         *
         * The registry is exported on the defined management port 'port'.
         */
        final UsernameCachingRMIJRMPServer usernameCachingRmiServer = new UsernameCachingRMIJRMPServer(jmxPortConnectorServer, csf, ssf, connectorEnv);

        final String localHostName = getLocalhost();
        final JMXServiceURL externalUrl = new JMXServiceURL(
                "service:jmx:rmi://"+localHostName+":"+(jmxPortConnectorServer)+"/jndi/rmi://"+localHostName+":"+jmxPortRegistryServer+"/jmxrmi");

        final JMXServiceURL internalUrl = new JMXServiceURL("rmi", localHostName, jmxPortConnectorServer);
        _cs = new RMIConnectorServer(internalUrl, connectorEnv, usernameCachingRmiServer, _mbeanServer)
        {
            @Override
            public synchronized void start() throws IOException
            {
                try
                {
                    //manually bind the connector server to the registry at key 'jmxrmi', like the out-of-the-box agent
                    _rmiRegistry.bind("jmxrmi", usernameCachingRmiServer);
                }
                catch (AlreadyBoundException abe)
                {
                    //key was already in use. shouldnt happen here as its a new registry, unbindable by normal means.

                    //IOExceptions are the only checked type throwable by the method, wrap and rethrow
                    IOException ioe = new IOException(abe.getMessage());
                    ioe.initCause(abe);
                    throw ioe;
                }

                //now do the normal tasks
                super.start();
            }

            @Override
            public synchronized void stop() throws IOException
            {
                try
                {
                    if (_rmiRegistry != null)
                    {
                        _rmiRegistry.unbind("jmxrmi");
                    }
                }
                catch (NotBoundException nbe)
                {
                    _log.error("Failed to unbind jmxrmi", nbe);
                    //ignore
                }

                //now do the normal tasks
                super.stop();
            }

            @Override
            public JMXServiceURL getAddress()
            {
                //must return our pre-crafted url that includes the full details, inc JNDI details
                return externalUrl;
            }
        };

        //Add the custom invoker as an MBeanServerForwarder, and start the RMIConnectorServer.
        MBeanServerForwarder mbsf = MBeanInvocationHandlerImpl.newProxyInstance(_broker);
        _cs.setMBeanServerForwarder(mbsf);

        // Install a ManagementLogonLogoffReporter so we can report as users logon/logoff
        ManagementLogonLogoffReporter jmxManagementUserLogonLogoffReporter = new ManagementLogonLogoffReporter(_broker.getRootMessageLogger(), usernameCachingRmiServer);
        _cs.addNotificationListener(jmxManagementUserLogonLogoffReporter, jmxManagementUserLogonLogoffReporter, null);

        // Install the usernameCachingRmiServer as a listener so it may cleanup as clients disconnect
        _cs.addNotificationListener(usernameCachingRmiServer, usernameCachingRmiServer, null);

        _cs.start();

        String connectorServer = (connectorSslEnabled ? "SSL " : "") + "JMX RMIConnectorServer";
        CurrentActor.get().message(ManagementConsoleMessages.LISTENING(connectorServer, jmxPortConnectorServer));
        CurrentActor.get().message(ManagementConsoleMessages.READY(OPERATIONAL_LOGGING_NAME));
    }

    private Registry createRmiRegistry(int jmxPortRegistryServer, boolean useCustomRmiRegistry)
            throws RemoteException
    {
        Registry rmiRegistry;
        if(useCustomRmiRegistry)
        {
            _log.debug("Using custom RMIServerSocketFactory");
            rmiRegistry = LocateRegistry.createRegistry(jmxPortRegistryServer, null, new CustomRMIServerSocketFactory());
        }
        else
        {
            _log.debug("Using default RMIServerSocketFactory");
            rmiRegistry = LocateRegistry.createRegistry(jmxPortRegistryServer, null, null);
        }

        CurrentActor.get().message(ManagementConsoleMessages.LISTENING("RMI Registry", jmxPortRegistryServer));
        return rmiRegistry;
    }

    private void validateKeyStoreProperties(String keyStorePath, String keyStorePassword) throws FileNotFoundException
    {
        if (keyStorePath == null)
        {
            throw new IllegalConfigurationException("JVM system property 'javax.net.ssl.keyStore' is not set, "
                    + "unable to start requested SSL protected JMX connector");
        }
        if (keyStorePassword == null)
        {
            throw new IllegalConfigurationException( "JVM system property 'javax.net.ssl.keyStorePassword' is not set, "
                    + "unable to start requested SSL protected JMX connector");
        }

        File ksf = new File(keyStorePath);
        if (!ksf.exists())
        {
            throw new FileNotFoundException("Cannot find SSL keystore file for JMX management: " + ksf);
        }
        if (!ksf.canRead())
        {
            throw new FileNotFoundException("Cannot read SSL keystore file for JMX management: "
                                            + ksf +  ". Check permissions.");
        }
    }

    @Override
    public void registerObject(ManagedObject managedObject) throws JMException
    {
        _mbeanServer.registerMBean(managedObject, managedObject.getObjectName());
    }

    @Override
    public void unregisterObject(ManagedObject managedObject) throws JMException
    {
        _mbeanServer.unregisterMBean(managedObject.getObjectName());
    }

    @Override
    public void close()
    {
        _log.debug("close() called");

        closeConnectorAndRegistryServers();

        unregisterAllMbeans();

        CurrentActor.get().message(ManagementConsoleMessages.STOPPED(OPERATIONAL_LOGGING_NAME));
    }

    private void closeConnectorAndRegistryServers()
    {
        closeConnectorServer();
        closeRegistryServer();
    }

    // checks if the system properties are set which enable the JVM's out-of-the-box JMXAgent.
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

    private String getLocalhost()
    {
        String localHost;
        try
        {
            localHost = InetAddress.getLocalHost().getHostName();
        }
        catch(UnknownHostException ex)
        {
            localHost="127.0.0.1";
        }
        return localHost;
    }

    private void closeRegistryServer()
    {
        if (_rmiRegistry != null)
        {
            // Stopping the RMI registry
            CurrentActor.get().message(ManagementConsoleMessages.SHUTTING_DOWN("RMI Registry", _registryPort.getPort()));
            try
            {
                boolean success = UnicastRemoteObject.unexportObject(_rmiRegistry, false);
                if (!success)
                {
                    _log.warn("Failed to unexport object " + _rmiRegistry);
                }
            }
            catch (NoSuchObjectException e)
            {
                _log.error("Exception while closing the RMI Registry: ", e);
            }
            finally
            {
                _rmiRegistry = null;
            }
        }
    }

    private void closeConnectorServer()
    {
        if (_cs != null)
        {
            // Stopping the JMX ConnectorServer
            try
            {
                CurrentActor.get().message(ManagementConsoleMessages.SHUTTING_DOWN("JMX RMIConnectorServer", _cs.getAddress().getPort()));
                _cs.stop();
            }
            catch (IOException e)
            {
                _log.error("Exception while closing the JMX ConnectorServer: ",  e);
            }
            finally
            {
                _cs = null;
            }
        }
    }

    private void unregisterAllMbeans()
    {
        //ObjectName query to gather all Qpid related MBeans
        ObjectName mbeanNameQuery = null;
        try
        {
            mbeanNameQuery = new ObjectName(ManagedObject.DOMAIN + ":*");
        }
        catch (Exception e1)
        {
            _log.warn("Unable to generate MBean ObjectName query for close operation");
        }

        for (ObjectName name : _mbeanServer.queryNames(mbeanNameQuery, null))
        {
            try
            {
                _mbeanServer.unregisterMBean(name);
            }
            catch (JMException e)
            {
                _log.error("Exception unregistering MBean '"+ name +"': " + e.getMessage());
            }
        }
    }

}
