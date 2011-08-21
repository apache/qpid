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
package org.apache.qpid.server.signal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.log4j.Logger;

public abstract class SignalHandlerTask
{
    private static final Logger LOGGER = Logger.getLogger(SignalHandlerTask.class);

    private static final String HANDLE_METHOD = "handle";
    private static final String SUN_MISC_SIGNAL_CLASS = "sun.misc.Signal";
    private static final String SUN_MISC_SIGNAL_HANDLER_CLASS = "sun.misc.SignalHandler";

    public boolean register(final String signalName)
    {
        try
        {
            //try to load the signal handling classes
            Class<?> signalClazz = Class.forName(SUN_MISC_SIGNAL_CLASS);
            Class<?> handlerClazz = Class.forName(SUN_MISC_SIGNAL_HANDLER_CLASS);

            //create an InvocationHandler that just executes the SignalHandlerTask
            InvocationHandler invoker = new InvocationHandler()
            {
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
                {
                    handle();

                    return null;
                }
            };

            //create a dynamic proxy implementing SignalHandler
            Object handler = Proxy.newProxyInstance(handlerClazz.getClassLoader(), new Class[]{handlerClazz}, invoker);

            //create the Signal to handle
            Constructor<?> signalConstructor = signalClazz.getConstructor(String.class);
            Object signal = signalConstructor.newInstance(signalName);

            //invoke the Signal.handle(signal, handler) method
            Method handleMethod = signalClazz.getMethod(HANDLE_METHOD, signalClazz, handlerClazz);
            handleMethod.invoke(null, signal, handler);
        }
        catch (Exception e)
        {
            LOGGER.debug("Unable to register handler for Signal " + signalName + " due to exception: " + e, e);
            return false;
        }

        return true;
    }

    public abstract void handle();

    public static String getPlatformDescription()
    {
        String name = System.getProperty("os.name");
        String osVer = System.getProperty("os.version");
        String jvmVendor = System.getProperty("java.vm.vendor");
        String jvmName = System.getProperty("java.vm.name");
        String javaRuntimeVer = System.getProperty("java.runtime.version");

        return "OS: " + name + " " + osVer + ", JVM:" + jvmVendor + " " + jvmName + " " + javaRuntimeVer;
    }
}
