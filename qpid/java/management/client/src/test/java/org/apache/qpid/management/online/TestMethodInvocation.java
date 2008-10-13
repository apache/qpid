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
package org.apache.qpid.management.online;

import java.io.IOException;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.qpid.management.domain.handler.impl.InvocationResult;

public class TestMethodInvocation extends BaseOnlineTestCase
{    
    /**
     * Tests the execution of the purge() method on a queue instance.
     * 
     * <br>precondition : QMan is up and running; managed domain model contains at least one queue.
     * <br>postcondition : method is invoked and result object indicates that all was performed correctly.
     */
    public void testInvokePurgeOnQueue() throws Exception
    {
        Set<ObjectName> names = connection.queryNames(
                new ObjectName("Q-MAN:*,package=org.apache.qpid.broker,class=queue"),null); 
        
        for (ObjectName objectName : names)
        {
            InvocationResult result = (InvocationResult) connection.invoke(objectName,"purge",new Object[]{0},new String[]{Integer.class.getName()});           
            assertEquals(0,result.getReturnCode());
            assertEquals("OK",result.getStatusText());
        }
    }
    
    /**
     * Tests the execution of the invocation request with an unknown method.
     * 
     * <br>precondition : QMan is up and running; managed domain model contains at least one queue.
     * <br>postcondition An exception is thrown indicating that the method was not found.
     */
    public void testInvokeWithUnknwonMethod() throws MalformedObjectNameException, NullPointerException, IOException, InstanceNotFoundException, MBeanException
    {
        Set<ObjectName> names = connection.queryNames(new ObjectName("Q-MAN:*,package=org.apache.qpid.broker,class=queue"),null); 
        System.out.println(names.size());
        
        for (ObjectName objectName : names)
        {
            try
            {
                InvocationResult result = (InvocationResult) connection.invoke(objectName,"spurgexyzhopethatitwontexists",new Object[]{1},new String[]{Integer.class.getName()});
            } catch (ReflectionException expected)
            {
                NoSuchMethodException exception = (NoSuchMethodException) expected.getCause();
                assertEquals("spurge",exception.getMessage());
            }           
        }        
    }    
}