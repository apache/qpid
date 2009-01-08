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
using System.Text;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport.codec
{
	
	
	/// <summary> 
	/// MSDecoder
	/// 
	/// </summary>
	
	
	public sealed class MSDecoder:AbstractDecoder
	{

	    private BinaryReader reader;

        public void init(MemoryStream st)
		{            
            reader = new BinaryReader(st, Encoding.BigEndianUnicode);
		}
		
		protected override byte doGet()
		{
		    return reader.ReadByte();
		}

        protected override void doGet(byte[] bytes)
		{
            reader.Read(bytes, 0, bytes.Length);
		}
	
		public override bool hasRemaining()
		{
		    return (reader.BaseStream.Position < reader.BaseStream.Length);
		}

        public override short readUint8()
		{
			return (short) (0xFF & reader.ReadByte());
		}

        public override int readUint16()
		{
		    return ByteEncoder.GetBigEndian((UInt16) reader.ReadInt16());
		}

        public override long readUint32()
		{
            return ByteEncoder.GetBigEndian((UInt32) reader.ReadInt32());
		}

        public override long readUint64()
		{
		    return (long) ByteEncoder.GetBigEndian(reader.ReadInt64());            
		}
	}
}