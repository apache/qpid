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
using System.IO;
using org.apache.qpid.transport.codec;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport.network
{
    /// <summary> 
    /// Disassembler
    /// </summary>
    public sealed class Disassembler : Sender<ProtocolEvent>, ProtocolDelegate<Object>
    {
        private readonly IIOSender<MemoryStream> _sender;
        private readonly int _maxPayload;
        private readonly MemoryStream _header;
        private readonly BinaryWriter _writer;
        private readonly Object _sendlock = new Object();
        [ThreadStatic] static MSEncoder _encoder;


        public Disassembler(IIOSender<MemoryStream> sender, int maxFrame)
        {
            if (maxFrame <= Frame.HEADER_SIZE || maxFrame >= 64*1024)
            {
                throw new Exception(String.Format("maxFrame must be > {0} and < 64K: ", Frame.HEADER_SIZE) + maxFrame);
            }
            _sender = sender;
            _maxPayload = maxFrame - Frame.HEADER_SIZE;
            _header = new MemoryStream(Frame.HEADER_SIZE);
            _writer = new BinaryWriter(_header);
        }

        #region Sender Interface 

        public void send(ProtocolEvent pevent)
        {
            pevent.ProcessProtocolEvent(null, this);
        }

        public void flush()
        {
            lock (_sendlock)
            {
                _sender.flush();
            }
        }

        public void close()
        {
            lock (_sendlock)
            {
                _sender.close();
            }
        }

        #endregion

        #region ProtocolDelegate<Object> Interface 

        public void Init(Object v, ProtocolHeader header)
        {
            lock (_sendlock)
            {
                _sender.send(header.ToMemoryStream());
                _sender.flush();
            }
        }

        public void Control(Object v, Method method)
        {
            invokeMethod(method, SegmentType.CONTROL);
        }

        public void Command(Object v, Method method)
        {
            invokeMethod(method, SegmentType.COMMAND);
        }

        public void Error(Object v, ProtocolError error)
        {
            throw new Exception("Error: " + error);
        }

        #endregion

        #region private 

        private void frame(byte flags, byte type, byte track, int channel, int size, MemoryStream buf)
        {
            lock (_sendlock)
            {
                 _writer.Write(flags);
                _writer.Write(type);
                _writer.Write(ByteEncoder.GetBigEndian((UInt16)(size + Frame.HEADER_SIZE)));
                _writer.Write((byte)0);
                _writer.Write(track);
                _writer.Write(ByteEncoder.GetBigEndian((UInt16)( channel)));               
                _writer.Write((byte)0);
                _writer.Write((byte)0);
                _writer.Write((byte)0);
               _writer.Write((byte)0);
                _sender.send(_header);
                _header.Seek(0, SeekOrigin.Begin);               
                _sender.send(buf, size);
            }
        }

        private void fragment(byte flags, SegmentType type, ProtocolEvent mevent, MemoryStream buf)
        {
            byte typeb = (byte) type;
            byte track = mevent.EncodedTrack == Frame.L4 ? (byte) 1 : (byte) 0;
            int remaining = (int) buf.Length;
            buf.Seek(0, SeekOrigin.Begin);
            bool first = true;
            while (true)
            {
                int size = Math.Min(_maxPayload, remaining);
                remaining -= size;              

                byte newflags = flags;
                if (first)
                {
                    newflags |= Frame.FIRST_FRAME;
                    first = false;
                }
                if (remaining == 0)
                {
                    newflags |= Frame.LAST_FRAME;
                }                

                frame(newflags, typeb, track, mevent.Channel, size, buf);

                if (remaining == 0)
                {
                    break;
                }
            }
        }

        private MSEncoder getEncoder()
        {
            if( _encoder == null)
            {
                _encoder = new MSEncoder(4 * 1024);
            }
            return _encoder;
        }

        private void invokeMethod(Method method, SegmentType type)
        {
            MSEncoder encoder = getEncoder();
            encoder.init();
            encoder.writeUint16(method.getEncodedType());
            if (type == SegmentType.COMMAND)
            {
                if (method.Sync)
                {
                    encoder.writeUint16(0x0101);
                }
                else
                {
                    encoder.writeUint16(0x0100);
                }
            }
            method.write(_encoder);
            MemoryStream methodSeg = encoder.segment();

            byte flags = Frame.FIRST_SEG;

            bool payload = method.hasPayload();
            if (!payload)
            {
                flags |= Frame.LAST_SEG;
            }

            MemoryStream headerSeg = null;
            if (payload)
            {
                Header hdr = method.Header;
                Struct[] structs = hdr.Structs;

                foreach (Struct st in structs)
                {
                    encoder.writeStruct32(st);
                }
                headerSeg = encoder.segment();
            }

            lock (_sendlock)
            {
                fragment(flags, type, method, methodSeg);
                if (payload)
                {
                    fragment( 0x0, SegmentType.HEADER, method, headerSeg);
                    fragment(Frame.LAST_SEG, SegmentType.BODY, method, method.Body);
                }
            }
        }

        #endregion
    }
}