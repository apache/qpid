/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.security.auth.manager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.configuration.PropertyException;
import org.apache.qpid.configuration.PropertyUtils;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.management.AMQUserManagementMBean;
import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;
import org.apache.qpid.server.security.auth.sasl.JCAProvider;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;


/**
 * Concrete implementation of the AuthenticationManager that determines if supplied
 * user credentials match those appearing in a PrincipalDatabase.   The implementation
 * of the PrincipalDatabase is determined from the configuration.
 * 
 * This implementation also registers the JMX UserManagemement MBean.
 * 
 * This plugin expects configuration such as:
 *
 * <pre>
 * &lt;pd-auth-manager&gt;
 *   &lt;principal-database&gt;
 *      &lt;class&gt;org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase&lt;/class&gt;
 *      &lt;attributes&gt;
 *         &lt;attribute&gt;
 *              &lt;name>passwordFile&lt;/name&gt;
 *              &lt;value>${conf}/passwd&lt;/value&gt;
 *          &lt;/attribute&gt;
 *      &lt;/attributes&gt;
 *   &lt;/principal-database&gt;
 * &lt;/pd-auth-manager&gt;
 * </pre>
 */
public class PrincipalDatabaseAuthenticationManager implements AuthenticationManager
{
    private static final Logger _logger = Logger.getLogger(PrincipalDatabaseAuthenticationManager.class);

    /** The list of mechanisms, in the order in which they are configured (i.e. preferred order) */
    private String _mechanisms;

    /** Maps from the mechanism to the callback handler to use for handling those requests */
    private final Map<String, CallbackHandler> _callbackHandlerMap = new HashMap<String, CallbackHandler>();

    /**
     * Maps from the mechanism to the properties used to initialise the server. See the method Sasl.createSaslServer for
     * details of the use of these properties. This map is populated during initialisation of each provider.
     */
    private final Map<String, Map<String, ?>> _serverCreationProperties = new HashMap<String, Map<String, ?>>();

    protected PrincipalDatabase _principalDatabase = null;

    protected AMQUserManagementMBean _mbean = null;

    public static final AuthenticationManagerPluginFactory<PrincipalDatabaseAuthenticationManager> FACTORY = new AuthenticationManagerPluginFactory<PrincipalDatabaseAuthenticationManager>()
    {
        public PrincipalDatabaseAuthenticationManager newInstance(final ConfigurationPlugin config) throws ConfigurationException
        {
            final PrincipalDatabaseAuthenticationManagerConfiguration configuration = config.getConfiguration(PrincipalDatabaseAuthenticationManagerConfiguration.class.getName());

            // If there is no configuration for this plugin then don't load it.
            if (configuration == null)
            {
                _logger.info("No authentication-manager configuration found for PrincipalDatabaseAuthenticationManager");
                return null;
            }

            final PrincipalDatabaseAuthenticationManager pdam = new PrincipalDatabaseAuthenticationManager();
            pdam.configure(configuration);
            pdam.initialise();
            return pdam;
        }

        public Class<PrincipalDatabaseAuthenticationManager> getPluginClass()
        {
            return PrincipalDatabaseAuthenticationManager.class;
        }

        public String getPluginName()
        {
            return PrincipalDatabaseAuthenticationManager.class.getName();
        }
    };

    public static class PrincipalDatabaseAuthenticationManagerConfiguration extends ConfigurationPlugin {
 
        public static final ConfigurationPluginFactory FACTORY = new ConfigurationPluginFactory()
        {
            public List<String> getParentPaths()
            {
                return Arrays.asList("security.pd-auth-manager");
            }

            public ConfigurationPlugin newInstance(final String path, final Configuration config) throws ConfigurationException
            {
                final ConfigurationPlugin instance = new PrincipalDatabaseAuthenticationManagerConfiguration();
                
                instance.setConfiguration(path, config);
                return instance;
            }
        };

        public String[] getElementsProcessed()
        {
            return new String[] {"principal-database.class",
                                 "principal-database.attributes.attribute.name",
                                 "principal-database.attributes.attribute.value"};
        }

        public void validateConfiguration() throws ConfigurationException
        {
        }
  
        public String getPrincipalDatabaseClass()
        {
            return _configuration.getString("principal-database.class");
        }
  
