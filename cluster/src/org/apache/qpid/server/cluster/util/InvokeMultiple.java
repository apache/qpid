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
package org.apache.qpid.server.cluster.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.HashSet;

/**
 * Allows a method to be invoked on a list of listeners with one call
 *
 */
public class InvokeMultiple <T> implements InvocationHandler
{
    private final Set<T> _targets = new HashSet<T>();
    private final T _proxy;

    public InvokeMultiple(Class<? extends T> type)
    {
        _proxy = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        Set<T> targets;
        synchronized(this)
        {
            targets = new HashSet<T>(_targets);
        }

        for(T target : targets)
        {
            method.invoke(target, args);
        }
        return null;
    }

    public synchronized void addListener(T t)
    {
        _targets.add(t);
    }

    public synchronized void removeListener(T t)
    {
        _targets.remove(t);
    }

    public T getProxy()
    {
        return _proxy;
    }
}
