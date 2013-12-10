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
package org.apache.qpid.client.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * CallbackHandlerRegistry is a registry for call back handlers for user authentication and interaction during user
 * authentication. It is capable of reading its configuration from a properties file containing call back handler
 * implementing class names for different SASL mechanism names. Instantiating this registry also has the effect of
 * configuring and registering the SASL client factory implementations using {@link DynamicSaslRegistrar}.
 *
 * <p/>The callback configuration should be specified in a properties file, refered to by the System property
 * "amp.callbackhandler.properties". The format of the properties file is:
 *
 * <p/><pre>
 * CallbackHanlder.n.mechanism=fully.qualified.class.name where n is an ordinal
 * </pre>
 *
 * <p/>Where mechanism is an IANA-registered mechanism name and the fully qualified class name refers to a
 * class that implements org.apache.qpid.client.security.AMQCallbackHanlder and provides a call back handler for the
 * specified mechanism.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Parse callback properties.
 * <tr><td> Provide mapping from SASL mechanisms to callback implementations.
 * </table>
 */
public class CallbackHandlerRegistry
{
    private static final Logger _logger = LoggerFactory.getLogger(CallbackHandlerRegistry.class);

    /** The name of the system property that holds the name of the callback handler properties file. */
    private static final String FILE_PROPERTY = "amq.callbackhandler.properties";

    /** The default name of the callback handler properties resource. */
    public static final String DEFAULT_RESOURCE_NAME = "org/apache/qpid/client/security/CallbackHandlerRegistry.properties";

    /** A static reference to the singleton instance of this registry. */
    private static final CallbackHandlerRegistry _instance;

    /** Holds a map from SASL mechanism names to call back handlers. */
    private Map<String, Class<AMQCallbackHandler>> _mechanismToHandlerClassMap = new HashMap<String, Class<AMQCallbackHandler>>();

    /** Ordered collection of mechanisms for which callback handlers exist. */
    private Collection<String> _mechanisms;

    private static final Collection<String> MECHS_THAT_NEED_USERPASS = Arrays.asList(new String [] {"PLAIN", "AMQPLAIN", "CRAM-MD5","CRAM-MD5-HASHED"});

