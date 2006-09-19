/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.management.jmx;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.management.ManagementConnection;
import org.apache.qpid.management.messaging.CMLMessageFactory;
import org.apache.qpid.schema.cml.CmlDocument;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

/**
 * Main entry point for AMQ console implementation.
 *
 */
public class AMQConsole
{
    private static final Logger _log = Logger.getLogger(AMQConsole.class);

    private ManagementConnection _connection;

    private MBeanInfoRegistry _mbeanInfoRegistry;

    private MBeanRegistrar _mbeanRegistrar;

    private MBeanServer _mbeanServer;

    public AMQConsole(String host, int port, String username, String password,
                      String context)
    {
        _connection = new ManagementConnection(host, port, username, password, context);
    }

    public void initialise() throws AMQException, JMSException, XmlException, URLSyntaxException
    {
        _connection.connect();
        createMBeanInfo();
        _mbeanServer = ManagementFactory.getPlatformMBeanServer();
        _mbeanRegistrar = new MBeanRegistrar(_mbeanServer, _connection, _mbeanInfoRegistry);
    }

    public void registerAllMBeans() throws JMSException, AMQException
    {
        _mbeanRegistrar.registerAllMBeans();
    }

    private void createMBeanInfo() throws JMSException, AMQException, XmlException
    {
        TextMessage tm = _connection.sendRequest(CMLMessageFactory.createSchemaRequest());
        if (_log.isDebugEnabled())
        {
            _log.debug("Response document: \n" + tm.getText());
        }
        CmlDocument cmlDoc = CmlDocument.Factory.parse(tm.getText());
        _mbeanInfoRegistry = new MBeanInfoRegistry(cmlDoc);
    }

    public static void main(String[] args)
    {
        AMQConsole console = new AMQConsole(args[0], Integer.parseInt(args[1]), args[2], args[3],
                                            args[4]);
        try
        {
            console.initialise();
            _log.info("Registering all MBeans...");
            console.registerAllMBeans();
            _log.info("MBean registration completed successfully");             
        }
        catch (Exception e)
        {
            _log.error("Console initialisation error: " + e, e);
        }
    }
}
