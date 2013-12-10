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

import junit.framework.TestCase;

import static org.apache.qpid.util.Serial.COMPARATOR;
import static org.apache.qpid.util.Serial.eq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * RangeSetTest
 *
 */

public class RangeSetTest extends TestCase
{

    private void check(RangeSet ranges)
    {
        List<Integer> posts = new ArrayList<Integer>();
        for (Range range : ranges)
        {
            posts.add(range.getLower());
            posts.add(range.getUpper());
        }

        List<Integer> sorted = new ArrayList<Integer>(posts);
        Collections.sort(sorted, COMPARATOR);

        assertEquals(posts, sorted);

        int idx = 1;
        while (idx + 1 < posts.size())
        {
            assertTrue(!eq(posts.get(idx) + 1, posts.get(idx+1)));
            idx += 2;
        }
    }

    public void test1()
    {
        RangeSet ranges = RangeSetFactory.createRangeSet();
        ranges.add(5, 10);
        check(ranges);
        ranges.add(15, 20);
        check(ranges);
        ranges.add(23, 25);
        check(ranges);
        ranges.add(12, 14);
        check(ranges);
        ranges.add(0, 1);
        check(ranges);
        ranges.add(3, 11);
        check(ranges);
    }

    public void test2()
    {
        RangeSet rs = RangeSetFactory.createRangeSet();
        check(rs);

        rs.add(1);
        assertTrue(rs.includes(1));
        assertTrue(!rs.includes(2));
        assertTrue(!rs.includes(0));
        check(rs);

        rs.add(2);
        assertTrue(!rs.includes(0));
        assertTrue(rs.includes(1));
        assertTrue(rs.includes(2));
        assertTrue(!rs.includes(3));
        check(rs);

        rs.add(0);

        assertTrue(!rs.includes(-1));
        assertTrue(rs.includes(0));
        assertTrue(rs.includes(1));
        assertTrue(rs.includes(2));
        assertTrue(!rs.includes(3));
        check(rs);

        rs.add(37);

        assertTrue(!rs.includes(-1));
        assertTrue(rs.includes(0));
        assertTrue(rs.includes(1));
        assertTrue(rs.includes(2));
        assertTrue(!rs.includes(3));
        assertTrue(!rs.includes(36));
        assertTrue(rs.includes(37));
        assertTrue(!rs.includes(38));
        check(rs);

        rs.add(-1);
        check(rs);

        rs.add(-3);
        check(rs);

        rs.add(1, 20);
        assertTrue(!rs.includes(21));
        assertTrue(rs.includes(20));
        check(rs);
    }

    public void testAddSelf()
    {
        RangeSet a = RangeSetFactory.createRangeSet();
        a.add(0, 8);
        check(a);
        a.add(0, 8);
        check(a);
        assertEquals(a.size(), 1);
        Range range = a.iterator().next();
        assertEquals(range.getLower(), 0);
        assertEquals(range.getUpper(), 8);
    }

    public void testIntersect1()
    {
        Range a = Range.newInstance(0, 10);
        Range b = Range.newInstance(9, 20);
        Range i1 = a.intersect(b);
        Range i2 = b.intersect(a);
        assertEquals(i1.getUpper(), 10);
        assertEquals(i2.getUpper(), 10);
        assertEquals(i1.getLower(), 9);
        assertEquals(i2.getLower(), 9);
    }

    public void testIntersect2()
    {
        Range a = Range.newInstance(0, 10);
        Range b = Range.newInstance(11, 20);
        assertNull(a.intersect(b));
        assertNull(b.intersect(a));
    }

    public void testIntersect3()
    {
        Range a = Range.newInstance(0, 10);
        Range b = Range.newInstance(3, 5);
        Range i1 = a.intersect(b);
        Range i2 = b.intersect(a);
        assertEquals(i1.getUpper(), 5);
        assertEquals(i2.getUpper(), 5);
        assertEquals(i1.getLower(), 3);
        assertEquals(i2.getLower(), 3);
    }

    public void testSubtract1()
    {
        Range a = Range.newInstance(0, 10);
        assertTrue(a.subtract(a).isEmpty());
    }

    public void testSubtract2()
    {
        Range a = Range.newInstance(0, 10);
        Range b = Range.newInstance(20, 30);
        List<Range> ranges = a.subtract(b);
        assertEquals(ranges.size(), 1);
        Range d = ranges.get(0);
        assertEquals(d.getLower(), a.getLower());
        assertEquals(d.getUpper(), a.getUpper());
    }

    public void testSubtract3()
    {
        Range a = Range.newInstance(20, 30);
        Range b = Range.newInstance(0, 10);
        List<Range> ranges = a.subtract(b);
        assertEquals(ranges.size(), 1);
        Range d = ranges.get(0);
        assertEquals(d.getLower(), a.getLower());
        assertEquals(d.getUpper(), a.getUpper());
    }

