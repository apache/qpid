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
package org.apache.qpid.transport.vm;

import java.lang.reflect.Method;

import org.apache.qpid.BrokerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to start an InVm broker instance.
 */
public class VmBroker
{
    private static final String BROKER_INSTANCE = "org.apache.qpid.server.BrokerInstance";
 
    private static final Logger _logger = LoggerFactory.getLogger(VmBroker.class);
    
    private static Object _instance = null;
    
    public static void createVMBroker() throws VMBrokerCreationException
    {
        if (_instance == null)
        {
	        BrokerOptions options = new BrokerOptions();
	        options.setProtocol("vm");
	        options.setBind("localhost");
	        options.setPorts(1);
 
	        createVMBroker(options);
        }
    }

    public static void createVMBroker(BrokerOptions options) throws VMBrokerCreationException
    {
        synchronized (VmBroker.class)
        {
            if (_instance == null)
            {
                try
                {
                    Class<?> brokerClass = Class.forName(BROKER_INSTANCE);
                    Object brokerInstance = brokerClass.newInstance();
                    
                    Class<?>[] types = { BrokerOptions.class };
                    Object[] args = { options };
                    Method startup = brokerClass.getMethod("startup", types);
                    startup.invoke(brokerInstance, args);
                    
                    _instance = brokerInstance;
                }
                catch (Exception e)
                {
                    String because;
                    if (e.getCause() == null)
                    {
                        because = e.toString();
                    }
                    else
                    {
                        because = e.getCause().toString();
                    }
                    _logger.warn("Unable to create InVM broker instance: " + because);
        
                    throw new VMBrokerCreationException(because + " Stopped InVM broker instance creation", e);
                }
                _logger.info("Created InVM broker instance.");
            }
        }
    }

    public static void killVMBroker()
    {
        _logger.info("Killing InVM broker");
        synchronized (VmBroker.class)
        {
            if (_instance != null)
            {
                try
                {
                    Class<?> brokerClass = Class.forName(BROKER_INSTANCE);
                    Method shutdown = brokerClass.getMethod("shutdown", new Class[0]);
                    
                    shutdown.invoke(_instance);
                    
                    _instance = null;
                }
                catch (Exception e)
                {
                    String because;
                    if (e.getCause() == null)
                    {
                        because = e.toString();
                    }
                    else
                    {
                        because = e.getCause().toString();
                    }
                    _logger.warn("Error shutting down broker instance: " + because);
                }
            }
            _logger.info("Stopped InVM broker instance");
        }
    }
}
