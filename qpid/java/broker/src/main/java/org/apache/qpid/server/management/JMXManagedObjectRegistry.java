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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.rmi.RMIPasswordAuthenticator;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HashedInitialiser;
import org.apache.qpid.server.security.auth.sasl.plain.PlainInitialiser;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.management.remote.rmi.RMIJRMPServerImpl;
import javax.management.remote.rmi.RMIServerImpl;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

/**
 * This class starts up an MBeanserver. If out of the box agent has been enabled then there are no 
 * security features implemented like user authentication and authorisation.
 */
public class JMXManagedObjectRegistry implements ManagedObjectRegistry
{
    private static final Logger _log = Logger.getLogger(JMXManagedObjectRegistry.class);
    private static final Logger _startupLog = Logger.getLogger("Qpid.Broker");
    
    public static final String MANAGEMENT_PORT_CONFIG_PATH = "management.jmxport";
    public static final int MANAGEMENT_PORT_DEFAULT = 8999;
    public static final int PORT_EXPORT_OFFSET = 100;

    private final MBeanServer _mbeanServer;
    private Registry _rmiRegistry;
    

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


    public void start() throws IOException, ConfigurationException
    {
        //check if system properties are set to use the JVM's out-of-the-box JMXAgent
        if (areOutOfTheBoxJMXOptionsSet())
        {
            _log.warn("JMX: Using the out of the box JMX Agent");
            _startupLog.warn("JMX: Using the out of the box JMX Agent");
            return;
        }

        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();

        boolean jmxmpSecurity = appRegistry.getConfiguration().getBoolean("management.security-enabled", false);
        int port = appRegistry.getConfiguration().getInt(MANAGEMENT_PORT_CONFIG_PATH, MANAGEMENT_PORT_DEFAULT);

        //retrieve the Principal Database assigned to JMX authentication duties
        String jmxDatabaseName = appRegistry.getConfiguration().getString("security.jmx.principal-database");
        Map<String, PrincipalDatabase> map = appRegistry.getDatabaseManager().getDatabases();        
        PrincipalDatabase db = map.get(jmxDatabaseName);

        final JMXConnectorServer cs;
        HashMap<String,Object> env = new HashMap<String,Object>();

        if (jmxmpSecurity)
        {
            // For SASL using JMXMP
            JMXServiceURL jmxURL = new JMXServiceURL("jmxmp", null, port);

            String saslType = null;
            if (db instanceof Base64MD5PasswordFilePrincipalDatabase)
            {
                saslType = "SASL/CRAM-MD5";
                env.put("jmx.remote.profiles", "SASL/CRAM-MD5");
                CRAMMD5HashedInitialiser initialiser = new CRAMMD5HashedInitialiser();
                initialiser.initialise(db);
                env.put("jmx.remote.sasl.callback.handler", initialiser.getCallbackHandler());
            }
            else if (db instanceof PlainPasswordFilePrincipalDatabase)
            {
                saslType = "SASL/PLAIN";
                PlainInitialiser initialiser = new PlainInitialiser();
                initialiser.initialise(db);
                env.put("jmx.remote.sasl.callback.handler", initialiser.getCallbackHandler());
                env.put("jmx.remote.profiles", "SASL/PLAIN");
            }

            //workaround NPE generated from env map classloader issue when using Eclipse 3.4 to launch
            env.put("jmx.remote.profile.provider.class.loader", this.getClass().getClassLoader());

            _log.warn("Starting JMXMP based JMX ConnectorServer on port '" + port + "' with " + saslType);
            _startupLog.warn("Starting JMXMP based JMX ConnectorServer on port '" + port + "' with " + saslType);
            
            cs = JMXConnectorServerFactory.newJMXConnectorServer(jmxURL, env, _mbeanServer);
        }
        else
        {   
            //Socket factories for the RMIConnectorServer, either default or SLL depending on configuration
            RMIClientSocketFactory csf;
            RMIServerSocketFactory ssf;
            
            //check ssl enabled option in config, default to true if option is not set
            boolean sslEnabled = appRegistry.getConfiguration().getBoolean("management.ssl.enabled", true);

            if (sslEnabled)
            {
                //set the SSL related system properties used by the SSL RMI socket factories to the values
                //given in the configuration file, unless command line settings have already been specified
                String keyStorePath;
                
                if(System.getProperty("javax.net.ssl.keyStore") != null)
                {
                    keyStorePath = System.getProperty("javax.net.ssl.keyStore");
                }
                else{
                    keyStorePath = appRegistry.getConfiguration().getString("management.ssl.keyStorePath", null);
                }
                
                //check the keystore path value is valid
                if (keyStorePath == null)
                {
                    throw new ConfigurationException("JMX management SSL keystore path not defined, " +
                    		                         "unable to start SSL protected JMX ConnectorServer");
                }
                else
                {
                    //ensure the system property is set
                    System.setProperty("javax.net.ssl.keyStore", keyStorePath);
                    
                    //check the file is usable
                    File ksf = new File(keyStorePath);
                    
                    if (!ksf.exists())
                    {
                        throw new FileNotFoundException("Cannot find JMX management SSL keystore file " + ksf);
                    }
                    if (!ksf.canRead())
                    {
                        throw new FileNotFoundException("Cannot read JMX management SSL keystore file: " 
                                                        + ksf +  ". Check permissions.");
                    }
                    
                    _log.info("JMX ConnectorServer using SSL keystore file " + ksf.getAbsolutePath());
                    _startupLog.info("JMX ConnectorServer using SSL keystore file " + ksf.getAbsolutePath());
                }

                //check the key store password is set
                if (System.getProperty("javax.net.ssl.keyStorePassword") == null)
                {
                
                    if (appRegistry.getConfiguration().getString("management.ssl.keyStorePassword") == null)
                    {
                        throw new ConfigurationException("JMX management SSL keystore password not defined, " +
                        		                         "unable to start requested SSL protected JMX server");
                    }
                    else
                    {
                        System.setProperty("javax.net.ssl.keyStorePassword",
                                appRegistry.getConfiguration().getString("management.ssl.keyStorePassword"));
                    }
                }

                //create the SSL RMI socket factories
                csf = new SslRMIClientSocketFactory();
                ssf = new SslRMIServerSocketFactory();

                _log.warn("Starting JMX ConnectorServer on port '"+ port + "' (+" + 
                        (port +PORT_EXPORT_OFFSET) + ") with SSL");
                _startupLog.warn("Starting JMX ConnectorServer on port '"+ port + "' (+" + 
                        (port +PORT_EXPORT_OFFSET) + ") with SSL");
            }
            else
            {
                //Do not specify any specific RMI socket factories, resulting in use of the defaults.
                csf = null;
                ssf = null;
                
                _log.warn("Starting JMX ConnectorServer on port '" + port + "' (+" + (port +PORT_EXPORT_OFFSET) + ")");
                _startupLog.warn("Starting JMX ConnectorServer on port '" + port + "' (+" + (port +PORT_EXPORT_OFFSET) + ")");
            }
            
            //add a JMXAuthenticator implementation the env map to authenticate the RMI based JMX connector server
            RMIPasswordAuthenticator rmipa = new RMIPasswordAuthenticator();
            rmipa.setPrincipalDatabase(db);
            env.put(JMXConnectorServer.AUTHENTICATOR, rmipa);
            
            /*
             * Start a RMI registry on the management port, to hold the JMX RMI ConnectorServer stub. 
             * Using custom socket factory to prevent anyone (including us unfortunately) binding to the registry using RMI.
             * As a result, only binds made using the object reference will succeed, thus securing it from external change. 
             */
            System.setProperty("java.rmi.server.randomIDs", "true");
            _rmiRegistry = LocateRegistry.createRegistry(port, null, new CustomRMIServerSocketFactory());
            
            /*
             * We must now create the RMI ConnectorServer manually, as the JMX Factory methods use RMI calls 
             * to bind the ConnectorServer to the registry, which will now fail as for security we have
             * locked it from any RMI based modifications, including our own. Instead, we will manually bind 
             * the RMIConnectorServer stub to the registry using its object reference, which will still succeed.
             * 
             * The registry is exported on the defined management port 'port'. We will export the RMIConnectorServer
             * on 'port +1'. Use of these two well-defined ports will ease any navigation through firewall's. 
             */
            final RMIServerImpl rmiConnectorServerStub = new RMIJRMPServerImpl(port+PORT_EXPORT_OFFSET, csf, ssf, env); 
            final String hostname = InetAddress.getLocalHost().getHostName();
            final JMXServiceURL externalUrl = new JMXServiceURL(
                    "service:jmx:rmi://"+hostname+":"+(port+PORT_EXPORT_OFFSET)+"/jndi/rmi://"+hostname+":"+port+"/jmxrmi");

            final JMXServiceURL internalUrl = new JMXServiceURL("rmi", hostname, port+PORT_EXPORT_OFFSET);
            cs = new RMIConnectorServer(internalUrl, env, rmiConnectorServerStub, _mbeanServer)
            {   
                @Override  
                public synchronized void start() throws IOException
                {   
                    try
                    {   
                        //manually bind the connector server to the registry at key 'jmxrmi', like the out-of-the-box agent                        
                        _rmiRegistry.bind("jmxrmi", rmiConnectorServerStub);   
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
                public JMXServiceURL getAddress()
                {
                    //must return our pre-crafted url that includes the full details, inc JNDI details
                    return externalUrl;
                }   

            };   
        }

        //Add the custom invoker as an MBeanServerForwarder, and start the RMIConnectorServer.
        MBeanServerForwarder mbsf = MBeanInvocationHandlerImpl.newProxyInstance();
        cs.setMBeanServerForwarder(mbsf);
        cs.start();
    }

    /*
     * Custom RMIServerSocketFactory class, used to prevent updates to the RMI registry. 
     * Supplied to the registry at creation, this will prevent RMI-based operations on the
     * registry such as attempting to bind a new object, thereby securing it from tampering.
     * This is accomplished by always returning null when attempting to determine the address
     * of the caller, thus ensuring the registry will refuse the attempt. Calls to bind etc
     * made using the object reference will not be affected and continue to operate normally.
     */
    
    private class CustomRMIServerSocketFactory implements RMIServerSocketFactory
    {

        public ServerSocket createServerSocket(int port) throws IOException
        {
            return new NoLocalAddressServerSocket(port);
        }

        private class NoLocalAddressServerSocket extends ServerSocket
        {
            NoLocalAddressServerSocket(int port) throws IOException
            {
                super(port);
            }

            @Override
            public Socket accept() throws IOException
            {
                Socket s = new NoLocalAddressSocket();
                super.implAccept(s);
                return s;
            }
        }

        private class NoLocalAddressSocket extends Socket
        {
            @Override
            public InetAddress getInetAddress()
            {
                return null;
            }
        }
    }


    public void registerObject(ManagedObject managedObject) throws JMException
    {
        _mbeanServer.registerMBean(managedObject, managedObject.getObjectName());
    }

    public void unregisterObject(ManagedObject managedObject) throws JMException
    {
        _mbeanServer.unregisterMBean(managedObject.getObjectName());
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

    // stops the RMIRegistry, if it was running and bound to a port
    public void close() throws RemoteException
    {
        if (_rmiRegistry != null)
        {
            // Stopping the RMI registry
            UnicastRemoteObject.unexportObject(_rmiRegistry, true);
        }
    }

}
