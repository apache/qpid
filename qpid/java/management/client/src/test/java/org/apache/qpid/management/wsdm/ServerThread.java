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
package org.apache.qpid.management.wsdm;

import java.io.File;

import org.apache.qpid.management.Names;
import org.mortbay.component.LifeCycle.Listener;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.start.Monitor;

public class ServerThread extends Thread
{	
	private final Listener _lifecycleListener;
	private Server server;
	ServerThread(Listener listener)
	{
		this._lifecycleListener = listener;
	}
	
	@Override
	public void run()
	{
		try 
		{		
			Monitor.monitor();        
			server = new Server();
    		server.setStopAtShutdown(true);
            
            Connector connector=new SelectChannelConnector();
            connector.setPort(
            		Integer.parseInt(
            				System.getProperty(Names.ADAPTER_PORT_PROPERTY_NAME)));
            connector.setHost(System.getProperty(Names.ADAPTER_HOST_PROPERTY_NAME));
            
            server.setConnectors(new Connector[]{connector});
            
            WebAppContext webapp = new WebAppContext();
//            webapp.setExtractWAR(false);
            webapp.setContextPath("/qman");
            webapp.setDefaultsDescriptor("/org/apache/qpid/management/wsdm/web.xml");

            String webApplicationPath = System.getProperty("qman.war");
            File rootFolderPath = (webApplicationPath != null) ? new File(webApplicationPath) : new File(".");
            
            webapp.setWar(rootFolderPath.toURI().toURL().toExternalForm());
            webapp.addLifeCycleListener(_lifecycleListener);
            server.setHandler(webapp);
            server.start();
            server.join();
		} catch(Exception exception)
		{
			throw new RuntimeException(exception);
		}
	}
	
	public void shutdown() throws Exception
	{
		server.stop();
	}
}
