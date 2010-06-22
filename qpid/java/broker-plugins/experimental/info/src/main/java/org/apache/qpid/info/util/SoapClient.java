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
/**
 * 
 * @author sorin
 * 
 *  An simple SOAP client for qpid info service
 */
package org.apache.qpid.info.util;

import java.io.BufferedWriter;

import java.io.OutputStreamWriter;

import java.net.InetAddress;

import java.net.Socket;
import java.util.HashMap;
import java.util.Properties;

public class SoapClient
{

    private final StringBuffer xmlData;

    private final Properties _destprops;

    private final String hostname;

    private final String port;

    private final String urlpath;

    private final String soapenvelope;

    private final String soapaction;

    private final StringBuffer soapMessage = new StringBuffer();

    public SoapClient(HashMap<String, String> map, Properties destprops)
    {
        if (null == destprops)
        {
            _destprops = new Properties();
        } else
        {
            _destprops = destprops;
        }
        hostname = _destprops.getProperty("soap.hostname", "null");
        port = _destprops.getProperty("soap.port", "null");
        urlpath = _destprops.getProperty("soap.path", "null");
        soapenvelope = _destprops.getProperty("soap.envelope", "null");
        soapaction = _destprops.getProperty("soap.action", "null");
        xmlData = new StringBuffer(soapenvelope);
        replaceVariables(map);
    }

    public StringBuffer getXMLData()
    {
        return xmlData;
    }

    public StringBuffer getSoapMessage()
    {
        return soapMessage;
    }

    public String getSoapEnvelope()
    {
        return soapenvelope;
    }

    public void setXMLData(StringBuilder sb)
    {
        xmlData.delete(0, xmlData.length() - 1);
        xmlData.append(sb);
    }

    public void replaceVariables(HashMap<String, String> vars)
    {
        if (vars == null)
            return;
        int ix = 0;
        for (String var : vars.keySet())
        {
            while ((ix = xmlData.indexOf("@" + var.toUpperCase())) >= 0)
            {
                xmlData.replace(ix, ix + 1 + var.length(), vars.get(var));
            }
        }
    }

    public void replaceVariables(Properties varProps)
    {
        if (varProps == null)
            return;
        int ix = 0;
        for (Object var : varProps.keySet())
        {
            while ((ix = xmlData.indexOf("@" + var)) >= 0)
            {
                xmlData.replace(ix, ix + 1 + var.toString().length(), varProps
                        .get(var).toString());
            }
        }
    }

    public String sendSOAPMessage()
    {

        try
        {
            InetAddress addr = InetAddress.getByName(hostname);
            Socket sock = new Socket(addr, Integer.parseInt(port));
            StringBuffer sb = new StringBuffer();
            sb.append("POST " + urlpath + " HTTP/1.1\r\n");
            sb.append("Host: " + hostname + ":" + port + "\r\n");
            sb.append("Content-Length: " + xmlData.length() + "\r\n");
            sb.append("Content-Type: text/xml; charset=\"utf-8\"\r\n");
            sb.append("SOAPAction: \"urn:" + soapaction + "\"\r\n");
            sb.append("User-Agent: Axis2\r\n");
            sb.append("\r\n");
            // Send header
            BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(sock
                    .getOutputStream(), "UTF-8"));
            synchronized (soapMessage)
            {
                soapMessage.setLength(0);
                soapMessage.append(sb);
                soapMessage.append(xmlData);
            }
            String msg = soapMessage.toString();
            // Send data
            wr.write(msg);
            wr.flush();
            wr.close();
            return msg;
        } catch (Exception ex)
        {
            // Drop any exception
            return null;
        }
    }
}
