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
package org.apache.qpid.server.jmx;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.ManagementActor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;

import javax.management.Attribute;
import javax.management.JMException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.RuntimeErrorException;
import javax.management.remote.MBeanServerForwarder;
import javax.security.auth.Subject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.Arrays;

/**
 * This class can be used by the JMXConnectorServer as an InvocationHandler for the mbean operations. It delegates
 * JMX access decisions to the SecurityPlugin.
 */
public class MBeanInvocationHandlerImpl implements InvocationHandler
{
    private static final Logger _logger = Logger.getLogger(MBeanInvocationHandlerImpl.class);

    private final static String DELEGATE = "JMImplementation:type=MBeanServerDelegate";
    private MBeanServer _mbs;
    private final ManagementActor _logActor;

    private final boolean _managementRightsInferAllAccess;
    private final Broker _broker;

    MBeanInvocationHandlerImpl(Broker broker)
    {
        _managementRightsInferAllAccess = Boolean.valueOf(System.getProperty(BrokerProperties.PROPERTY_MANAGEMENT_RIGHTS_INFER_ALL_ACCESS, "true"));
        _broker = broker;
        _logActor = new ManagementActor(broker.getRootMessageLogger());
    }

    public static MBeanServerForwarder newProxyInstance(Broker broker)
    {
        final InvocationHandler handler = new MBeanInvocationHandlerImpl(broker);
        final Class<?>[] interfaces = new Class[] { MBeanServerForwarder.class };

        Object proxy = Proxy.newProxyInstance(MBeanServerForwarder.class.getClassLoader(), interfaces, handler);
        return MBeanServerForwarder.class.cast(proxy);
    }

    private boolean invokeDirectly(String methodName, Object[] args, Subject subject)
    {
        // Allow operations performed locally on behalf of the connector server itself
        if (subject == null)
        {
            return true;
        }

        if (args == null || DELEGATE.equals(args[0]))
        {
            return true;
        }

        // Allow querying available object names and mbeans
        if (methodName.equals("queryNames") || methodName.equals("queryMBeans"))
        {
            return true;
        }

        if (args[0] instanceof ObjectName)
        {
            ObjectName mbean = (ObjectName) args[0];

            if(!ManagedObject.DOMAIN.equalsIgnoreCase(mbean.getDomain()))
            {
                return true;
            }
        }

        return false;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();

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

        // Restrict access to "createMBean" and "unregisterMBean" to any user
        if (methodName.equals("createMBean") || methodName.equals("unregisterMBean"))
        {
            _logger.debug("User trying to create or unregister an MBean");
            throw new SecurityException("Access denied: " + methodName);
        }

        // Retrieve Subject from current AccessControlContext
        AccessControlContext acc = AccessController.getContext();
        Subject subject = Subject.getSubject(acc);

        try
        {
            if(invokeDirectly(methodName, args, subject))
            {
                return method.invoke(_mbs, args);
            }

            try
            {
                AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(subject);
            }
            catch(Exception e)
            {
                throw new SecurityException("Access denied: no authenticated principal", e);
            }

            // Save the subject
            SecurityManager.setThreadSubject(subject);
            CurrentActor.set(_logActor);
            try
            {
                return authoriseAndInvoke(method, args);
            }
            finally
            {
                CurrentActor.remove();
            }
        }
        catch (InvocationTargetException e)
        {
            Throwable targetException =  e.getCause();
            logTargetException(method, args, targetException);
            throw targetException;
        }
    }

    private void logTargetException(Method method, Object[] args, Throwable targetException)
    {
        Throwable error = null;
        if (targetException instanceof RuntimeErrorException)
        {
            error = ((RuntimeErrorException)targetException).getCause();
        }
        else if (targetException instanceof Error)
        {
            error = targetException;
        }
        if (error == null)
        {
            _logger.debug("Exception was thrown on invoking of " + method + " with arguments " + Arrays.toString(args), targetException);
        }
        else
        {
            _logger.error("Unexpected error occured on invoking of " + method + " with arguments " + Arrays.toString(args), targetException);
        }
    }

    private Object authoriseAndInvoke(Method method, Object[] args) throws IllegalAccessException, InvocationTargetException
    {
        String methodName;
        // Get the component, type and impact, which may be null
        String type = getType(method, args);
        String vhost = getVirtualHost(method, args);
        int impact = getImpact(method, args);

        // Get the security manager for the virtual host (if set)
        SecurityManager security;
        if (vhost == null)
        {
            security = _broker.getSecurityManager();
        }
        else
        {
            VirtualHost virtualHost = _broker.findVirtualHostByName(vhost);
            if (virtualHost == null)
            {
                throw new IllegalArgumentException("Virtual host with name '" + vhost + "' is not found.");
            }
            security = virtualHost.getSecurityManager();
        }

        methodName = getMethodName(method, args);
        if (isAccessMethod(methodName) || impact == MBeanOperationInfo.INFO)
        {
            // Check for read-only method invocation permission
            if (!security.authoriseMethod(Operation.ACCESS, type, methodName))
            {
                throw new SecurityException("Permission denied: Access " + methodName);
            }
        }
        else
        {
            // Check for setting properties permission
            if (!security.authoriseMethod(Operation.UPDATE, type, methodName))
            {
                throw new SecurityException("Permission denied: Update " + methodName);
            }
        }

        boolean oldAccessChecksDisabled = false;
        if(_managementRightsInferAllAccess)
        {
            oldAccessChecksDisabled = SecurityManager.setAccessChecksDisabled(true);
        }

        try
        {
            return method.invoke(_mbs, args);
        }
        finally
        {
            if(_managementRightsInferAllAccess)
            {
                SecurityManager.setAccessChecksDisabled(oldAccessChecksDisabled);
            }
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
                _logger.error("Unable to determine mbean impact for method : " + mbeanMethod, ex);
            }
        }

        return -1;
    }

    private boolean isAccessMethod(String methodName)
    {
        //handle standard get/query/is methods from MBeanServer
        return (methodName.startsWith("query") || methodName.startsWith("get") || methodName.startsWith("is"));
    }

}

