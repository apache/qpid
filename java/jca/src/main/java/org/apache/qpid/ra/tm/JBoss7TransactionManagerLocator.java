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

import javax.naming.InitialContext;
import javax.transaction.TransactionManager;

public class JBoss7TransactionManagerLocator
{
    private static final String TM_JNDI_NAME = "java:jboss/TransactionManager";

    public TransactionManager getTm() throws Exception
    {
        InitialContext ctx = null;

        try
        {
            ctx = new InitialContext();
            return (TransactionManager)ctx.lookup(TM_JNDI_NAME);
        }
        finally
        {
            try
            {
                if(ctx != null)
                {
                    ctx.close();
                }
            }
            catch(Exception ignore)
            {
            }
        }
    }
}
