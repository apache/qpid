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
package org.apache.qpid.ra.tm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.transaction.TransactionManager;

/**
 */
public class JBossTransactionManagerLocator
{
   private final String LOCATOR = "org.jboss.tm.TransactionManagerLocator" ;

   public TransactionManager getTm()
      throws SecurityException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
   {
      final ClassLoader classLoader = Thread.currentThread().getContextClassLoader() ;
      final Class<?> locatorClass ;
      try
      {
         locatorClass = classLoader.loadClass(LOCATOR) ;
      }
      catch (final ClassNotFoundException cnfe)
      {
         return null ;
      }

      Method instanceMethod = null ;
      try
      {
         instanceMethod = locatorClass.getMethod("getInstance") ;
      }
      catch (final NoSuchMethodException nsme) {} // ignore

      final Object instance ;
      final String locatorMethodName ;
      if (instanceMethod != null)
      {
         instance = instanceMethod.invoke(null) ;
         locatorMethodName = "locate" ;
      }
      else
      {
         instance = null ;
         locatorMethodName = "locateTransactionManager" ;
      }
      final Method locatorMethod = locatorClass.getMethod(locatorMethodName) ;
      return (TransactionManager) locatorMethod.invoke(instance) ;
   }
}
