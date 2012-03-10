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

    public void subtract(final RangeSet other)
    {
        final Iterator<Range> otherIter = other.iterator() ;
        if (otherIter.hasNext())
        {
            Range otherRange = otherIter.next();
            final ListIterator<Range> iter = ranges.listIterator() ;
            if (iter.hasNext())
            {
                Range range = iter.next();
                do
                {
                    if (otherRange.getUpper() < range.getLower())
                    {
                        otherRange = nextRange(otherIter) ;
                    }
                    else if (range.getUpper() < otherRange.getLower())
                    {
                        range = nextRange(iter) ;
                    }
                    else
                    {
                        final boolean first = range.getLower() < otherRange.getLower() ;
                        final boolean second = otherRange.getUpper() < range.getUpper() ;

                        if (first)
                        {
                            iter.set(Range.newInstance(range.getLower(), otherRange.getLower()-1)) ;
                            if (second)
                            {
                                iter.add(Range.newInstance(otherRange.getUpper()+1, range.getUpper())) ;
                                iter.previous() ;
                                range = iter.next() ;
                            }
                            else
                            {
                                range = nextRange(iter) ;
                            }
                        }
                        else if (second)
                        {
                            range = Range.newInstance(otherRange.getUpper()+1, range.getUpper()) ;
                            iter.set(range) ;
                            otherRange = nextRange(otherIter) ;
                        }
                        else
                        {
                            iter.remove() ;
                            range = nextRange(iter) ;
                        }
                    }
                }
                while ((otherRange != null) && (range != null)) ;
            }
        }
    }

    private Range nextRange(final Iterator<Range> iter)
    {
        return (iter.hasNext() ? iter.next() : null) ;
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
