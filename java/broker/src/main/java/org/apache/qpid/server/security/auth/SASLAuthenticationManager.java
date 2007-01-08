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
package org.apache.qpid.server.security.auth;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.configuration.PropertyUtils;
import org.apache.qpid.framing.AMQShortString;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.security.Security;

public class SASLAuthenticationManager implements AuthenticationManager
{
    private static final Logger _log = Logger.getLogger(SASLAuthenticationManager.class);

    /**
     * The list of mechanisms, in the order in which they are configured (i.e. preferred order)
     */
    private String _mechanisms;

    /**
     * Maps from the mechanism to the callback handler to use for handling those requests
     */
    private Map<String, CallbackHandler> _callbackHandlerMap = new HashMap<String, CallbackHandler>();

    /**
     * Maps from the mechanism to the properties used to initialise the server. See the method
     * Sasl.createSaslServer for details of the use of these properties. This map is populated during initialisation
     * of each provider.
     */
    private Map<String, Map<String, ?>> _serverCreationProperties = new HashMap<String, Map<String, ?>>();

    public SASLAuthenticationManager() throws Exception
    {
        _log.info("Initialising SASL authentication manager");
        Map<String, PrincipalDatabase> databases = initialisePrincipalDatabases();
        initialiseAuthenticationMechanisms(databases);
    }

    private Map<String, PrincipalDatabase> initialisePrincipalDatabases() throws Exception
    {
        Configuration config = ApplicationRegistry.getInstance().getConfiguration();
        List<String> databaseNames = config.getList("security.principal-databases.principal-database.name");
        List<String> databaseClasses = config.getList("security.principal-databases.principal-database.class");
        Map<String, PrincipalDatabase> databases = new HashMap<String, PrincipalDatabase>();
        for (int i = 0; i < databaseNames.size(); i++)
        {
            Object o;
            try
            {
                o = Class.forName(databaseClasses.get(i)).newInstance();
            }
            catch (Exception e)
            {
                throw new Exception("Error initialising principal database: " + e, e);
            }

            if (!(o instanceof PrincipalDatabase))
            {
                throw new Exception("Principal databases must implement the PrincipalDatabase interface");
            }

            initialisePrincipalDatabase((PrincipalDatabase) o, config, i);

            String name = databaseNames.get(i);
            if (name == null || name.length() == 0)
            {
                throw new Exception("Principal database names must have length greater than or equal to one character");
            }
            PrincipalDatabase pd = databases.get(name);
            if (pd != null)
            {
                throw new Exception("Duplicate principal database name provided");
            }
            _log.info("Initialised principal database " + name + " successfully");
            databases.put(name, (PrincipalDatabase) o);
        }
        return databases;
    }

    private void initialisePrincipalDatabase(PrincipalDatabase principalDatabase, Configuration config, int index)
            throws Exception
    {
        String baseName = "security.principal-databases.principal-database(" + index + ").attributes.attribute.";
        List<String> argumentNames = config.getList(baseName + "name");
        List<String> argumentValues = config.getList(baseName + "value");
        for (int i = 0; i < argumentNames.size(); i++)
        {
            String argName = argumentNames.get(i);
            if (argName == null || argName.length() == 0)
            {
                throw new Exception("Argument names must have length >= 1 character");
            }
            if (Character.isLowerCase(argName.charAt(0)))
            {
                argName = Character.toUpperCase(argName.charAt(0)) + argName.substring(1);
            }
            String methodName = "set" + argName;
            Method method = principalDatabase.getClass().getMethod(methodName, String.class);
            if (method == null)
            {
                throw new Exception("No method " + methodName + " found in class " + principalDatabase.getClass() +
                                    " hence unable to configure principal database. The method must be public and " +
                                    "have a single String argument with a void return type");
            }
            method.invoke(principalDatabase, PropertyUtils.replaceProperties(argumentValues.get(i)));
        }
    }

    private void initialiseAuthenticationMechanisms(Map<String, PrincipalDatabase> databases) throws Exception
    {
        Configuration config = ApplicationRegistry.getInstance().getConfiguration();
        List<String> mechanisms = config.getList("security.sasl.mechanisms.mechanism.initialiser.class");

        // Maps from the mechanism to the properties used to initialise the server. See the method
        // Sasl.createSaslServer for details of the use of these properties. This map is populated during initialisation
        // of each provider.
        Map<String, Class<? extends SaslServerFactory>> providerMap = new TreeMap<String, Class<? extends SaslServerFactory>>();

        for (int i = 0; i < mechanisms.size(); i++)
        {
            String baseName = "security.sasl.mechanisms.mechanism(" + i + ").initialiser";
            String clazz = config.getString(baseName + ".class");
            initialiseAuthenticationMechanism(baseName, clazz, databases, config, providerMap);
        }
        if (providerMap.size() > 0)
        {
            Security.addProvider(new JCAProvider(providerMap));
        }
    }

    private void initialiseAuthenticationMechanism(String baseName, String clazz,
                                                   Map<String, PrincipalDatabase> databases,
                                                   Configuration configuration,
                                                   Map<String, Class<? extends SaslServerFactory>> providerMap)
            throws Exception
    {
        Class initialiserClazz = Class.forName(clazz);
        Object o = initialiserClazz.newInstance();
        if (!(o instanceof AuthenticationProviderInitialiser))
        {
            throw new Exception("The class " + clazz + " must be an instance of " +
                                AuthenticationProviderInitialiser.class);
        }
        AuthenticationProviderInitialiser initialiser = (AuthenticationProviderInitialiser) o;
        initialiser.initialise(baseName, configuration, databases);
        String mechanism = initialiser.getMechanismName();
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
        _log.info("Initialised " + mechanism + " SASL provider successfully");
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

    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete())
            {
                return new AuthenticationResult(challenge, AuthenticationResult.AuthenticationStatus.SUCCESS);
            }
            else
            {
                return new AuthenticationResult(challenge, AuthenticationResult.AuthenticationStatus.CONTINUE);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR);
        }
    }
}
