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
 *  An simple Http post class for qpid info service
 */

package org.apache.qpid.info.util;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

public class HttpPoster implements Runnable
{
    private final String url;

    private final Hashtable<String, String> header;

    private final List<String> response = new ArrayList<String>();

    private final StringBuffer _buf;

    /**
     * Constructor
     * @param props  Properties containing the URL 
     * @param buf    Buffer containing the message to be posted
     */
    public HttpPoster(Properties props, StringBuffer buf)
    {
        _buf = buf;
        if (null != props)
        {
            url = props.getProperty("http.url");
            header = new Hashtable<String, String>();
            try
            {
              String hostname = InetAddress.getLocalHost().getHostName();
              header.put("hostname", hostname);
            } catch (UnknownHostException e)
            {
               // Silently ignoring the error ;)
            }
        } else
        {
            url = null;
            header = null;
        }
    }
    /**
     * Posts the message from the _buf StringBuffer to the http server
     */
    public void run()
    {
        if (null == url)
            return;
        String line;
        URL urlDest;
        URLConnection urlConn;
        try
        {
            urlDest = new URL(url);
            urlConn = urlDest.openConnection();
            urlConn.setDoOutput(true);
            urlConn.setUseCaches(false);
            for (Iterator<String> it = header.keySet().iterator(); it.hasNext();)
            {
                String prop = (String) it.next();
                urlConn.setRequestProperty(prop, header.get(prop));
            }
            OutputStreamWriter wr = new OutputStreamWriter(urlConn
                    .getOutputStream());
            wr.write(_buf.toString());
            wr.flush();
            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    urlConn.getInputStream()));
            while ((line = rd.readLine()) != null)
            {
                response.add(line);
            }
        } catch (Exception ex)
        {
            return;
        }
    }
    
    /**
     * Retrieves the response from the http server
     * @return List<String> response received from the http server
     */
    public List<String> getResponse()
    {
        return response;
    }

}
