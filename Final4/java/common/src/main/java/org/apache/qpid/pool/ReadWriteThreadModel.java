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
package org.apache.qpid.pool;

import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.ThreadModel;
import org.apache.mina.filter.ReferenceCountingIoFilter;

/**
 * ReadWriteThreadModel is a Mina i/o filter chain factory, which creates a filter chain with seperate filters to
 * handle read and write events. The seperate filters are {@link PoolingFilter}s, which have thread pools to handle
 * these events. The effect of this is that reading and writing may happen concurrently.
 *
 * <p/>Socket i/o will only happen with concurrent reads and writes if Mina has seperate selector threads for each.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Create a filter chain with seperate read and write thread pools for read/write Mina events.
 *     <td> {@link PoolingFilter}
 * </table>
 */
public class ReadWriteThreadModel implements ThreadModel
{
    /** Holds the singleton instance of this factory. */
    private static final ReadWriteThreadModel _instance = new ReadWriteThreadModel();

    /** Holds the thread pooling filter for reads. */
    private final PoolingFilter _asynchronousReadFilter;

    /** Holds the thread pooloing filter for writes. */
    private final PoolingFilter _asynchronousWriteFilter;

    /**
     * Creates a new factory for concurrent i/o, thread pooling filter chain construction. This is private, so that
     * only a singleton instance of the factory is ever created.
     */
    private ReadWriteThreadModel()
    {
        final ReferenceCountingExecutorService executor = ReferenceCountingExecutorService.getInstance();
        _asynchronousReadFilter = PoolingFilter.createAynschReadPoolingFilter(executor, "AsynchronousReadFilter");
        _asynchronousWriteFilter = PoolingFilter.createAynschWritePoolingFilter(executor, "AsynchronousWriteFilter");
    }

    /**
     * Gets the singleton instance of this filter chain factory.
     *
     * @return The singleton instance of this filter chain factory.
     */
    public static ReadWriteThreadModel getInstance()
    {
        return _instance;
    }

    /**
     * Gets the read filter.
     *
     * @return The read filter.
     */
    public PoolingFilter getAsynchronousReadFilter()
    {
        return _asynchronousReadFilter;
    }

    /**
     * Gets the write filter.
     *
     * @return The write filter.
     */
    public PoolingFilter getAsynchronousWriteFilter()
    {
        return _asynchronousWriteFilter;
    }

    /**
     * Adds the concurrent read and write filters to a filter chain.
     *
     * @param chain The Mina filter chain to add to.
     */
    public void buildFilterChain(IoFilterChain chain)
    {
        chain.addFirst("AsynchronousReadFilter", new ReferenceCountingIoFilter(_asynchronousReadFilter));
        chain.addLast("AsynchronousWriteFilter", new ReferenceCountingIoFilter(_asynchronousWriteFilter));
    }
}
