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
using System.Collections.Generic;
using System.IO;
using org.apache.qpid.transport.codec;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport.network
{
    /// <summary> 
    /// Assembler
    /// </summary>
    public delegate void Processor(NetworkDelegate ndelegate);

    public class Assembler : NetworkDelegate, Receiver<ReceivedPayload<ProtocolEvent>>
    {
        private static readonly Logger log = Logger.get(typeof (Assembler));
        private readonly Dictionary<int, List<byte[]>> segments;
        private readonly Method[] incomplete;
        [ThreadStatic] static MSDecoder _decoder;
        private readonly Object m_objectLock = new object();

        // the event raised when a buffer is read from the wire        
        public event EventHandler<ReceivedPayload<ProtocolEvent>> ReceivedEvent;
        public event EventHandler<ExceptionArgs> ExceptionProcessing;
        public event EventHandler HandlerClosed;

        event EventHandler<ReceivedPayload<ProtocolEvent>> Receiver<ReceivedPayload<ProtocolEvent>>.Received
        {
            add
            {
                lock (m_objectLock)
                {
                    ReceivedEvent += value;
                }
            }
            remove
            {
                lock (m_objectLock)
                {
                    ReceivedEvent -= value;
                }
            }
        }

        event EventHandler<ExceptionArgs> Receiver<ReceivedPayload<ProtocolEvent>>.Exception
        {
            add
            {
                lock (m_objectLock)
                {
                    ExceptionProcessing += value;
                }
            }
            remove
            {
                lock (m_objectLock)
                {
                    ExceptionProcessing -= value;
                }
            }
        }

        event EventHandler Receiver<ReceivedPayload<ProtocolEvent>>.Closed
        {
            add
            {
                lock (m_objectLock)
                {
                    HandlerClosed += value;
                }
            }
            remove
            {
                lock (m_objectLock)
                {
                    HandlerClosed -= value;
                }
            }
        }

        public Assembler()
        {
            segments = new Dictionary<int, List<byte[]>>();
            incomplete = new Method[64*1024];
        }

        // Invoked when a network event is received
        public void On_ReceivedEvent(object sender, ReceivedPayload<NetworkEvent> payload)
        {
            payload.Payload.ProcessNetworkEvent(this);
        }

        #region Interface NetworkDelegate

        public void Init(ProtocolHeader header)
        {
            Emit(0, header);
        }

        public void Error(ProtocolError error)
        {
            Emit(0, error);
        }

        public void Frame(Frame frame)
        {
            MemoryStream segment;
            if (frame.isFirstFrame() && frame.isLastFrame())
            {                
                byte[] tmp = new byte[frame.BodySize];
                frame.Body.Read(tmp, 0, tmp.Length);
                segment = new MemoryStream();
                BinaryWriter w = new BinaryWriter(segment);
                w.Write(tmp);
                assemble(frame, new MemoryStream(tmp));
            }
            else
            {
                List<byte[]> frames;
                if (frame.isFirstFrame())
                {
                    frames = new List<byte[]>();
                    setSegment(frame, frames);
                }
                else
                {
                    frames = getSegment(frame);
                }
                byte[] tmp = new byte[frame.BodySize];
                frame.Body.Read(tmp, 0, tmp.Length);
                frames.Add(tmp);

                if (frame.isLastFrame())
                {
                    clearSegment(frame);
                    segment = new MemoryStream();
                    BinaryWriter w = new BinaryWriter(segment);
                    foreach (byte[] f in frames)
                    {
                        w.Write(f);
                    }
                    assemble(frame, segment);
                }
            }
        }

        #endregion

        #region Private Support Functions


        private MSDecoder getDecoder()
        {
            if( _decoder == null )
            {
                _decoder = new MSDecoder();
            }
            return _decoder;
        }

        private void assemble(Frame frame, MemoryStream segment)
        {
            MSDecoder decoder = getDecoder();
            decoder.init(segment);
            int channel = frame.Channel;
            Method command;
            switch (frame.Type)
            {
                case SegmentType.CONTROL:
                    int controlType = decoder.readUint16();                    
                    Method control = Method.create(controlType);
                    control.read(decoder);
                    Emit(channel, control);
                    break;
                case SegmentType.COMMAND:
                    int commandType = decoder.readUint16();
                     // read in the session header, right now we don't use it
                    decoder.readUint16();
                    command = Method.create(commandType);
                    command.read(decoder);
                    if (command.hasPayload())
                    {
                        incomplete[channel] = command;
                    }
                    else
                    {
                        Emit(channel, command);
                    }
                    break;
                case SegmentType.HEADER:
                    command = incomplete[channel];
                    List<Struct> structs = new List<Struct>();
                    while (decoder.hasRemaining())                    
                    {
                        structs.Add(decoder.readStruct32());
                    }
                    command.Header = new Header(structs);
                    if (frame.isLastSegment())
                    {
                        incomplete[channel] = null;
                        Emit(channel, command);
                    }
                    break;
                case SegmentType.BODY:
                    command = incomplete[channel];                  
                    segment.Seek(0, SeekOrigin.Begin);
                    command.Body = segment;
                    incomplete[channel] = null;
                    Emit(channel, command);
                    break;
                default:
                    throw new Exception("unknown frame type: " + frame.Type);
            }
        }

        private int segmentKey(Frame frame)
        {
            return (frame.Track + 1)*frame.Channel;
        }

        private List<byte[]> getSegment(Frame frame)
        {
            return segments[segmentKey(frame)];
        }

        private void setSegment(Frame frame, List<byte[]> segment)
        {
            int key = segmentKey(frame);
            if (segments.ContainsKey(key))
            {
                Error(new ProtocolError(network.Frame.L2, "segment in progress: %s",
                                        frame));
            }
            segments.Add(segmentKey(frame), segment);            
        }

        private void clearSegment(Frame frame)
        {
            segments.Remove(segmentKey(frame));
        }

        // Emit a protocol event 
        private void Emit(int channel, ProtocolEvent protevent)
        {
            protevent.Channel = channel;
            log.debug("Assembler: protocol event:", protevent);
            ReceivedPayload<ProtocolEvent> payload = new ReceivedPayload<ProtocolEvent>();
            payload.Payload = protevent;
            if (ReceivedEvent != null)
            {
                ReceivedEvent(this, payload);
            }
            else
            {
                log.debug("No listener for event: {0}", protevent);
            }
        }

        #endregion
    }
}