    public void testSubtract4()
    {
        Range a = Range.newInstance(0, 10);
        Range b = Range.newInstance(3, 5);
        List<Range> ranges = a.subtract(b);
        assertEquals(ranges.size(), 2);
        Range low = ranges.get(0);
        Range high = ranges.get(1);
        assertEquals(low.getLower(), 0);
        assertEquals(low.getUpper(), 2);
        assertEquals(high.getLower(), 6);
        assertEquals(high.getUpper(), 10);
    }

    public void testSubtract5()
    {
        Range a = Range.newInstance(0, 10);
        Range b = Range.newInstance(3, 20);
        List<Range> ranges = a.subtract(b);
        assertEquals(ranges.size(), 1);
        Range d = ranges.get(0);
        assertEquals(d.getLower(), 0);
        assertEquals(d.getUpper(), 2);
    }

    public void testSubtract6()
    {
        Range a = Range.newInstance(0, 10);
        Range b = Range.newInstance(-10, 5);
        List<Range> ranges = a.subtract(b);
        assertEquals(ranges.size(), 1);
        Range d = ranges.get(0);
        assertEquals(d.getLower(), 6);
        assertEquals(d.getUpper(), 10);
    }

    public void testSetSubtract1()
    {
        final RangeSet orig = createRangeSet(0, 10) ;
        final RangeSet update = createRangeSet(3, 15) ;
        orig.subtract(update) ;
        checkRange(orig, 0, 2) ;
    }

    public void testSetSubtract2()
    {
        final RangeSet orig = createRangeSet(0, 10) ;
        final RangeSet update = createRangeSet(3, 10) ;
        orig.subtract(update) ;
        checkRange(orig, 0, 2) ;
    }

    public void testSetSubtract3()
    {
        final RangeSet orig = createRangeSet(0, 10) ;
        final RangeSet update = createRangeSet(3, 4) ;
        orig.subtract(update) ;
        checkRange(orig, 0, 2, 5, 10) ;
    }

    public void testSetSubtract4()
    {
        final RangeSet orig = createRangeSet(3, 15) ;
        final RangeSet update = createRangeSet(0, 10) ;
        orig.subtract(update) ;
        checkRange(orig, 11, 15) ;
    }

    public void testSetSubtract5()
    {
        final RangeSet orig = createRangeSet(3, 10) ;
        final RangeSet update = createRangeSet(0, 10) ;
        orig.subtract(update) ;
        checkRange(orig) ;
    }

    public void testSetSubtract6()
    {
        final RangeSet orig = createRangeSet(3, 10) ;
        final RangeSet update = createRangeSet(0, 15) ;
        orig.subtract(update) ;
        checkRange(orig) ;
    }

    public void testSetSubtract7()
    {
        final RangeSet orig = createRangeSet(0, 10) ;
        final RangeSet update = createRangeSet(0, 15) ;
        orig.subtract(update) ;
        checkRange(orig) ;
    }

    public void testSetSubtract8()
    {
        final RangeSet orig = createRangeSet(0, 15) ;
        final RangeSet update = createRangeSet(0, 10) ;
        orig.subtract(update) ;
        checkRange(orig, 11, 15) ;
    }

    public void testSetSubtract9()
    {
        final RangeSet orig = createRangeSet(0, 15, 20, 30) ;
        final RangeSet update = createRangeSet(2, 3, 5, 6, 8, 9, 22, 23, 27, 28) ;
        orig.subtract(update) ;
        checkRange(orig, 0, 1, 4, 4, 7, 7, 10, 15, 20, 21, 24, 26, 29, 30) ;
    }

    public void testSetSubtract10()
    {
        final RangeSet orig = createRangeSet(0, 15, 20, 30) ;
        final RangeSet update = createRangeSet(0, 2, 4, 6, 10, 22, 24, 24, 27, 30) ;
        orig.subtract(update) ;
        checkRange(orig, 3, 3, 7, 9, 23, 23, 25, 26) ;
    }

    public void testSetSubtract11()
    {
        final RangeSet orig = createRangeSet(0, 2, 4, 6, 10, 22, 24, 24, 27, 30) ;
        final RangeSet update = createRangeSet(0, 2, 4, 6, 10, 22, 24, 24, 27, 30) ;
        orig.subtract(update) ;
        checkRange(orig) ;
    }
    
    private RangeSet createRangeSet(int ... bounds)
    {
        RangeSet set = RangeSetFactory.createRangeSet();
        final int length = (bounds == null ? 0 : bounds.length) ;
        int count = 0 ;
        while(count < length)
        {
            set.add(bounds[count++], bounds[count++]) ;
        }
        return set ;
    }
    
    private void checkRange(final RangeSet rangeSet, int ... bounds)
    {
        final int length = (bounds == null ? 0 : bounds.length) ;
        assertEquals("Range count", length/2, rangeSet.size()) ;
        final Iterator<Range> iter = rangeSet.iterator() ;
        int count = 0 ;
        while(count < length)
        {
            final Range range = iter.next() ;
            assertEquals("Range lower", bounds[count++], range.getLower()) ;
            assertEquals("Range upper", bounds[count++], range.getUpper()) ;
        }
    }
}