        public Map<String,String> getPdClassAttributeMap() throws ConfigurationException
        {
            final List<String> argumentNames = _configuration.getList("principal-database.attributes.attribute.name");
            final List<String> argumentValues = _configuration.getList("principal-database.attributes.attribute.value");
            final Map<String,String> attributes = new HashMap<String,String>(argumentNames.size());

            for (int i = 0; i < argumentNames.size(); i++)
            {
                final String argName = argumentNames.get(i);
                final String argValue = argumentValues.get(i);

                attributes.put(argName, argValue);
            }

            return Collections.unmodifiableMap(attributes);
        }
    }

    protected PrincipalDatabaseAuthenticationManager()  
    {
    }

    public void initialise()
    {
        final Map<String, Class<? extends SaslServerFactory>> providerMap = new TreeMap<String, Class<? extends SaslServerFactory>>();

        initialiseAuthenticationMechanisms(providerMap, _principalDatabase);

        if (providerMap.size() > 0)
        {
            // Ensure we are used before the defaults
            if (Security.insertProviderAt(new JCAProvider(PROVIDER_NAME, providerMap), 1) == -1)
            {
                _logger.error("Unable to load custom SASL providers. Qpid custom SASL authenticators unavailable.");
            }
            else
            {
                _logger.info("Additional SASL providers successfully registered.");
            }
        }
        else
        {
            _logger.warn("No additional SASL providers registered.");
        }

        registerManagement();
    }

    private void initialiseAuthenticationMechanisms(Map<String, Class<? extends SaslServerFactory>> providerMap, PrincipalDatabase database) 
    {
        if (database == null || database.getMechanisms().size() == 0)
        {
            _logger.warn("No Database or no mechanisms to initialise authentication");
            return;
        }

        for (Map.Entry<String, AuthenticationProviderInitialiser> mechanism : database.getMechanisms().entrySet())
        {
            initialiseAuthenticationMechanism(mechanism.getKey(), mechanism.getValue(), providerMap);
        }
    }

    private void initialiseAuthenticationMechanism(String mechanism, AuthenticationProviderInitialiser initialiser,
                                                   Map<String, Class<? extends SaslServerFactory>> providerMap)
    {
        if (_mechanisms == null)
        {
            _mechanisms = mechanism;
        }
        else
        {
            // simple append should be fine since the number of mechanisms is small and this is a one time initialisation
            _mechanisms = _mechanisms + " " + mechanism;
        }
        _callbackHandlerMap.put(mechanism, initialiser.getCallbackHandler());
        _serverCreationProperties.put(mechanism, initialiser.getProperties());
        Class<? extends SaslServerFactory> factory = initialiser.getServerFactoryClassForJCARegistration();
        if (factory != null)
        {
            providerMap.put(mechanism, factory);
        }
        _logger.info("Initialised " + mechanism + " SASL provider successfully");
    }

    /**
     * @see org.apache.qpid.server.plugins.Plugin#configure(org.apache.qpid.server.configuration.plugins.ConfigurationPlugin)
     */
    public void configure(final ConfigurationPlugin config) throws ConfigurationException
    {
        final PrincipalDatabaseAuthenticationManagerConfiguration pdamConfig = (PrincipalDatabaseAuthenticationManagerConfiguration) config;
        final String pdClazz = pdamConfig.getPrincipalDatabaseClass();

        _logger.info("PrincipalDatabase concrete implementation : " + pdClazz);

        _principalDatabase = createPrincipalDatabaseImpl(pdClazz);

        configPrincipalDatabase(_principalDatabase, pdamConfig);        
    }

    public String getMechanisms()
    {
        return _mechanisms;
    }

    public SaslServer createSaslServer(String mechanism, String localFQDN) throws SaslException
    {
        return Sasl.createSaslServer(mechanism, "AMQP", localFQDN, _serverCreationProperties.get(mechanism),
                                     _callbackHandlerMap.get(mechanism));
    }

