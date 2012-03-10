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

import static org.apache.qpid.util.Serial.gt;
import static org.apache.qpid.util.Serial.le;
import static org.apache.qpid.util.Serial.max;
import static org.apache.qpid.util.Serial.min;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * Range
 *
 * @author Rafael H. Schloming
 */

public abstract class Range implements RangeSet
{
    public static Range newInstance(int point)
    {
        return new PointImpl(point);
    }

    public static Range newInstance(int lower, int upper)
    {
        return lower == upper ? new PointImpl(lower) : new RangeImpl(lower, upper);
    }

    public abstract int getLower();

    public abstract int getUpper();

    public abstract boolean includes(int value);

    public abstract boolean includes(Range range);

    public abstract boolean intersects(Range range);

    public abstract boolean touches(Range range);

    public abstract Range span(Range range);

    public abstract List<Range> subtract(Range range);


    public Range intersect(Range range)
    {
        int l = max(getLower(), range.getLower());
        int r = min(getUpper(), range.getUpper());
        if (gt(l, r))
        {
            return null;
        }
        else
        {
            return newInstance(l, r);
        }
    }



    public int size()
    {
        return 1;
    }

    public Iterator<Range> iterator()
    {
        return new RangeIterator();
    }

    public Range getFirst()
    {
        return this;
    }

    public Range getLast()
    {
        return this;
    }

    public void add(Range range)
    {
        throw new UnsupportedOperationException();
    }

    public void add(int lower, int upper)
    {
        throw new UnsupportedOperationException();
    }

    public void add(int value)
    {
        throw new UnsupportedOperationException();
    }

    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    public void subtract(RangeSet rangeSet)
    {
        throw new UnsupportedOperationException();
    }

    public RangeSet copy()
    {
        RangeSet rangeSet = RangeSetFactory.createRangeSet();
        rangeSet.add(this);
        return rangeSet;
    }

    private static class PointImpl extends Range
    {
        private final int point;

        private PointImpl(int point)
        {
            this.point = point;
        }

        public int getLower()
        {
            return point;
        }

        public int getUpper()
        {
            return point;
        }

        public boolean includes(int value)
        {
            return value == point;
        }


        public boolean includes(Range range)
        {
            return range.getLower() == point && range.getUpper() == point;
        }

        public boolean intersects(Range range)
        {
            return range.includes(point);
        }

        public boolean touches(Range range)
        {
            return intersects(range) ||
                    includes(range.getUpper() + 1) || includes(range.getLower() - 1) ||
                    range.includes(point + 1) || range.includes(point - 1);
        }

        public Range span(Range range)
        {
            return newInstance(min(point, range.getLower()), max(point, range.getUpper()));
        }

        public List<Range> subtract(Range range)
        {
            if(range.includes(point))
            {
                return Collections.emptyList();
            }
            else
            {
                return Collections.singletonList((Range) this);
            }
        }

        public String toString()
        {
            return "[" + point + ", " + point + "]";
        }


    }

    private static class RangeImpl extends Range
    {
        private final int lower;
        private final int upper;

        private RangeImpl(int lower, int upper)
        {
            this.lower = lower;
            this.upper = upper;
        }

        public int getLower()
        {
            return lower;
        }

        public int getUpper()
        {
            return upper;
        }

        public boolean includes(int value)
        {
            return le(lower, value) && le(value, upper);
        }

        public boolean includes(Range range)
        {
            return includes(range.getLower()) && includes(range.getUpper());
        }

        public boolean intersects(Range range)
        {
            return (includes(range.getLower()) || includes(range.getUpper()) ||
                    range.includes(lower) || range.includes(upper));
        }

        public boolean touches(Range range)
        {
            return (intersects(range) ||
                    includes(range.getUpper() + 1) || includes(range.getLower() - 1) ||
                    range.includes(upper + 1) || range.includes(lower - 1));
        }

        public Range span(Range range)
        {
            return newInstance(min(lower, range.getLower()), max(upper, range.getUpper()));
        }

        public List<Range> subtract(Range range)
        {
            List<Range> result = new ArrayList<Range>();

            if (includes(range.getLower()) && le(lower, range.getLower() - 1))
            {
                result.add(newInstance(lower, range.getLower() - 1));
            }

            if (includes(range.getUpper()) && le(range.getUpper() + 1, upper))
            {
                result.add(newInstance(range.getUpper() + 1, upper));
            }

            if (result.isEmpty() && !range.includes(this))
            {
                result.add(this);
            }

            return result;
        }


        public String toString()
        {
            return "[" + lower + ", " + upper + "]";
        }
    }


    private class RangeIterator implements Iterator<Range>
    {
        private boolean atFirst = true;

        public boolean hasNext()
        {
            return atFirst;
        }

        public Range next()
        {

            Range range = atFirst ? Range.this : null;
            atFirst = false;
            return range;
        }


        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
