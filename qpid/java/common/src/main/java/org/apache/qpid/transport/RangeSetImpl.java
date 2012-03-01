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
package org.apache.qpid.transport;

import static org.apache.qpid.util.Serial.lt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class RangeSetImpl implements RangeSet
{

    private List<Range> ranges;

    public RangeSetImpl()
    {
        ranges =  new ArrayList<Range>();
    }

    public RangeSetImpl(int size)
    {
        ranges =  new ArrayList<Range>(size);
    }


    public RangeSetImpl(org.apache.qpid.transport.RangeSetImpl copy)
    {
        ranges = new ArrayList<Range>(copy.ranges);
    }

    public int size()
    {
        return ranges.size();
    }

    public Iterator<Range> iterator()
    {
        return ranges.iterator();
    }

    public Range getFirst()
    {
        return ranges.get(0);
    }

    public Range getLast()
    {
        return ranges.get(ranges.size() - 1);
    }

    public boolean includes(Range range)
    {
        for (Range r : this)
        {
            if (r.includes(range))
            {
                return true;
            }
        }

        return false;
    }

    public boolean includes(int n)
    {
        for (Range r : this)
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
        ListIterator<Range> it = ranges.listIterator();

        while (it.hasNext())
        {
            Range next = it.next();
            if (range.touches(next))
            {
                it.remove();
                range = range.span(next);
            }
            else if (lt(range.getUpper(), next.getLower()))
            {
                it.previous();
                it.add(range);
                return;
            }
        }

        it.add(range);
    }

    public void add(int lower, int upper)
    {
        switch(ranges.size())
        {
            case 0:
                ranges.add(Range.newInstance(lower, upper));
                break;

            case 1:
                Range first = ranges.get(0);
                if(first.getUpper() + 1 >= lower && upper >= first.getUpper())
                {
                    ranges.set(0, Range.newInstance(first.getLower(), upper));
                    break;
                }

            default:
                add(Range.newInstance(lower, upper));
        }


    }

    public void add(int value)
    {
        add(value, value);
    }

    public void clear()
    {
        ranges.clear();
    }

    public RangeSet copy()
    {
        return new org.apache.qpid.transport.RangeSetImpl(this);
    }

    public String toString()
    {
        StringBuffer str = new StringBuffer();
        str.append("{");
        boolean first = true;
        for (Range range : ranges)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                str.append(", ");
            }
            str.append(range);
        }
        str.append("}");
        return str.toString();
    }
}
