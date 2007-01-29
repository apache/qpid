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
using System.Runtime.Serialization;

namespace Qpid.Buffer
{
    /// <summary>
    /// An exception thrown when the data the <see cref="ByteBuffer"/> 
    /// contains is corrupt
    /// </summary>
    [Serializable]
    public class BufferDataException : Exception
    {
        public BufferDataException()
        {
        }

        public BufferDataException( String message ) : base(message)
        {
        }

        public BufferDataException( String message, Exception cause ) : base(message, cause)
        {
        }

        public BufferDataException( Exception cause ) : base("", cause)
        {
        }

        protected BufferDataException(SerializationInfo info, StreamingContext ctxt)
           : base(info, ctxt)
        {
        }
    }    
}