    /**
     * @see org.apache.qpid.server.security.auth.manager.AuthenticationManager#authenticate(SaslServer, byte[])
     */
    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete())
            {
                final Subject subject = new Subject();
                subject.getPrincipals().add(new UsernamePrincipal(server.getAuthorizationID()));
                return new AuthenticationResult(subject);
            }
            else
            {
                return new AuthenticationResult(challenge, AuthenticationResult.AuthenticationStatus.CONTINUE);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    public CallbackHandler getHandler(String mechanism)
    {
        return _callbackHandlerMap.get(mechanism);
    }

    /**
     * @see org.apache.qpid.server.security.auth.manager.AuthenticationManager#authenticate(String, String)
     */
    public AuthenticationResult authenticate(final String username, final String password)
    {
        try
        {
            if (_principalDatabase.verifyPassword(username, password.toCharArray()))
            {
                final Subject subject = new Subject();
                subject.getPrincipals().add(new UsernamePrincipal(username));
                return new AuthenticationResult(subject);
            }
            else
            {
                return new AuthenticationResult(AuthenticationStatus.CONTINUE);
            }
        }
        catch (AccountNotFoundException e)
        {
            return new AuthenticationResult(AuthenticationStatus.CONTINUE);
        }
    }

    public void close()
    {
        _mechanisms = null;
        Security.removeProvider(PROVIDER_NAME);

        unregisterManagement();
    }

    private PrincipalDatabase createPrincipalDatabaseImpl(final String pdClazz) throws ConfigurationException
    {
        try
        {
            return (PrincipalDatabase) Class.forName(pdClazz).newInstance();
        }
        catch (InstantiationException ie)
        {
            throw new ConfigurationException("Cannot instantiate " + pdClazz, ie);
        }
        catch (IllegalAccessException iae)
        {
            throw new ConfigurationException("Cannot access " + pdClazz, iae);
        }
        catch (ClassNotFoundException cnfe)
        {
            throw new ConfigurationException("Cannot load " + pdClazz + " implementation", cnfe);
        }
        catch (ClassCastException cce)
        {
            throw new ConfigurationException("Expecting a " + PrincipalDatabase.class + " implementation", cce);
        }
    }

    private void configPrincipalDatabase(final PrincipalDatabase principalDatabase, final PrincipalDatabaseAuthenticationManagerConfiguration config)
            throws ConfigurationException
    {

        final Map<String,String> attributes = config.getPdClassAttributeMap();

        for (Iterator<Entry<String, String>> iterator = attributes.entrySet().iterator(); iterator.hasNext();)
        {
            final Entry<String, String> nameValuePair = iterator.next();
            final String methodName = generateSetterName(nameValuePair.getKey());
            final Method method;
            try
            {
                method = principalDatabase.getClass().getMethod(methodName, String.class);
            }
            catch (Exception e)
            {
                throw new ConfigurationException("No method " + methodName + " found in class "
                        + principalDatabase.getClass()
                        + " hence unable to configure principal database. The method must be public and "
                        + "have a single String argument with a void return type", e);
            }
            try
            {
                method.invoke(principalDatabase, PropertyUtils.replaceProperties(nameValuePair.getValue()));
            }
            catch (IllegalArgumentException e)
            {
                throw new ConfigurationException(e.getMessage(), e);
            }
            catch (PropertyException e)
            {
                throw new ConfigurationException(e.getMessage(), e);
            }
            catch (IllegalAccessException e)
            {
                throw new ConfigurationException(e.getMessage(), e);
            }
            catch (InvocationTargetException e)
            {
                // QPID-1347..  InvocationTargetException wraps the checked exception thrown from the reflective
                // method call.  Pull out the underlying message and cause to make these more apparent to the user.
                throw new ConfigurationException(e.getCause().getMessage(), e.getCause());
            }
        }
    }

    private String generateSetterName(String argName) throws ConfigurationException
    {
        if ((argName == null) || (argName.length() == 0))
        {
            throw new ConfigurationException("Argument names must have length >= 1 character");
        }

        if (Character.isLowerCase(argName.charAt(0)))
        {
            argName = Character.toUpperCase(argName.charAt(0)) + argName.substring(1);
        }

        final String methodName = "set" + argName;
        return methodName;
    }

    protected void setPrincipalDatabase(final PrincipalDatabase principalDatabase)
    {
        _principalDatabase = principalDatabase;
    }

    protected void registerManagement()
    {
        try
        {
            _logger.info("Registering UserManagementMBean");

            _mbean = new AMQUserManagementMBean();
            _mbean.setPrincipalDatabase(_principalDatabase);
            _mbean.register();
        }
        catch (Exception e)
        {
            _logger.warn("User management disabled as unable to create MBean:", e);
            _mbean = null;
        }
    }

    protected void unregisterManagement()
    {
        try
        {
            if (_mbean != null)
            {
                _logger.info("Unregistering UserManagementMBean");
                _mbean.unregister();
            }
        }
        catch (Exception e)
        {
            _logger.warn("Failed to unregister User management MBean:", e);
        }
        finally
        {
            _mbean = null;
        }
    }
}
