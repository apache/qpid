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

package org.apache.qpid.info;

import org.apache.qpid.info.util.HttpPoster;
import org.apache.qpid.info.util.IniFileReader;
import org.apache.qpid.info.util.SoapClient;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** The Activator class for the OSGI info service */
public class Activator implements BundleActivator
{

    private final List<String> _soapPropList = Arrays.asList("soap.hostname",
                                                             "soap.port", "soap.path", "soap.action", "soap.envelope");

    private final List<String> _httpPropList = Arrays.asList("http.url",
                                                             "http.envelope");

    InfoServiceImpl _service = null;

    BundleContext _ctx = null;

    /**
     * Start bundle method
     *
     * @param ctx the bundle context
     */
    public void start(BundleContext ctx) throws Exception
    {
        if (null != ctx)
        {
            _ctx = ctx;
            _service = new InfoServiceImpl();
            ctx.registerService(InfoService.class.getName(), _service, null);
            sendInfo("STARTUP");
        }
    }

    /**
     * Stop the bundle method
     *
     * @param ctx the bundle context
     */
    public void stop(BundleContext ctx) throws Exception
    {
        sendInfo("SHUTDOWN");
    }

    /**
     * Sends the information message
     *
     * @param action label that identifies if we are starting up or shutting down
     */
    private void sendInfo(String action)
    {
        if ((null == _ctx) && (null == _service))
        {
            // invalid state
            return;
        }

        IniFileReader ifr = new IniFileReader();
        try
        {
            String QPID_HOME = System.getProperty("QPID_HOME");
            String cfgFilePath = QPID_HOME + File.separator + "etc"
                                 + File.separator + "qpidinfo.ini";
            ifr.load(cfgFilePath);
        }
        catch (Throwable ex)
        {
            // drop everything to be silent
            return;
        }

        // Only send Messages if we have some sections.
        if (ifr.getSections().size() != 0)
        {
            Info<? extends Map<String, ?>> info = _service.invoke(action);
            String protocol = ifr.getSections().get("").getProperty("protocol");
            sendMessages(protocol, ifr, info);
        }
    }

    /**
     * Sends all the messages configured in the properties file
     *
     * @param protocol indicates what protocol to be used: http and soap implemented
     *                 for now
     * @param ifr      an instance of IniFileReader class
     * @param info     an instance of an Info object, encapsulating the information
     *                 we want to send
     */
    private void sendMessages(String protocol, IniFileReader ifr,
                              Info<? extends Map<String, ?>> info)
    {
        if (null != protocol)
        {
            // Set the global properties first (as they are the defaults)
            Properties defaultProps = ifr.getSections().get("");
            if (protocol.toLowerCase().startsWith("http"))
            {
                for (String section : ifr.getSections().keySet())
                {
                    // Skip the defaults
                    if (section.equals(""))
                    {
                        continue;
                    }
                    Properties props = new Properties();
                    props.putAll(defaultProps);
                    props.putAll(ifr.getSections().get(section));
                    if (isValid(protocol, props))
                    {
                        new HttpPoster(props, info.toXML()).run();
                    }
                }

            }
            else if (protocol.toLowerCase().startsWith("soap"))
            {
                for (String section : ifr.getSections().keySet())
                {
                    Properties props = new Properties();
                    props.putAll(defaultProps);
                    props.putAll(ifr.getSections().get(section));
                    if (isValid(protocol, props))
                    {
                        new SoapClient(info.toMap(), props).sendSOAPMessage();
                    }
                }
            }
        }
    }

    /**
     * Checks if the properties for a specified protocol are valid
     *
     * @param protocol String representing the protocol
     * @param props    The properties associate with the specified protocol
     * @return boolean
     */
    private boolean isValid(String protocol, Properties props)
    {
        if (null == protocol)
        {
            return false;
        }
        String value = "";
        if (protocol.toLowerCase().startsWith("http"))
        {
            for (String prop : _httpPropList)
            {
                if (null == props.get(prop))
                {
                    return false;
                }
            }
            return true;
        }

        if (protocol.toLowerCase().startsWith("soap"))
        {
            for (String prop : _soapPropList)
            {
                value = props.getProperty(prop);
                if (null == value)
                {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
} // end class

