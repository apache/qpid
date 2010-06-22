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

    private final StringBuffer _xmlData;

    private final Properties _destprops;

    private final String _hostname;

    private final int _port;

    private final String _urlpath;

    private final String _soapenvelope;
    
    private final String _soapaction;
    
    private final StringBuffer _soapMessage = new StringBuffer();
    

    public SoapClient(HashMap<String, String> map, Properties destprops)
    {
        _destprops = destprops;
        _hostname = (String) _destprops.get("soap.hostname");
        _port = Integer.parseInt((String) _destprops.get("soap.port"));
        _urlpath = (String) destprops.get("soap.path");
        _soapenvelope = (String) destprops.get("soap.envelope");
        _soapaction = (String) destprops.get("soap.action");
        _xmlData = new StringBuffer(_soapenvelope);
        replaceVariables(map);
    }

    public StringBuffer getXMLData()
    {
        return _xmlData;
    }

    public StringBuffer getSoapMessage() {
        return _soapMessage;
    }
   
    public String getSoapEnvelope() {
        return _soapenvelope;
    }

    /**
     * Clears and sets new XML data
     * @param sb the new data to set
     */
    public void setXMLData(StringBuffer sb)
    {
        _xmlData.delete(0, _xmlData.length());
        _xmlData.append(sb);
    }
    

    public void replaceVariables(HashMap<String, String> vars)
    {
        int ix = 0;
        for (String var : vars.keySet())
        {
            while ((ix = _xmlData.indexOf("@" + var.toUpperCase())) >= 0)
            {
                _xmlData.replace(ix, ix + 1 + var.length(), vars.get(var));
            }
        }
    }

    public void replaceVariables(Properties varProps)
    {
        if (varProps == null)
        {
            return;
        }
        int ix = 0;
        for (Object var : varProps.keySet())
        {
            while ((ix = _xmlData.indexOf("@" + var)) >= 0)
            {
                _xmlData.replace(ix, ix + 1 + var.toString().length(), varProps
                        .get(var).toString());
            }
        }
    }
    

    public void sendSOAPMessage()
    {

        try
        {
            InetAddress addr = InetAddress.getByName(_hostname);
            Socket sock = new Socket(addr, _port);
            StringBuffer sb = new StringBuffer();
            sb.append("POST " + _urlpath + " HTTP/1.1\r\n");
            sb.append("Host: " + _hostname + ":" + _port + "\r\n");
            sb.append("Content-Length: " + _xmlData.length() + "\r\n");
            sb.append("Content-Type: text/xml; charset=\"utf-8\"\r\n");
            sb.append("SOAPAction: \"urn:"+ _soapaction +"\"\r\n");
            sb.append("User-Agent: Axis2\r\n");
            sb.append("\r\n");
            // Send header
            BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(sock
                    .getOutputStream(), "UTF-8"));
            synchronized(_soapMessage) {
                _soapMessage.setLength(0);
                _soapMessage.append(sb);
                _soapMessage.append(_xmlData);
            }
            // Send data
            wr.write(_soapMessage.toString());
            wr.flush();
            wr.close();
            
        } catch (Exception ex)
        {
            // Drop any exception
        }
    }
}
