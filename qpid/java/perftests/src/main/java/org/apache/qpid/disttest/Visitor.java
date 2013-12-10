/*
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
package org.apache.qpid.disttest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A variation of the visitor pattern that uses reflection to call the correct
 * visit method. By convention, subclasses should provide public
 * <pre>visit(SpecificClass)</pre> methods.
 */
public abstract class Visitor
{

    private static final String VISITOR_METHOD_NAME = "visit";

    public void visit(Object targetObject)
    {
        Class<? extends Object> targetObjectClass = targetObject.getClass();
        final Method method = findVisitMethodForTargetObjectClass(targetObjectClass);
        invokeVisitMethod(targetObject, method);
    }

    private Method findVisitMethodForTargetObjectClass(
            Class<? extends Object> targetObjectClass)
    {
        final Method method;
        try
        {
            method = getClass().getDeclaredMethod(VISITOR_METHOD_NAME, targetObjectClass);
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Failed to find method " + VISITOR_METHOD_NAME + " on object of class " + targetObjectClass, e);
        }
        return method;
    }

    private void invokeVisitMethod(Object targetObject, final Method method)
    {
        try
        {
            method.invoke(this, targetObject);
        }
        catch (IllegalArgumentException e)
        {
            throw new DistributedTestException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new DistributedTestException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new DistributedTestException(e.getCause());
        }
    }
}
