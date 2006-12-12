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
using Qpid.Buffer;

namespace Qpid.Buffer
{
    /**
     * Allocates {@link ByteBuffer}s and manages them.  Please implement this
     * interface if you need more advanced memory management scheme.
     */
    public interface ByteBufferAllocator
    {
        /**
         * Returns the buffer which is capable of the specified size.
         * 
         * @param capacity the capacity of the buffer
         * @param direct <tt>true</tt> to get a direct buffer,
         *               <tt>false</tt> to get a heap buffer.
         */
        ByteBuffer allocate(int capacity, bool direct);

        /**
         * Wraps the specified NIO {@link FixedByteBuffer} into MINA buffer.
         */
        ByteBuffer wrap(FixedByteBuffer nioBuffer);

        /**
         * Dispose of this allocator.
         */
        void dispose();
    }
}