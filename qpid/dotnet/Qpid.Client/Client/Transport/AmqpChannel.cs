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
using System.Collections;
using log4net;
using Qpid.Buffer;
using Qpid.Codec;
using Qpid.Codec.Support;
using Qpid.Framing;

namespace Qpid.Client.Transport
{
    public class AmqpChannel : IProtocolChannel
    {
        // Warning: don't use this log for regular logging.
        static readonly ILog _protocolTraceLog = LogManager.GetLogger("Qpid.Client.ProtocolChannel.Tracing");
        
        IByteChannel byteChannel;
        IProtocolEncoder encoder;
        IProtocolDecoder decoder;

        public AmqpChannel(IByteChannel byteChannel)
        {
            this.byteChannel = byteChannel;    
            
            AMQProtocolProvider protocolProvider = new AMQProtocolProvider();
            IProtocolCodecFactory factory = protocolProvider.CodecFactory;
            encoder = factory.Encoder;
            decoder = factory.Decoder;
        }

        public Queue Read()
        {
            ByteBuffer buffer = byteChannel.Read();

            Queue frames = Decode(buffer);
            
            // TODO: Refactor to decorator.
            if (_protocolTraceLog.IsDebugEnabled)
            {
                foreach (object o in frames)
                {
                    _protocolTraceLog.Debug(String.Format("READ {0}", o));
                }
            }

            return frames;
        }

        public void Write(IDataBlock o)
        {
            // TODO: Refactor to decorator.
            if (_protocolTraceLog.IsDebugEnabled)
            {
                _protocolTraceLog.Debug(String.Format("WRITE {0}", o));
            }

            byteChannel.Write(Encode(o));
        }

        private ByteBuffer Encode(object o)
        {
            SingleProtocolEncoderOutput output = new SingleProtocolEncoderOutput();
            encoder.Encode(o, output);
            return output.buffer;
        }

        private Queue Decode(ByteBuffer byteBuffer)
        {
            SimpleProtocolDecoderOutput outx = new SimpleProtocolDecoderOutput();
            decoder.Decode(byteBuffer, outx);
            return outx.MessageQueue;
        }
    }
}

