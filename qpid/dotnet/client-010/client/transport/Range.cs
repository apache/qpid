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
        private int _lower;
        private int _upper;

        public Range(int lower, int upper)
        {
            _lower = lower;
            _upper = upper;
        }

        public int Lower
        {
            get { return _lower; }
            set { _lower = value; }
        }
        public int Upper
        {
            get { return _upper; }
            set { _upper = value; }
        }

        public bool includes(int value)
        {
            return Serial.le(_lower, value) && Serial.le(value, _upper);
        }

        public bool includes(Range range)
        {
            return includes(range._lower) && includes(range._upper);
        }

        public bool intersects(Range range)
        {
            return (includes(range._lower) || includes(range._upper) ||
                    range.includes(_lower) || range.includes(_upper));
        }

        public bool touches(Range range)
        {
            return (intersects(range) ||
                    includes(range._upper + 1) || includes(range._lower - 1) ||
                    range.includes(_upper + 1) || range.includes(_lower - 1));
        }

        public Range span(Range range)
        {
            return new Range(Serial.min(_lower, range._lower), Serial.max(_upper, range._upper));
        }

        public List<Range> subtract(Range range)
        {
            List<Range> result = new List<Range>();

            if (includes(range._lower) && Serial.le(_lower, range._lower - 1))
            {
                result.Add(new Range(_lower, range._lower - 1));
            }

            if (includes(range._upper) && Serial.le(range._upper + 1, _upper))
            {
                result.Add(new Range(range._upper + 1, _upper));
            }

            if (result.Count == 0 && !range.includes(this))
            {
                result.Add(this);
            }

            return result;
        }

        public Range intersect(Range range)
        {
            int l = Serial.max(_lower, range._lower);
            int r = Serial.min(_upper, range._upper);
            return Serial.gt(l, r) ? null : new Range(l, r);
        }

        public String toString()
        {
            return "[" + _lower + ", " + _upper + "]";
        }
    }
}