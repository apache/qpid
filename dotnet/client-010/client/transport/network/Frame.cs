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

namespace org.apache.qpid.transport.network
{
    public sealed class Frame : NetworkEvent
    {
        internal static int HEADER_SIZE = 12;

        // XXX: enums?
        public const byte L1 = 0;
        public const byte L2 = 1;
        public const byte L3 = 2;
        public const byte L4 = 3;

        public static byte RESERVED = 0x0;

        public static byte VERSION = 0x0;

        public static byte FIRST_SEG = 0x8;
        public static byte LAST_SEG = 0x4;
        public static byte FIRST_FRAME = 0x2;
        public static byte LAST_FRAME = 0x1;

        private readonly byte flags;
        private readonly SegmentType type;
        private readonly byte track;
        private readonly int channel;
        private readonly MemoryStream body;
        private int _bodySize;


        public Frame(byte flags, SegmentType type, byte track, int channel, int bodySize,
                     MemoryStream body)
        {
            this.flags = flags;
            this.type = type;
            this.track = track;
            this.channel = channel;
            this.body = body;
            _bodySize = bodySize;
        }

        public int BodySize
        {
            get { return _bodySize; }
        }

        public MemoryStream Body
        {
            get { return body; }
        }

        public byte Flags
        {
            get { return flags; }
        }

        public int Channel
        {
            get { return channel; }
        }

        public int Size
        {
            get { return (int) body.Length;}
        }

        public SegmentType Type
        {
            get { return type; }
        }

        public byte Track
        {
            get { return track; }
        }

        private bool flag(byte mask)
        {
            return (flags & mask) != 0;
        }

        public bool isFirstSegment()
        {
            return flag(FIRST_SEG);
        }

        public bool isLastSegment()
        {
            return flag(LAST_SEG);
        }

        public bool isFirstFrame()
        {
            return flag(FIRST_FRAME);
        }

        public bool isLastFrame()
        {
            return flag(LAST_FRAME);
        }

        #region NetworkEvent Methods

        public void ProcessNetworkEvent(NetworkDelegate ndelegate)
        {
            ndelegate.Frame(this);
        }

        #endregion

        public String toString()
        {
            return String.Format
                ("[{0:d} {1:d} {2:d} {3} {4}{5}{6}{7}] ", Channel, Size, Track, Type,                 
                 isFirstSegment() ? 1 : 0, isLastSegment() ? 1 : 0,
                 isFirstFrame() ? 1 : 0, isLastFrame() ? 1 : 0);
        }

      
    }
}