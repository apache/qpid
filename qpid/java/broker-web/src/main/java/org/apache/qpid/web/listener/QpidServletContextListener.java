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
package org.apache.qpid.web.listener;

import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.configuration.interpol.ConfigurationInterpolator;
import org.apache.commons.lang.text.StrLookup;
import org.apache.qpid.server.Broker;
import org.apache.qpid.server.BrokerOptions;

/**
 * An implementation of {@link ServletContextListener} allowing to start Qpid
 * broker in web container.
 * <p>
 * The listener instantiate {@link BrokerOptionsBuilder} specified in
 * initialization parameter "broker-options-builder" and uses this builder to
 * create {@link BrokerOptions} instance to start broker.
 */
public class QpidServletContextListener implements ServletContextListener
{
    private static final String INIT_PARAM_BROKER_OPTIONS_BUILDER = "broker-options-builder";

    private Broker _broker;

    @Override
    public void contextDestroyed(ServletContextEvent event)
    {
        if (_broker != null)
        {
            _broker.shutdown();
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent event)
    {
        ServletContext context = event.getServletContext();
        BrokerOptions options = createBrokerOptions(context);
        setConfigurationVariables(options, context);
        startBroker(options);
    }

    /**
     * Starts broker with given {@link BrokerOptions}.
     */
    private void startBroker(BrokerOptions options)
    {
        _broker = new Broker();
        try
        {
            _broker.startup(options);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Broker cannot be started", e);
        }
    }

    /**
     * Sets resolver for QPID_HOME and QPID_WORK configuration variables and all
     * context initialization parameters, so, they can be used in default
     * configuration files.
     */
    private void setConfigurationVariables(BrokerOptions options, ServletContext context)
    {
        Properties properties = new Properties();
        @SuppressWarnings("unchecked")
        Enumeration<String> parameterNames = context.getInitParameterNames();
        while (parameterNames.hasMoreElements())
        {
            String name = parameterNames.nextElement();
            properties.put(name, context.getInitParameter(name));
        }
        properties.put(BrokerOptions.QPID_HOME, options.getQpidHome());
        properties.put(BrokerOptions.QPID_WORK, options.getQpidWork());
        PropertiesLookup lookup = new PropertiesLookup(properties);
        ConfigurationInterpolator.registerGlobalLookup("web", lookup);
    }

    /**
     * Creates {@link BrokerOptions} using initialization parameters of servlet
     * context.
     */
    private BrokerOptions createBrokerOptions(final ServletContext context)
    {
        BrokerOptionsBuilder builder = null;
        String builderClassName = context.getInitParameter(INIT_PARAM_BROKER_OPTIONS_BUILDER);
        if (builderClassName != null)
        {
            Class<?> builderClass = null;
            try
            {
                builderClass = Class.forName(builderClassName);
            }
            catch (ClassNotFoundException e)
            {
                throw new RuntimeException("Invalid options builder class " + builderClassName + " is specified", e);
            }
            try
            {
                builder = (BrokerOptionsBuilder) builderClass.newInstance();
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot instantiate options builder", e);
            }
        }
        else
        {
            builder = new InitParametersOptionsBuilder();
        }
        return builder.buildBrokerOptions(context);
    }

    /**
     * A variable resolver to resolve variables set in given {@link Properties}.
     * <p>
     * It is used to resolve "QPID_HOME" and "QPID_WORK" and servlet context
     * init parameters.
     */
    private static class PropertiesLookup extends StrLookup
    {
        private Properties _properties;

        private PropertiesLookup(Properties properties)
        {
            _properties = properties;
        }

        @Override
        public String lookup(String varName)
        {
            return _properties.getProperty(varName);
        }

    }

}
