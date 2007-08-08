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
package org.apache.qpidity;

import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.LinkedList;


/**
 * RangeSet
 *
 * @author Rafael H. Schloming
 */

public class RangeSet implements Iterable<Range>
{

    private LinkedList<Range> ranges = new LinkedList<Range>();

    public int size()
    {
        return ranges.size();
    }

    public Iterator<Range> iterator()
    {
        return ranges.iterator();
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
            else if (range.getUpper() < next.getLower())
            {
                it.previous();
                it.add(range);
                return;
            }
        }

        it.add(range);
    }

    public void add(long lower, long upper)
    {
        add(new Range(lower, upper));
    }

    public void add(long value)
    {
        add(value, value);
    }

    public void clear()
    {
        ranges.clear();
    }

    public String toString()
    {
        return ranges.toString();
    }

    public static final void main(String[] args)
    {
        RangeSet ranges = new RangeSet();
        ranges.add(5, 10);
        System.out.println(ranges);
        ranges.add(15, 20);
        System.out.println(ranges);
        ranges.add(23, 25);
        System.out.println(ranges);
        ranges.add(12, 14);
        System.out.println(ranges);
        ranges.add(0, 1);
        System.out.println(ranges);
        ranges.add(3, 11);
        System.out.println(ranges);
    }

}