    static
    {
        // Register any configured SASL client factories.
        DynamicSaslRegistrar.registerSaslProviders();

        String filename = System.getProperty(FILE_PROPERTY);
        InputStream is =
            FileUtils.openFileOrDefaultResource(filename, DEFAULT_RESOURCE_NAME,
                CallbackHandlerRegistry.class.getClassLoader());

        final Properties props = new Properties();

        try
        {

            props.load(is);
        }
        catch (IOException e)
        {
            _logger.error("Error reading properties: " + e, e);
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();

                }
                catch (IOException e)
                {
                    _logger.error("Unable to close properties stream: " + e, e);
                }
            }
        }

        _instance = new CallbackHandlerRegistry(props);
        _logger.info("Callback handlers available for SASL mechanisms: " + _instance._mechanisms);

    }

    /**
     * Gets the singleton instance of this registry.
     *
     * @return The singleton instance of this registry.
     */
    public static CallbackHandlerRegistry getInstance()
    {
        return _instance;
    }

    public AMQCallbackHandler createCallbackHandler(final String mechanism)
    {
        final Class<AMQCallbackHandler> mechanismClass = _mechanismToHandlerClassMap.get(mechanism);

        if (mechanismClass == null)
        {
            throw new IllegalArgumentException("Mechanism " + mechanism + " not known");
        }

        try
        {
            return mechanismClass.newInstance();
        }
        catch (InstantiationException e)
        {
            throw new IllegalArgumentException("Unable to create an instance of mechanism " + mechanism, e);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalArgumentException("Unable to create an instance of mechanism " + mechanism, e);
        }
    }

    /**
     * Gets collections of supported SASL mechanism names, ordered by preference
     *
     * @return collection of SASL mechanism names.
     */
    public Collection<String> getMechanisms()
    {
        return Collections.unmodifiableCollection(_mechanisms);
    }

    /**
     * Creates the call back handler registry from its configuration resource or file.
     *
     * This also has the side effect of configuring and registering the SASL client factory
     * implementations using {@link DynamicSaslRegistrar}.
     *
     * This constructor is default protection to allow for effective unit testing.  Clients must use
     * {@link #getInstance()} to obtain the singleton instance.
     */
    CallbackHandlerRegistry(final Properties props)
    {
        parseProperties(props);
    }

    /**
     * Scans the specified properties as a mapping from IANA registered SASL mechanism to call back handler
     * implementations, that provide the necessary call back handling for obtaining user log in credentials
     * during authentication for the specified mechanism, and builds a map from mechanism names to handler
     * classes.
     *
     * @param props
     */
    private void parseProperties(Properties props)
    {

        final Map<Integer, String> mechanisms = new TreeMap<Integer, String>();

        Enumeration e = props.propertyNames();
        while (e.hasMoreElements())
        {
            final String propertyName = (String) e.nextElement();
            final String[] parts = propertyName.split("\\.", 2);

            checkPropertyNameFormat(propertyName, parts);

            final String mechanism = parts[0];
            final int ordinal = getPropertyOrdinal(propertyName, parts);
            final String className = props.getProperty(propertyName);
            Class clazz = null;
            try
            {
                clazz = Class.forName(className);
                if (!AMQCallbackHandler.class.isAssignableFrom(clazz))
                {
                    _logger.warn("SASL provider " + clazz + " does not implement " + AMQCallbackHandler.class
                        + ". Skipping");
                    continue;
                }
                _mechanismToHandlerClassMap.put(mechanism, clazz);

                mechanisms.put(ordinal, mechanism);
            }
            catch (ClassNotFoundException ex)
            {
                _logger.warn("Unable to load class " + className + ". Skipping that SASL provider");

                continue;
            }
        }

        _mechanisms = mechanisms.values();  // order guaranteed by keys of treemap (i.e. our ordinals)


    }

    private void checkPropertyNameFormat(final String propertyName, final String[] parts)
    {
        if (parts.length != 2)
        {
            throw new IllegalArgumentException("Unable to parse property " + propertyName + " when configuring SASL providers");
        }
    }

    private int getPropertyOrdinal(final String propertyName, final String[] parts)
    {
        try
        {
            return Integer.parseInt(parts[1]);
        }
        catch(NumberFormatException nfe)
        {
            throw new IllegalArgumentException("Unable to parse property " + propertyName + " when configuring SASL providers", nfe);
        }
    }

    /**
     * Selects a SASL mechanism that is mutually available to both parties.  If more than one
     * mechanism is mutually available the one appearing first (by ordinal) will be returned.
     *
     * @param peerMechanismList space separated list of mechanisms
     * @return selected mechanism, or null if none available
     */
    public String selectMechanism(final String peerMechanismList)
    {
        final Set<String> peerList = mechListToSet(peerMechanismList);

        return selectMechInternal(peerList, Collections.<String>emptySet());
    }

    /**
     * Selects a SASL mechanism that is mutually available to both parties.
     *
     * @param peerMechanismList space separated list of mechanisms
     * @param restrictionList space separated list of mechanisms
     * @return selected mechanism, or null if none available
     */
    public String selectMechanism(final String peerMechanismList, final String restrictionList)
    {
        final Set<String> peerList = mechListToSet(peerMechanismList);
        final Set<String> restrictionSet = mechListToSet(restrictionList);

        return selectMechInternal(peerList, restrictionSet);
    }

    private String selectMechInternal(final Set<String> peerSet, final Set<String> restrictionSet)
    {
        for (final String mech : _mechanisms)
        {
            if (peerSet.contains(mech))
            {
                if (restrictionSet.isEmpty() || restrictionSet.contains(mech))
                {
                    return mech;
                }
            }
        }

        return null;
    }

    private Set<String> mechListToSet(final String mechanismList)
    {
        if (mechanismList == null)
        {
            return Collections.emptySet();
        }

        final StringTokenizer tokenizer = new StringTokenizer(mechanismList, " ");
        final Set<String> mechanismSet = new HashSet<String>(tokenizer.countTokens());
        while (tokenizer.hasMoreTokens())
        {
            mechanismSet.add(tokenizer.nextToken());
        }
        return Collections.unmodifiableSet(mechanismSet);
    }

    public boolean isUserPassRequired(String selectedMech)
    {
        return MECHS_THAT_NEED_USERPASS.contains(selectedMech);
    }
}
