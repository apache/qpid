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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.apache.qpid.server.BrokerOptions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * Builder to build {@link BrokerOptions} from initialization parameters of servlet context.
 * <p>
 * The following init parameters are supported:
 * <ul>
 * <li>home, QPID_HOME setting
 * <li>work, QPID_WORK setting
 * <li>config, path to Qpid configuration file
 * <li>logconfig, path to Qpid log configuration file
 * <li>port, Qpid AMQP port
 * <li>sslport, SSL port for AMQP
 * <li>jmxregistryport, RMI port
 * <li>jmxconnectorport, JMX connector port
 * <li>bind, bind address
 * <li>logwatch, log watch frequency
 * </ul>
 * <p>
 * If parameters are not set then default configuration in WEB-INF/etc is used
 * to start the broker
 * <p>
 * Examples of init configuration for web.xml
 * <code>
 * &lt;context-param&gt;
 *  &lt;param-name&gt;config&lt;/param-name&gt;
 *  &lt;param-value&gt;/home/user/qpid/etc/config.xml&lt;/param-value&gt;
 * &lt;/context-param&gt;
 *
 * &lt;context-param&gt;
 *  &lt;param-name&gt;work&lt;/param-name&gt;
 *  &lt;param-value&gt;/home/user/qpid/work&lt;/param-value&gt;
 * &lt;/context-param&gt;
 *
 * &lt;context-param&gt;
 *  &lt;param-name&gt;home&lt;/param-name&gt;
 *  &lt;param-value&gt;/home/user/qpid&lt;/param-value&gt;
 * &lt;/context-param&gt;
 *
 * &lt;context-param&gt;
 *  &lt;param-name&gt;port&lt;/param-name&gt;
 *  &lt;param-value&gt;5671&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * &lt;context-param&gt;
 *  &lt;param-name&gt;jmxregistryport&lt;/param-name&gt;
 *  &lt;param-value&gt;8998&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * &lt;context-param&gt;
 *  &lt;param-name&gt;jmxconnectorport&lt;/param-name&gt;
 *  &lt;param-value&gt;9098&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </code>
 */
public class InitParametersOptionsBuilder implements BrokerOptionsBuilder
{
    private static final String INIT_PARAM_HOME_FOLDER = "home";

    private static final String INIT_PARAM_WORK_FOLDER = "work";

    private static final String INIT_PARAM_CONFIG_FILE = "config";

    private static final String INIT_PARAM_LOG_CONFIG_FILE = "logconfig";

    private static final String INIT_PARAM_PORT = "port";

    private static final String INIT_PARAM_SSL_PORT = "sslport";

    private static final String INIT_PARAM_JMX_PORT_REGISTRY_SERVER = "jmxregistryport";

    private static final String INIT_PARAM_JMX_PORT_CONNECTOR_SERVER = "jmxconnectorport";

    private static final String INIT_PARAM_BIND = "bind";

    private static final String INIT_PARAM_LOG_WATCH = "logwatch";

