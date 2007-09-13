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
package org.apache.qpidity.transport;

import static java.lang.Math.*;


/**
 * Range
 *
 * @author Rafael H. Schloming
 */

public class Range
{
    private final long lower;
    private final long upper;

    public Range(long lower, long upper)
    {
        this.lower = lower;
        this.upper = upper;
    }

    public long getLower()
    {
        return lower;
    }

    public long getUpper()
    {
        return upper;
    }

    public boolean includes(long value)
    {
        return lower <= value && value <= upper;
    }

    public boolean includes(Range range)
    {
        return includes(range.lower) && includes(range.upper);
    }

    public boolean intersects(Range range)
    {
        return (includes(range.lower) || includes(range.upper) ||
                range.includes(lower) || range.includes(upper));
    }

    public boolean touches(Range range)
    {
        return (includes(range.upper + 1) || includes(range.lower - 1) ||
                range.includes(upper + 1) || range.includes(lower - 1));
    }

    public Range span(Range range)
    {
        return new Range(min(lower, range.lower), max(upper, range.upper));
    }

    public String toString()
    {
        return "[" + lower + ", " + upper + "]";
    }

}
