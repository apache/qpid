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
using System.Globalization ;
using org.apache.qpid.transport.util;
using org.apache.qpid.transport.codec ;

namespace org.apache.qpid.console
{

	/**
	 * Identifies a specific class and version on the bus.
	 */
	public class ClassKey
	{
		public string PackageName { get; set; }
		public string ClassName { get; set; }	
		public long[] Hash = new long[4] ;
					
		public ClassKey(String keyString) {
			string delims = ":()" ;
			string[] parts = keyString.Split(delims.ToCharArray()) ;
			if (parts.Length < 3) {
				throw new Exception("Invalid class key format. Format should be package:class(bytes)") ;
			}
			PackageName = parts[0] ;
			ClassName = parts[1] ;
			delims = "-" ;
			string[] bytes = parts[2].Split(delims.ToCharArray()) ; 
			if (bytes.Length != 4) {
				throw new Exception("Invalid class key format. Bytes should be in the format HEX-HEX-HEX-HEX") ;
			}			
			Hash[0] = long.Parse(bytes[0], NumberStyles.HexNumber) ;
			Hash[1] = long.Parse(bytes[1], NumberStyles.HexNumber) ;
			Hash[2] = long.Parse(bytes[2], NumberStyles.HexNumber) ;
			Hash[3] = long.Parse(bytes[3], NumberStyles.HexNumber) ;
		}
		
		public ClassKey(Decoder dec) {
			PackageName = dec.readStr8() ;
			ClassName = dec.readStr8() ;
			Hash[0] = dec.readUint32() ;
			Hash[1] = dec.readUint32() ;	
			Hash[2] = dec.readUint32() ;
			Hash[3] = dec.readUint32() ;
			
		}
		
		public string GetKeyString() {
			string hashString = GetHashString() ;			
			return String.Format("{0}:{1}({2})", PackageName, ClassName, hashString) ;
		}
		
		public string GetHashString() {
			return String.Format("{0:x8}-{1:x8}-{2:x8}-{3:x8}", (long) Hash[0],Hash[1], Hash[2],Hash[3]) ;
		}
		
		public void encode(Encoder enc) {
			enc.writeStr8(PackageName) ;
			enc.writeStr8(ClassName) ;					
			enc.writeUint32(Hash[0]) ;
			enc.writeUint32(Hash[1]) ;
			enc.writeUint32(Hash[2]) ;
			enc.writeUint32(Hash[3]) ;			
		}
		
		override public string ToString() {
			return String.Format("ClassKey: {0}", GetKeyString()) ;
		}
		
		public override int GetHashCode ()
		{
			return GetKeyString().GetHashCode() ;
		}
		
		public override bool Equals (object obj)
		{
			if (obj.GetType().Equals(this.GetType())) {
				ClassKey other = (ClassKey) obj ;
				return (other.GetKeyString().Equals(this.GetKeyString())) ;
			}
			else {
				return false ;
			}
		}

	}
}
