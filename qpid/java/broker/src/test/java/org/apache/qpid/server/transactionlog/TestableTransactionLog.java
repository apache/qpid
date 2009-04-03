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
package org.apache.qpid.server.transactionlog;

import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.store.TestTransactionLog;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.routing.RoutingTable;

import java.util.List;
import java.util.LinkedList;

public class TestableTransactionLog extends BaseTransactionLog implements TestTransactionLog
{

    List<Long> _singleEnqueues = new LinkedList<Long>();

    public TestableTransactionLog()
    {
        super(null);
    }

    public TestableTransactionLog(BaseTransactionLog delegate)
    {
        super(delegate.getDelegate());
    }

    public TestableTransactionLog(TransactionLog delegate)
    {
        super(delegate);
    }


    @Override
    public Object configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        if (_delegate != null)
        {
            TransactionLog configuredLog = (TransactionLog)_delegate.configure(virtualHost, base, config);

            // Unwrap any BaseTransactionLog
            if (configuredLog instanceof BaseTransactionLog)
            {
                _delegate = ((BaseTransactionLog)configuredLog).getDelegate();
            }
        }
        else
        {
                String delegateClass = config.getStoreConfiguration().getString("delegate");
            Class clazz = Class.forName(delegateClass);
            Object o = clazz.newInstance();

            if (!(o instanceof TransactionLog))
            {
                throw new ClassCastException("TransactionLog class must implement " + TransactionLog.class + ". Class " + clazz +
                                             " does not.");
            }
            _delegate = (TransactionLog) o;

            // If a TransactionLog uses the BaseTransactionLog then it will return this object.
             _delegate.configure(virtualHost, base, config);
        }
        return this;
    }

    public List<AMQQueue> getMessageReferenceMap(Long messageID)
    {
        return _idToQueues.get(messageID);
    }
}
