/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.cassandra.db;

import java.nio.ByteBuffer;
import java.util.*;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.cassandra.db.marshal.IntegerType;
import org.apache.cassandra.utils.ByteBufferUtil;

public class RangeTombstoneListTest
{
    private static final Comparator<ByteBuffer> cmp = IntegerType.instance;

    @Test
    public void sortedAdditionTest()
    {
        sortedAdditionTest(0);
        sortedAdditionTest(10);
    }

    private void sortedAdditionTest(int initialCapacity)
    {
        RangeTombstoneList l = new RangeTombstoneList(cmp, initialCapacity);
        RangeTombstone rt1 = rt(1, 5, 3);
        RangeTombstone rt2 = rt(7, 10, 2);
        RangeTombstone rt3 = rt(10, 13, 1);

        l.add(rt1);
        l.add(rt2);
        l.add(rt3);

        Iterator<RangeTombstone> iter = l.iterator();
        assertRT(rt1, iter.next());
        assertRT(rt2, iter.next());
        assertRT(rt3, iter.next());

        assert !iter.hasNext();
    }

    @Test
    public void nonSortedAdditionTest()
    {
        nonSortedAdditionTest(0);
        nonSortedAdditionTest(10);
    }

    private void nonSortedAdditionTest(int initialCapacity)
    {
        RangeTombstoneList l = new RangeTombstoneList(cmp, initialCapacity);
        RangeTombstone rt1 = rt(1, 5, 3);
        RangeTombstone rt2 = rt(7, 10, 2);
        RangeTombstone rt3 = rt(10, 13, 1);

        l.add(rt2);
        l.add(rt1);
        l.add(rt3);

        Iterator<RangeTombstone> iter = l.iterator();
        assertRT(rt1, iter.next());
        assertRT(rt2, iter.next());
        assertRT(rt3, iter.next());

        assert !iter.hasNext();
    }

    @Test
    public void overlappingAdditionTest()
    {
        overlappingAdditionTest(0);
        overlappingAdditionTest(10);
    }

    private void overlappingAdditionTest(int initialCapacity)
    {
        RangeTombstoneList l = new RangeTombstoneList(cmp, initialCapacity);

        l.add(rt(4, 10, 3));
        l.add(rt(1, 7, 2));
        l.add(rt(8, 13, 4));
        l.add(rt(0, 15, 1));

        Iterator<RangeTombstone> iter = l.iterator();
        assertRT(rt(0, 1, 1), iter.next());
        assertRT(rt(1, 4, 2), iter.next());
        assertRT(rt(4, 8, 3), iter.next());
        assertRT(rt(8, 10, 4), iter.next());
        assertRT(rt(10, 13, 4), iter.next());
        assertRT(rt(13, 15, 1), iter.next());
        assert !iter.hasNext();

        RangeTombstoneList l2 = new RangeTombstoneList(cmp, initialCapacity);
        l.add(rt(4, 10, 12L));
        l.add(rt(0, 8, 25L));

        assertEquals(25L, l.search(b(8)).markedForDeleteAt);
    }

    @Test
    public void overlappingSearchTest()
    {
    }

    @Test
    public void simpleOverlapTest()
    {
        RangeTombstoneList l1 = new RangeTombstoneList(cmp, 0);
        l1.add(rt(0, 10, 3));
        l1.add(rt(3, 7, 5));

        Iterator<RangeTombstone> iter1 = l1.iterator();
        assertRT(rt(0, 3, 3), iter1.next());
        assertRT(rt(3, 7, 5), iter1.next());
        assertRT(rt(7, 10, 3), iter1.next());
        assert !iter1.hasNext();

        RangeTombstoneList l2 = new RangeTombstoneList(cmp, 0);
        l2.add(rt(0, 10, 3));
        l2.add(rt(3, 7, 2));

        Iterator<RangeTombstone> iter2 = l2.iterator();
        assertRT(rt(0, 10, 3), iter2.next());
        assert !iter2.hasNext();
    }

    @Test
    public void searchTest()
    {
        RangeTombstoneList l = new RangeTombstoneList(cmp, 0);
        l.add(rt(0, 4, 5));
        l.add(rt(4, 6, 2));
        l.add(rt(9, 12, 1));
        l.add(rt(14, 15, 3));
        l.add(rt(15, 17, 6));

        assertEquals(null, l.search(b(-1)));

        assertEquals(5, l.search(b(0)).markedForDeleteAt);
        assertEquals(5, l.search(b(3)).markedForDeleteAt);
        assertEquals(5, l.search(b(4)).markedForDeleteAt);

        assertEquals(2, l.search(b(5)).markedForDeleteAt);

        assertEquals(null, l.search(b(7)));

        assertEquals(3, l.search(b(14)).markedForDeleteAt);

        assertEquals(6, l.search(b(15)).markedForDeleteAt);
        assertEquals(null, l.search(b(18)));
    }

