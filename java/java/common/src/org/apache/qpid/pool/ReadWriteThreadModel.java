/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.pool;

import org.apache.mina.common.IoFilterChain;
import org.apache.mina.filter.ReferenceCountingIoFilter;
import org.apache.mina.common.ThreadModel;

public class ReadWriteThreadModel implements ThreadModel
{
    public void buildFilterChain(IoFilterChain chain) throws Exception
    {
        ReferenceCountingExecutorService executor = ReferenceCountingExecutorService.getInstance();
        PoolingFilter asyncRead = new PoolingFilter(executor, PoolingFilter.READ_EVENTS,
                                                    "AsynchronousReadFilter");
        PoolingFilter asyncWrite = new PoolingFilter(executor, PoolingFilter.WRITE_EVENTS,
                                                     "AsynchronousWriteFilter");

        chain.addFirst("AsynchronousReadFilter", new ReferenceCountingIoFilter(asyncRead));
        chain.addLast("AsynchronousWriteFilter", new ReferenceCountingIoFilter(asyncWrite));
    }
}
