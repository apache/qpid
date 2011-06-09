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
package org.apache.qpid.server.management;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.util.Set;

import javax.management.Attribute;
import javax.management.JMException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.MBeanServerForwarder;
import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.ManagementActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;

/**
 * This class can be used by the JMXConnectorServer as an InvocationHandler for the mbean operations. This implements
 * the logic for allowing the users to invoke MBean operations and implements the restrictions for readOnly, readWrite
 * and admin users.
 */
public class MBeanInvocationHandlerImpl implements InvocationHandler, NotificationListener
{
    private static final Logger _logger = Logger.getLogger(MBeanInvocationHandlerImpl.class);

    public final static String ADMIN = "admin";
    public final static String READWRITE = "readwrite";
    public final static String READONLY = "readonly";
    private final static String DELEGATE = "JMImplementation:type=MBeanServerDelegate";
    private MBeanServer _mbs;
    private static ManagementActor  _logActor;
    
    public static MBeanServerForwarder newProxyInstance()
    {
        final InvocationHandler handler = new MBeanInvocationHandlerImpl();
        final Class<?>[] interfaces = new Class[] { MBeanServerForwarder.class };


        _logActor = new ManagementActor(ApplicationRegistry.getInstance().getRootMessageLogger());

        Object proxy = Proxy.newProxyInstance(MBeanServerForwarder.class.getClassLoader(), interfaces, handler);
        return MBeanServerForwarder.class.cast(proxy);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        final String methodName = getMethodName(method, args);

        if (methodName.equals("getMBeanServer"))
        {
            return _mbs;
        }

        if (methodName.equals("setMBeanServer"))
        {
            if (args[0] == null)
            {
                throw new IllegalArgumentException("Null MBeanServer");
            }
            if (_mbs != null)
            {
                throw new IllegalArgumentException("MBeanServer object already initialized");
            }
            _mbs = (MBeanServer) args[0];
            return null;
        }

        // Retrieve Subject from current AccessControlContext
        AccessControlContext acc = AccessController.getContext();
        Subject subject = Subject.getSubject(acc);

        try
        {
            // Allow operations performed locally on behalf of the connector server itself
            if (subject == null)
            {
                return method.invoke(_mbs, args);
            }
    
            if (args == null || DELEGATE.equals(args[0]))
            {
                return method.invoke(_mbs, args);
            }
    
            // Restrict access to "createMBean" and "unregisterMBean" to any user
            if (methodName.equals("createMBean") || methodName.equals("unregisterMBean"))
            {
                _logger.debug("User trying to create or unregister an MBean");
                throw new SecurityException("Access denied: " + methodName);
            }
    
            // Allow querying available object names
            if (methodName.equals("queryNames"))
            {
                return method.invoke(_mbs, args);
            }
    
            // Retrieve JMXPrincipal from Subject
            Set<JMXPrincipal> principals = subject.getPrincipals(JMXPrincipal.class);
            if (principals == null || principals.isEmpty())
            {
                throw new SecurityException("Access denied: no principal");
            }
			
            // Save the principal
            Principal principal = principals.iterator().next();
            SecurityManager.setThreadPrincipal(principal);
    
			// Get the component, type and impact, which may be null
            String type = getType(method, args);
            String vhost = getVirtualHost(method, args);
            int impact = getImpact(method, args);
            
            // Get the security manager for the virtual host (if set)
            SecurityManager security;
            if (vhost == null)
            {
                security = ApplicationRegistry.getInstance().getSecurityManager();
            }
            else
            {
                security = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(vhost).getSecurityManager();
            }
            
			if (isAccessMethod(methodName) || impact == MBeanOperationInfo.INFO)
			{
				// Check for read-only method invocation permission
                if (!security.authoriseMethod(Operation.ACCESS, type, methodName))
                {
                    throw new SecurityException("Permission denied: Access " + methodName);
                }
			}
			else if (isUpdateMethod(methodName))
            {
	            // Check for setting properties permission
                if (!security.authoriseMethod(Operation.UPDATE, type, methodName))
                {
                    throw new SecurityException("Permission denied: Update " + methodName);
                }
            }
			else 
            {
	            // Check for invoking/executing method action/operation permission
                if (!security.authoriseMethod(Operation.EXECUTE, type, methodName))
                {
                    throw new SecurityException("Permission denied: Execute " + methodName);
                }
            }
			
			// Actually invoke the method
			return method.invoke(_mbs, args);
        }
        catch (InvocationTargetException e)
        {
            throw e.getTargetException();
        }
    }