    @Test
    public void addAllTest()
    {
        //addAllTest(false);
        addAllTest(true);
    }

    private void addAllTest(boolean doMerge)
    {
        RangeTombstoneList l1 = new RangeTombstoneList(cmp, 0);
        l1.add(rt(0, 4, 5));
        l1.add(rt(6, 10, 2));
        l1.add(rt(15, 17, 1));

        RangeTombstoneList l2 = new RangeTombstoneList(cmp, 0);
        l2.add(rt(3, 5, 7));
        l2.add(rt(7, 8, 3));
        l2.add(rt(8, 12, 1));
        l2.add(rt(14, 17, 4));

        l1.addAll(l2);

        Iterator<RangeTombstone> iter = l1.iterator();
        assertRT(rt(0, 3, 5), iter.next());
        assertRT(rt(3, 4, 7), iter.next());
        assertRT(rt(4, 5, 7), iter.next());
        assertRT(rt(6, 7, 2), iter.next());
        assertRT(rt(7, 8, 3), iter.next());
        assertRT(rt(8, 10, 2), iter.next());
        assertRT(rt(10, 12, 1), iter.next());
        assertRT(rt(14, 15, 4), iter.next());
        assertRT(rt(15, 17, 4), iter.next());

        assert !iter.hasNext();
    }

    @Test
    public void addAllSequentialTest()
    {
        RangeTombstoneList l1 = new RangeTombstoneList(cmp, 0);
        l1.add(rt(3, 5, 2));

        RangeTombstoneList l2 = new RangeTombstoneList(cmp, 0);
        l2.add(rt(5, 7, 7));

        l1.addAll(l2);

        Iterator<RangeTombstone> iter = l1.iterator();
        assertRT(rt(3, 5, 2), iter.next());
        assertRT(rt(5, 7, 7), iter.next());

        assert !iter.hasNext();
    }

    @Test
    public void addAllIncludedTest()
    {
        RangeTombstoneList l1 = new RangeTombstoneList(cmp, 0);
        l1.add(rt(3, 10, 5));

        RangeTombstoneList l2 = new RangeTombstoneList(cmp, 0);
        l2.add(rt(4, 5, 2));
        l2.add(rt(5, 7, 3));
        l2.add(rt(7, 9, 4));

        l1.addAll(l2);

        Iterator<RangeTombstone> iter = l1.iterator();
        assertRT(rt(3, 10, 5), iter.next());

        assert !iter.hasNext();
    }

    @Test
    public void purgetTest()
    {
        RangeTombstoneList l = new RangeTombstoneList(cmp, 0);
        l.add(rt(0, 4, 5, 110));
        l.add(rt(4, 6, 2, 98));
        l.add(rt(9, 12, 1, 200));
        l.add(rt(14, 15, 3, 3));
        l.add(rt(15, 17, 6, 45));

        l.purge(100);

        Iterator<RangeTombstone> iter = l.iterator();
        assertRT(rt(0, 4, 5, 110), iter.next());
        assertRT(rt(9, 12, 1, 200), iter.next());

        assert !iter.hasNext();
    }

    @Test
    public void minMaxTest()
    {
        RangeTombstoneList l = new RangeTombstoneList(cmp, 0);
        l.add(rt(0, 4, 5, 110));
        l.add(rt(4, 6, 2, 98));
        l.add(rt(9, 12, 1, 200));
        l.add(rt(14, 15, 3, 3));
        l.add(rt(15, 17, 6, 45));

        assertEquals(1, l.minMarkedAt());
        assertEquals(6, l.maxMarkedAt());
    }

    private static void assertRT(RangeTombstone expected, RangeTombstone actual)
    {
        assertEquals(String.format("Expected %s but got %s", toString(expected), toString(actual)), expected, actual);
    }

    private static String toString(RangeTombstone rt)
    {
        return String.format("[%d, %d]@%d", i(rt.min), i(rt.max), rt.data.markedForDeleteAt);
    }

    private static ByteBuffer b(int i)
    {
        return ByteBufferUtil.bytes(i);
    }

    private static int i(ByteBuffer bb)
    {
        return ByteBufferUtil.toInt(bb);
    }

    private static RangeTombstone rt(int start, int end, long tstamp)
    {
        return rt(start, end, tstamp, 0);
    }

    private static RangeTombstone rt(int start, int end, long tstamp, int delTime)
    {
        return new RangeTombstone(b(start), b(end), tstamp, delTime);
    }
}