    @Override
    public BrokerOptions buildBrokerOptions(ServletContext context)
    {
        BrokerOptions options = new BrokerOptions();

        String homeFolder = context.getInitParameter(INIT_PARAM_HOME_FOLDER);
        if (homeFolder == null)
        {
            homeFolder = context.getRealPath("/WEB-INF");
        }
        options.setQpidHome(homeFolder);

        String workFolder = context.getInitParameter(INIT_PARAM_WORK_FOLDER);
        if (workFolder == null)
        {
            workFolder = context.getRealPath("/WEB-INF/work");
        }
        options.setQpidWork(workFolder);

        String configFile = context.getInitParameter(INIT_PARAM_CONFIG_FILE);
        if (configFile == null)
        {
            configFile = context.getRealPath("/WEB-INF/" + BrokerOptions.DEFAULT_CONFIG_FILE);
        }
        options.setConfigFile(configFile);

        String logConfig = context.getInitParameter(INIT_PARAM_LOG_CONFIG_FILE);
        if (logConfig == null)
        {
            logConfig = createLogConfigurationFromTemplate(context);
        }
        options.setLogConfigFile(logConfig);

        String port = context.getInitParameter(INIT_PARAM_PORT);
        if (port != null)
        {
            int p = 0;
            try
            {
                p = Integer.parseInt(port);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot parse broker port. Please specify correct value for a port!");
            }
            options.addPort(p);
        }
        String sslport = context.getInitParameter(INIT_PARAM_SSL_PORT);
        if (sslport != null)
        {
            int p = 0;
            try
            {
                p = Integer.parseInt(sslport);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot parse broker ssl port. Please specify correct value for ssl port!");
            }
            options.addSSLPort(p);
        }
        String jmxRegistryPort = context.getInitParameter(INIT_PARAM_JMX_PORT_REGISTRY_SERVER);
        if (jmxRegistryPort != null)
        {
            int p = 0;
            try
            {
                p = Integer.parseInt(jmxRegistryPort);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot parse broker RMI port. Please specify correct value for RMI port!");
            }
            options.setJmxPortRegistryServer(p);
        }
        String jmxConnectorPort = context.getInitParameter(INIT_PARAM_JMX_PORT_CONNECTOR_SERVER);
        if (jmxConnectorPort != null)
        {
            int p = 0;
            try
            {
                p = Integer.parseInt(jmxConnectorPort);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot parse broker JMX connector port."
                        + " Please specify correct value for JMX connector port!");
            }
            options.setJmxPortConnectorServer(p);
        }
        String logwatch = context.getInitParameter(INIT_PARAM_LOG_WATCH);
        if (logwatch != null)
        {
            int logWatchFrequency = 0;
            try
            {
                logWatchFrequency = Integer.parseInt(logwatch);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot parse broker log watch frequency."
                        + " Please specify correct value for log watch frequency!");
            }
            options.setLogWatchFrequency(logWatchFrequency);
        }
        String bind = context.getInitParameter(INIT_PARAM_BIND);
        if (bind != null)
        {
            options.setBind(bind);
        }
        return options;
    }


    /**
     * Opens /WEB-INF/etc/log4j.xml, changes the location of the log file to
     * "/WEB-INF/work/log/qpid.log" and stores log configuration in
     * "/WEB-INF/work/log4j.xml"
     */
    private String createLogConfigurationFromTemplate(ServletContext context)
    {
        String webInfPath = context.getRealPath("/WEB-INF");
        String template = webInfPath + "/" + BrokerOptions.DEFAULT_LOG_CONFIG_FILE;
        String targetLogConfig = webInfPath + "/work/log4j.xml";
        String logFile = webInfPath + "/work/log/${logprefix}qpid${logsuffix}.log";
        transformLog(template, targetLogConfig, logFile);
        return targetLogConfig;
    }

    private void transformLog(String template, String targetLogConfig, String logFile)
            throws TransformerFactoryConfigurationError
    {
        Document document = null;
        try
        {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setAttribute("http://xml.org/sax/features/namespaces", true);
            documentBuilderFactory.setAttribute("http://xml.org/sax/features/validation", false);
            documentBuilderFactory.setAttribute("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            documentBuilderFactory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setIgnoringElementContentWhitespace(false);
            documentBuilderFactory.setIgnoringComments(false);
            documentBuilderFactory.setValidating(false);

            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            document = builder.parse(new File(template));
            NodeList parameters = document.getDocumentElement().getElementsByTagName("param");
            for (int i = 0, l = parameters.getLength(); i < l; i++)
            {
                Node node = parameters.item(i);
                if (node instanceof Element)
                {
                    Element element = (Element) node;
                    String nameAttribute = element.getAttribute("name");
                    if (nameAttribute != null && nameAttribute.equalsIgnoreCase("file"))
                    {

                        element.setAttribute("value", logFile);
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot open log4j configuration at " + template, e);
        }
        FileWriter fileWriter = null;
        try
        {
            fileWriter = new FileWriter(targetLogConfig);
            DOMImplementationLS ls = (DOMImplementationLS) DOMImplementationRegistry.newInstance()
                    .getDOMImplementation("LS");
            LSOutput lsout = ls.createLSOutput();
            lsout.setCharacterStream(fileWriter);
            lsout.setEncoding(document.getXmlEncoding());
            LSSerializer serializer = ls.createLSSerializer();
            serializer.write(document, lsout);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot save log4j configuration at " + targetLogConfig, e);
        }
        finally
        {
            if (fileWriter != null)
            {
                try
                {
                    fileWriter.close();
                }
                catch (IOException e)
                {
                    // ignore this
                }
            }
        }

    }
}
