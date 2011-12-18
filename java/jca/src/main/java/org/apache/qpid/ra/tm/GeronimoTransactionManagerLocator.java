/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.qpid.ra.tm;

import java.util.Set;

import javax.transaction.TransactionManager;

import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.gbean.AbstractNameQuery;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.KernelRegistry;

public class GeronimoTransactionManagerLocator
{

    public GeronimoTransactionManagerLocator()
    {
    }

    public TransactionManager getTransactionManager()
    {
        try
        {
            Kernel kernel = KernelRegistry.getSingleKernel();
            AbstractNameQuery query = new AbstractNameQuery(TransactionManager.class.getName ());
            Set<AbstractName> names = kernel.listGBeans(query);

            if (names.size() != 1)
            {
                throw new IllegalStateException("Expected one transaction manager, not " + names.size());
            }

            AbstractName name = names.iterator().next();
            TransactionManager transMg = (TransactionManager) kernel.getGBean(name);
            return (TransactionManager)transMg;

        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }


}
