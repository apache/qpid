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
using System.Collections.Generic;
using System.Text;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport
{
	
	
	/// <summary> 
    /// RangeSet
	/// </summary>

    public sealed class RangeSet : IEnumerable<Range> 
	{
		    private readonly LinkedList<Range> ranges = new LinkedList<Range>();

	    IEnumerator IEnumerable.GetEnumerator()
	    {
	        return GetEnumerator();
	    }

	    public IEnumerator<Range> GetEnumerator()
	    {
	        return ranges.GetEnumerator();
	    }


	    public int size()
    {
	        return ranges.Count;
    }

   

    public Range getFirst()
    {
        return ranges.First.Value;
    }

    public bool includes(Range range)
    {
        foreach (Range r in this)
        {
            if (r.includes(range))
            {
                return true;
            }
        }

        return false;
    }

    public bool includes(int n)
    {
        foreach (Range r in this)
        {
            if (r.includes(n))
            {
                return true;
            }
        }

        return false;
    }

    public void add(Range range)
    {
         foreach (Range r in ranges)
        {
            if (range.touches(r))
            {
                ranges.Remove(r);
                range = range.span(r);
            }
            else if (Serial.lt(range.Upper, r.Lower ))
            {                               
                ranges.AddBefore(ranges.Find(r), range);
                return;
            }
        }
        ranges.AddLast(range);
    }

    public void add(int lower, int upper)
    {
        add(new Range(lower, upper));
    }

    public void add(int value)
    {
        add(value, value);
    }

    public void clear()
    {
        ranges.Clear();
    }

    public RangeSet copy()
    {
        RangeSet copy = new RangeSet();
        foreach( Range r in ranges)
        {
            copy.ranges.AddLast(r);
        }        
        return copy;
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.Append("{");
        bool first = true;
        foreach (Range range in ranges)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                str.Append(", ");
            }
            str.Append(range);
        }
        str.Append("}");
        return str.ToString();
    }
	}
}