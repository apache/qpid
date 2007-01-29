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

namespace Qpid.Buffer
{
   /// <summary>
   /// Allocates <see cref="ByteBuffer"/>'s and manages them. Please 
   /// implement this interface if you need more advanced memory management scheme
   /// </summary>
   public interface IByteBufferAllocator
   {
      /// <summary>
      /// Returns the buffer which is capable of the specified size.
      /// </summary>
      /// <param name="capacity">The capacity of the buffer</param>
      /// <param name="direct">true to get a direct buffer, false to get a heap buffer</param>
      ByteBuffer Allocate(int capacity, bool direct);

      /// <summary>
      /// Wraps the specified buffer
      /// </summary>
      /// <param name="nioBuffer">fixed byte buffer</param>
      /// <returns>The wrapped buffer</returns>
      ByteBuffer Wrap(FixedByteBuffer nioBuffer);


      /// <summary>
      /// Dispose of this allocator.
      /// </summary>
      void Dispose();
   }
}