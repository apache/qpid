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
using System;
using Apache.Qpid.Messaging;

namespace Apache.Qpid.Client
{
  public abstract class Closeable : ICloseable
    {
        /// <summary>
        /// Used to ensure orderly closing of the object. The only method that is allowed to be called
        /// from another thread of control is close().
        /// </summary>
        protected readonly object _closingLock = new object();

        /// <summary>
        /// All access to this field should be using the Inerlocked class, to make it atomic.
        /// Hence it is an int since you cannot use a bool with the Interlocked class.
        /// </summary>
        protected int _closed = NOT_CLOSED;

        protected const int CLOSED = 1;
        protected const int NOT_CLOSED = 2;

        /// <summary>
        /// Checks the not closed.
        /// </summary>
        protected void CheckNotClosed()
        {
            if (_closed == CLOSED)
            {
                throw new InvalidOperationException("Object " + ToString() + " has been closed");
            }
        }

        /// <summary>
        /// Gets a value indicating whether this <see cref="Closeable"/> is closed.
        /// </summary>
        /// <value><c>true</c> if closed; otherwise, <c>false</c>.</value>
        public bool Closed
        {
            get
            {
                return _closed == CLOSED;
            }
        }

        /// <summary>
        /// Close the resource
        /// </summary>
        /// <exception cref="QpidMessagingException">If something goes wrong</exception>
        public abstract void Close();
    }
}
