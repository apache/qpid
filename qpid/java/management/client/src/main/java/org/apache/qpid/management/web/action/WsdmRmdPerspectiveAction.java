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
package org.apache.qpid.management.web.action;

import java.io.IOException;
import java.net.URI;

import javax.management.ObjectName;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.apache.muse.core.proxy.ProxyHandler;
import org.apache.muse.core.proxy.ReflectionProxyHandler;
import org.apache.muse.util.xml.XmlUtils;
import org.apache.muse.ws.addressing.EndpointReference;
import org.apache.muse.ws.resource.remote.WsResourceClient;
import org.apache.qpid.management.Names;
import org.w3c.dom.Element;

public class WsdmRmdPerspectiveAction extends HttpServlet
{
	private static final long serialVersionUID = -2411413147821629363L;
	private static final Object [] DIALECT = new Object[]{"http://docs.oasis-open.org/wsrf/rmd-1"};
	
	private ProxyHandler proxyHandler;
	
	private URI resourceUri;
		
	@Override
	public void init() throws ServletException
	{
		proxyHandler  = new ReflectionProxyHandler();
		proxyHandler.setAction("http://schemas.xmlsoap.org/ws/2004/09/mex/GetMetadata");
		proxyHandler.setRequestName(new QName("http://schemas.xmlsoap.org/ws/2004/09/mex", "GetMetadata", Names.PREFIX));
		proxyHandler.setRequestParameterNames(new QName[]{new QName("http://schemas.xmlsoap.org/ws/2004/09/mex", "Dialect", Names.PREFIX)});
		proxyHandler.setResponseName(new QName("http://schemas.xmlsoap.org/ws/2004/09/mex", "Metadata", Names.PREFIX));
		proxyHandler.setReturnType(Element[].class);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		try 
		{
//			String resourceId = request.getParameter("resourceId");
//			ObjectName objectName = new ObjectName(resourceId);
//			
//			String wsresourceid = objectName.getKeyProperty(Names.OBJECT_ID);
//			EndpointReference resourceEndpointReference = new EndpointReference(getURI(request));
//			resourceEndpointReference.addParameter(
//					Names.RESOURCE_ID_QNAME, 
//					wsresourceid);
//			
//			WsResourceClient resourceClient = new WsResourceClient(resourceEndpointReference);
//			Element rmd = ((Element[])resourceClient.invoke(proxyHandler,DIALECT))[0];
//						        		
//        	String output = XmlUtils.toString(rmd);
//        	
//			String [] keyProperties = objectName.getKeyPropertyListString().split(",");
//			
//			request.setAttribute("resourceId", objectName);
//			request.setAttribute("nameAttributes",keyProperties);
//			request.setAttribute("rmd",output);
			RequestDispatcher dispatcher = request.getRequestDispatcher("/tbd.jsp");
			dispatcher.forward(request,response); 
		} catch(Exception exception)
		{
			request.setAttribute("errorMessage","Unable to detect the exact cause Please look at the reported stack trace below.");
			request.setAttribute("exception",exception);
			RequestDispatcher dispatcher = request.getRequestDispatcher("/error_page.jsp");
			dispatcher.forward(request,response);			
		}
	}

	private URI getURI(HttpServletRequest request)
	{
		if (resourceUri == null)
		{
			StringBuilder builder = new StringBuilder();
			builder
				.append("http://")
				.append(request.getServerName())
				.append(":")
				.append(request.getServerPort())
				.append("/qman/services/QManWsResource");
			resourceUri = URI.create(builder.toString());
		}
		return resourceUri;
	}
}