    private String getType(Method method, Object[] args)
    {		
		if (args[0] instanceof ObjectName)
		{
			ObjectName object = (ObjectName) args[0];
			String type = object.getKeyProperty("type");
			
			return type;
		}
		return null;
    }

    private String getVirtualHost(Method method, Object[] args)
    {       
        if (args[0] instanceof ObjectName)
        {
            ObjectName object = (ObjectName) args[0];
            String vhost = object.getKeyProperty("VirtualHost");
            
            if(vhost != null)
            {
                try
                {
                    //if the name is quoted in the ObjectName, unquote it
                    vhost = ObjectName.unquote(vhost);
                }
                catch(IllegalArgumentException e)
                {
                    //ignore, this just means the name is not quoted
                    //and can be left unchanged
                }
            }

            return vhost;
        }
        return null;
    }
    
    private String getMethodName(Method method, Object[] args)
    {
        String methodName = method.getName();

        // if arguments are set, try and work out real method name
        if (args != null && args.length >= 1 && args[0] instanceof ObjectName)
        {
            if (methodName.equals("getAttribute"))
            {
                methodName = "get" + (String) args[1];
            }
            else if (methodName.equals("setAttribute"))
            {
                methodName = "set" + ((Attribute) args[1]).getName();
            }
            else if (methodName.equals("invoke"))
            {
                methodName = (String) args[1];
            }
        }
        
        return methodName;
    }

    private int getImpact(Method method, Object[] args)
    {
        //handle invocation of other methods on mbeans
        if ((args[0] instanceof ObjectName) && (method.getName().equals("invoke")))
        {
            //get invoked method name
            String mbeanMethod = (args.length > 1) ? (String) args[1] : null;
            if (mbeanMethod == null)
            {
                return -1;
            }
            
            try
            {
                //Get the impact attribute
                MBeanInfo mbeanInfo = _mbs.getMBeanInfo((ObjectName) args[0]);
                if (mbeanInfo != null)
                {
                    MBeanOperationInfo[] opInfos = mbeanInfo.getOperations();
                    for (MBeanOperationInfo opInfo : opInfos)
                    {
                        if (opInfo.getName().equals(mbeanMethod))
                        {
                            return opInfo.getImpact();
                        }
                    }
                }
            }
            catch (JMException ex)
            {
                ex.printStackTrace();
            }
        }

        return -1;
    }

    private boolean isAccessMethod(String methodName)
    {
        //handle standard get/query/is methods from MBeanServer
        return (methodName.startsWith("query") || methodName.startsWith("get") || methodName.startsWith("is"));
    }


    private boolean isUpdateMethod(String methodName)
    {
        //handle standard set methods from MBeanServer
        return methodName.startsWith("set");
    }

    public void handleNotification(Notification notification, Object handback)
    {
        assert notification instanceof JMXConnectionNotification;

        // only RMI Connections are serviced here, Local API atta
        // rmi://169.24.29.116 guest 3
        String[] connectionData = ((JMXConnectionNotification) notification).getConnectionId().split(" ");
        String user = connectionData[1];

        if (notification.getType().equals(JMXConnectionNotification.OPENED))
        {
            _logActor.message(ManagementConsoleMessages.OPEN(user));
        }
        else if (notification.getType().equals(JMXConnectionNotification.CLOSED) ||
                 notification.getType().equals(JMXConnectionNotification.FAILED))
        {
            _logActor.message(ManagementConsoleMessages.CLOSE());
        }
    }
}

