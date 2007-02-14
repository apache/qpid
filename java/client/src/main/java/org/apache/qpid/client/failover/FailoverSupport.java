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
package org.apache.qpid.client.failover;

import javax.jms.JMSException;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;

public abstract class FailoverSupport
{
    private static final Logger _log = Logger.getLogger(FailoverSupport.class);

    public Object execute(AMQConnection con) throws JMSException
    {
        // We wait until we are not in the middle of failover before acquiring the mutex and then proceeding.
        // Any method that can potentially block for any reason should use this class so that deadlock will not
        // occur. The FailoverException is propagated by the AMQProtocolHandler to any listeners (e.g. frame listeners)
        // that might be causing a block. When that happens, the exception is caught here and the mutex is released
        // before waiting for the failover to complete (either successfully or unsuccessfully).
        while (true)
        {
            try
            {
                con.blockUntilNotFailingOver();
            }
            catch (InterruptedException e)
            {
                _log.info("Interrupted: " + e, e);
                return null;
            }
            synchronized (con.getFailoverMutex())
            {
                try
                {
                    return operation();
                }
                catch (FailoverException e)
                {
                    _log.info("Failover exception caught during operation: " + e, e);
                }
            }
        }
    }

    protected abstract Object operation() throws JMSException;
}
