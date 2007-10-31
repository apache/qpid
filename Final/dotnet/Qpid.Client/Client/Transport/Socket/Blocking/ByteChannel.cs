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
using log4net;
using Apache.Qpid.Buffer;

namespace Apache.Qpid.Client.Transport.Socket.Blocking
{
    class ByteChannel : IByteChannel
    {
        // Warning: don't use this log for regular logging.
        private static readonly ILog _ioTraceLog = LogManager.GetLogger("Qpid.Client.ByteChannel.Tracing");

       private IByteChannel _lowerChannel;
        
        public ByteChannel(IByteChannel lowerChannel)
        {
            _lowerChannel = lowerChannel;
        }

        public ByteBuffer Read()
        {
            ByteBuffer result = _lowerChannel.Read();

            // TODO: Move into decorator.
            if (_ioTraceLog.IsDebugEnabled)
            {
                _ioTraceLog.Debug(String.Format("READ {0}", result));
            }
            
            return result;
        }

        public IAsyncResult BeginRead(AsyncCallback callback, object state)
        {
           return _lowerChannel.BeginRead(callback, state);
        }

        public ByteBuffer EndRead(IAsyncResult result)
        {
           ByteBuffer buffer = _lowerChannel.EndRead(result);
           if ( _ioTraceLog.IsDebugEnabled )
           {
              _ioTraceLog.Debug(String.Format("READ {0}", buffer));
           }
           return buffer;
        }

        public void Write(ByteBuffer buffer)
        {
            // TODO: Move into decorator.
            if (_ioTraceLog.IsDebugEnabled)
            {
                _ioTraceLog.Debug(String.Format("WRITE {0}", buffer));
            }

            _lowerChannel.Write(buffer);
        }
     
        public IAsyncResult BeginWrite(ByteBuffer buffer, AsyncCallback callback, object state)
        {
           if ( _ioTraceLog.IsDebugEnabled )
           {
              _ioTraceLog.Debug(String.Format("WRITE {0}", buffer));
           }
           return _lowerChannel.BeginWrite(buffer, callback, state);
        }

        public void EndWrite(IAsyncResult result)
        {
           _lowerChannel.EndWrite(result);
        }
     }
}
