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
package org.apache.qpid.gentools;

public class AmqpVersion implements Comparable<AmqpVersion>
{
	private int major;
	private int minor;
	
	public AmqpVersion(int major, int minor)
	{
		this.major = major;
		this.minor = minor;
	}

	public AmqpVersion(AmqpVersion version)
	{
		this.major = version.major;
		this.minor = version.minor;
	}
	
	public int getMajor()
	{
		return major;
	}
	
	public int getMinor()
	{
		return minor;
	}
	
	public int compareTo(AmqpVersion v)
	{
		if (major != v.major)
			return major - v.major;
		if (minor != v.minor)
			return minor - v.minor;
		return 0;
	}
	
	public String namespace()
	{
		return "ver_" + major + "_" + minor;
	}
	
	public String toString()
	{
		return major + "-" + minor;
	}
}
