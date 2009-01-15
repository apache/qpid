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
package org.apache.qpid.management.servlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.qpid.management.Names;
import org.apache.qpid.management.domain.services.QMan;
import org.apache.qpid.management.domain.services.StartupFailureException;

/**
 * QMan lifecycle management.
 * 
 * @author Andrea Gazzarini
 */
public class QManLifeCycleManager implements ServletContextListener 
{
	/**
	 * Stops QMan.
	 * 
	 * @param event the application context event.
	 */
	public void contextDestroyed(ServletContextEvent event) 
	{
		ServletContext context = event.getServletContext();
		
		QMan qman = (QMan) context.getAttribute(Names.APPLICATION_NAME);		
		qman.stop();
		
		context.setAttribute(Names.APPLICATION_NAME, qman);
	}

	/**
	 * Starts QMan.
	 * 
	 * @param event the application context event.
	 */
	public void contextInitialized(ServletContextEvent event) 
	{
		try 
		{
			QMan qman = new QMan();
			qman.start();

			event.getServletContext().setAttribute(Names.APPLICATION_NAME, qman);
		} catch (StartupFailureException exception) 
		{
			// TODO : LOG ERROR.
			exception.printStackTrace();
		}
	}
}