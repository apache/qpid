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
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport
{
	
	/// <summary> 
	/// Range
	/// </summary>


    public sealed class Range
    {
        private int lower;
        private int upper;

        public Range(int lower, int upper)
        {
            this.lower = lower;
            this.upper = upper;
        }

        public int Lower
        {
            get { return lower; }
            set { lower = value; }
        }
        public int Upper
        {
            get { return upper; }
            set { upper = value; }
        }

        public bool includes(int value)
        {
            return Serial.le(lower, value) && Serial.le(value, upper);
        }

        public bool includes(Range range)
        {
            return includes(range.lower) && includes(range.upper);
        }

        public bool intersects(Range range)
        {
            return (includes(range.lower) || includes(range.upper) ||
                    range.includes(lower) || range.includes(upper));
        }

        public bool touches(Range range)
        {
            return (intersects(range) ||
                    includes(range.upper + 1) || includes(range.lower - 1) ||
                    range.includes(upper + 1) || range.includes(lower - 1));
        }

        public Range span(Range range)
        {
            return new Range(Serial.min(lower, range.lower), Serial.max(upper, range.upper));
        }

        public List<Range> subtract(Range range)
        {
            List<Range> result = new List<Range>();

            if (includes(range.lower) && Serial.le(lower, range.lower - 1))
            {
                result.Add(new Range(lower, range.lower - 1));
            }

            if (includes(range.upper) && Serial.le(range.upper + 1, upper))
            {
                result.Add(new Range(range.upper + 1, upper));
            }

            if (result.Count == 0 && !range.includes(this))
            {
                result.Add(this);
            }

            return result;
        }

        public Range intersect(Range range)
        {
            int l = Serial.max(lower, range.lower);
            int r = Serial.min(upper, range.upper);
            return Serial.gt(l, r) ? null : new Range(l, r);
        }

        public String toString()
        {
            return "[" + lower + ", " + upper + "]";
        }
    }
}