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
package org.apache.qpid.server.queue;


import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.test.utils.QpidTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConsumerListTest extends QpidTestCase
{
    private QueueConsumerList _subList;
    private QueueConsumer _sub1;
    private QueueConsumer _sub2;
    private QueueConsumer _sub3;
    private QueueConsumerList.ConsumerNode _node;

    protected void setUp()
    {
        _subList = new QueueConsumerList();

        _sub1 = newMockConsumer();
        _sub2 = newMockConsumer();
        _sub3 = newMockConsumer();

        _subList.add(_sub1);
        _subList.add(_sub2);
        _subList.add(_sub3);

        _node = _subList.getHead();
    }


    private QueueConsumer newMockConsumer()
    {
        QueueConsumer consumer = mock(QueueConsumer.class);
        MessageInstance.ConsumerAcquiredState owningState = new QueueEntryImpl.ConsumerAcquiredState(consumer);
        when(consumer.getOwningState()).thenReturn(owningState);

        return consumer;
    }

    /**
     * Test that if the first (non-head) node in the list is deleted (but is still present),
     * it is not returned when searching through the list for the next viable node, and the
     * subsequent viable node is returned instead.
     */
    public void testFindNextSkipsFirstDeletedNode()
    {
        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub1).delete());

        assertNotNull("Returned node should not be null", _node = _node.findNext());
        assertEquals("Should have returned node for 2nd consumer", _sub2, _node.getConsumer());

        assertNotNull("Returned node should not be null", _node = _node.findNext());
        assertEquals("Should have returned node for 3rd consumer", _sub3, _node.getConsumer());
    }

    /**
     * Test that if a central node in the list is deleted (but is still present),
     * it is not returned when searching through the list for the next viable node,
     * and the subsequent viable node is returned instead.
     */
    public void testFindNextSkipsCentralDeletedNode()
    {
        assertNotNull("Returned node should not be null", _node = _node.findNext());

        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub2).delete());

        assertNotNull("Returned node should not be null", _node = _node.findNext());
        assertEquals("Should have returned node for 3rd consumer", _sub3, _node.getConsumer());
    }

    /**
     * Test that if the last node in the list is deleted (but is still present),
     * it is not returned when searching through the list for the next viable node,
     * and null is returned instead.
     */
    public void testFindNextSkipsLastDeletedNode()
    {
        assertNotNull("Returned node should not be null", _node = _node.findNext());
        assertEquals("Should have returned node for 1st consumer", _sub1, _node.getConsumer());

        assertNotNull("Returned node should not be null", _node = _node.findNext());
        assertEquals("Should have returned node for 2nd consumer", _sub2, _node.getConsumer());

        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub3).delete());

        assertNull("Returned node should be null", _node = _node.findNext());
    }

    /**
     * Test that if multiple nodes in the list are deleted (but still present), they
     * are not returned when searching through the list for the next viable node,
     * and the subsequent viable node is returned instead.
     */
    public void testFindNextSkipsMultipleDeletedNode()
    {
        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub1).delete());
        assertTrue("Deleting consumer node should have succeeded",
                getNodeForConsumer(_subList, _sub2).delete());

        assertNotNull("Returned node should not be null", _node = _node.findNext());
        assertEquals("Should have returned node for 3rd consumer", _sub3, _node.getConsumer());
    }

    /**
     * Test that if a node in the list is marked 'deleted' it is still present in the list
     * until actually removed. counter-test to verify above testing of getNext() method.
     */
    public void testDeletedNodeStillPresent()
    {
        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub1).delete());

        assertNotNull("Node marked deleted should still be present", getNodeForConsumer(_subList, _sub1));
        assertEquals("All 3 nodes are still expected to be present", 3, countNodes(_subList));
    }

    /**
     * Traverses the list nodes in a non-mutating fashion, returning the first node which matches the given
     * Consumer, or null if none is found.
     */
    private QueueConsumerList.ConsumerNode getNodeForConsumer(final QueueConsumerList list, final Consumer sub)
    {
        QueueConsumerList.ConsumerNode node = list.getHead();
        while (node != null && node.getConsumer() != sub)
        {
            node = node.nextNode();
        }

        return node;
    }

    /**
     * Counts the number of (non-head) nodes in the list.
     */
    private int countNodes(final QueueConsumerList list)
    {
        QueueConsumerList.ConsumerNode node = list.getHead();
        int count;
        for(count = -1; node != null; count++)
        {
            node = node.nextNode();
        }

        return count;
    }

    /**
     * Tests that the head is returned as expected, and isn't the node for the first consumer.
     */
    public void testGetHead()
    {
        assertNotNull("List head should be non null", _node);
        assertNotSame("Head should not be node for first consumer",
                _node, getNodeForConsumer(_subList, _sub1));
    }

    /**
     * Tests that the size is returned correctly in the face of additions and removals.
     */
    public void testGetSize()
    {
        QueueConsumerList subList = new QueueConsumerList();

        assertEquals("Unexpected size result", 0, subList.size());

        QueueConsumer sub1 = newMockConsumer();
        QueueConsumer sub2 = newMockConsumer();
        QueueConsumer sub3 = newMockConsumer();

        subList.add(sub1);
        assertEquals("Unexpected size result", 1, subList.size());

        subList.add(sub2);
        assertEquals("Unexpected size result", 2, subList.size());

        subList.add(sub3);
        assertEquals("Unexpected size result", 3, subList.size());

        assertTrue("Removing consumer from list should have succeeded", subList.remove(sub1));
        assertEquals("Unexpected size result", 2, subList.size());

        assertTrue("Removing consumer from list should have succeeded", subList.remove(sub2));
        assertEquals("Unexpected size result", 1, subList.size());

        assertTrue("Removing consumer from list should have succeeded", subList.remove(sub3));
        assertEquals("Unexpected size result", 0, subList.size());
    }

    /**
     * Test that if the first (non-head) node in the list is removed it is no longer
     * present in the node structure of the list at all.
     */
    public void testRemoveFirstNode()
    {
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub1));
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub1));
        assertNull("Should not have been a node present for the removed consumer",
                   getNodeForConsumer(_subList, _sub1));
        assertEquals("Unexpected number of nodes", 2, countNodes(_subList));
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub2));
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub3));
    }

    /**
     * Test that if a central node in the list is removed it is no longer
     * present in the node structure of the list at all.
     */
    public void testRemoveCentralNode()
    {
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub2));
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub2));
        assertNull("Should not have been a node present for the removed consumer",
                   getNodeForConsumer(_subList, _sub2));
        assertEquals("Unexpected number of nodes", 2, countNodes(_subList));
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub1));
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub3));
    }

    /**
     * Test that if the consumer contained in the last node of the list is removed
     * it is no longer present in the node structure of the list at all. However,
     * as the last node in the structure can't actually be removed a dummy will instead
     * be present.
     */
    public void testRemoveLastNode()
    {
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub3));
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub3));
        assertNull("Should not have been a node present for the removed consumer",
                   getNodeForConsumer(_subList, _sub3));

        //We actually expect 3 nodes to remain this time, because the last node cant be removed for thread safety reasons,
        //however a dummy final node can be used as substitute to allow removal of the consumer node.
        assertEquals("Unexpected number of nodes", 2 + 1, countNodes(_subList));
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub1));
        assertNotNull("Should have been a node present for the consumer", getNodeForConsumer(_subList, _sub2));
    }

    /**
     * Test that if the consumer not contained in the list is requested to be removed
     * that the removal fails
     */
    public void testRemoveNonexistentNode()
    {
        QueueConsumer sub4 = newMockConsumer();
        assertNull("Should not have been a node present for the consumer", getNodeForConsumer(_subList, sub4));
        assertFalse("Removing consumer node should not have succeeded", _subList.remove(sub4));
        assertEquals("Unexpected number of nodes", 3, countNodes(_subList));
    }

    /**
     * Test that if a consumer node which occurs later in the main list than the marked node is
     * removed from the list after the marked node is also removed, then the marker node doesn't
     * serve to retain the subsequent nodes in the list structure (and thus memory) despite their
     * removal.
     */
    public void testDeletedMarkedNodeDoesntLeakSubsequentlyDeletedNodes()
    {
        //get the nodes out the list for the 1st and 3rd consumers
        QueueConsumerList.ConsumerNode sub1Node = getNodeForConsumer(_subList, _sub1);
        assertNotNull("Should have been a node present for the consumer", sub1Node);
        QueueConsumerList.ConsumerNode sub3Node = getNodeForConsumer(_subList, _sub3);
        assertNotNull("Should have been a node present for the consumer", sub3Node);

        //mark the first consumer node
        assertTrue("should have succeeded in updating the marked node",
                   _subList.updateMarkedNode(_subList.getMarkedNode(), sub1Node));

        //remove the 1st consumer from the list
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub1));
        //verify the 1st consumer is no longer the marker node (replaced by a dummy), or in the main list structure
        assertNotSame("Unexpected marker node", sub1Node, _subList.getMarkedNode());
        assertNull("Should not have been a node present in the list structure for the marked-but-removed sub1 node",
                getNodeForConsumer(_subList, _sub1));

        //remove the 2nd consumer from the list
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub2));

        //verify the marker node isn't leaking subsequently removed nodes, by ensuring the very next node
        //in its list structure is now the 3rd consumer (since the 2nd was removed too)
        assertEquals("Unexpected next node", sub3Node, _subList.getMarkedNode().nextNode());

        //remove the 3rd and final/tail consumer
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub3));

        //verify the marker node isn't leaking subsequently removed nodes, by ensuring the very next node
        //in its list structure is now the dummy tail (since the 3rd consumer was removed, and a dummy
        //tail was inserted) and NOT the 3rd sub node.
        assertNotSame("Unexpected next node", sub3Node, _subList.getMarkedNode().nextNode());
        assertTrue("Unexpected next node", _subList.getMarkedNode().nextNode().isDeleted());
        assertNull("Next non-deleted node from the marker should now be the list end, i.e. null", _subList.getMarkedNode().findNext());
    }

    /**
     * Test that the marked node 'findNext' behaviour is as expected after a consumer is added
     * to the list following the tail consumer node being removed while it is the marked node.
     * That is, that the new consumers node is returned by getMarkedNode().findNext().
     */
    public void testMarkedNodeFindsNewConsumerAfterRemovingTailWhilstMarked()
    {
        //get the node out the list for the 3rd consumer
        QueueConsumerList.ConsumerNode sub3Node = getNodeForConsumer(_subList, _sub3);
        assertNotNull("Should have been a node present for the consumer", sub3Node);

        //mark the 3rd consumer node
        assertTrue("should have succeeded in updating the marked node",
                _subList.updateMarkedNode(_subList.getMarkedNode(), sub3Node));

        //verify calling findNext on the marked node returns null, i.e. the end of the list has been reached
        assertEquals("Unexpected node after marked node", null, _subList.getMarkedNode().findNext());

        //remove the 3rd(marked) consumer from the list
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub3));

        //add a new 4th consumer to the list
        QueueConsumer sub4 = newMockConsumer();
        _subList.add(sub4);

        //get the node out the list for the 4th consumer
        QueueConsumerList.ConsumerNode sub4Node = getNodeForConsumer(_subList, sub4);
        assertNotNull("Should have been a node present for the consumer", sub4Node);

        //verify the marked node (which is now a dummy substitute for the 3rd consumer) returns
        //the 4th consumers node as the next non-deleted node.
        assertEquals("Unexpected next node", sub4Node, _subList.getMarkedNode().findNext());
    }

    /**
     * Test that setting the marked node to null doesn't cause problems during remove operations
     */
    public void testRemoveWithNullMarkedNode()
    {
        //set the marker to null
        assertTrue("should have succeeded in updating the marked node",
                _subList.updateMarkedNode(_subList.getMarkedNode(), null));

        //remove the 1st consumer from the main list
        assertTrue("Removing consumer node should have succeeded", _subList.remove(_sub1));

        //verify the 1st consumer is no longer in the main list structure
        assertNull("Should not have been a node present in the main list structure for sub1",
                getNodeForConsumer(_subList, _sub1));
        assertEquals("Unexpected number of nodes", 2, countNodes(_subList));
    }

    /**
     * Tests that after the first (non-head) node of the list is marked deleted but has not
     * yet been removed, the iterator still skips it.
     */
    public void testIteratorSkipsFirstDeletedNode()
    {
        //'delete' but don't remove the node for the 1st consumer
        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub1).delete());
        assertNotNull("Should still have been a node present for the deleted consumer",
                getNodeForConsumer(_subList, _sub1));

        QueueConsumerList.ConsumerNodeIterator iter = _subList.iterator();

        //verify the iterator returns the 2nd consumers node
        assertTrue("Iterator should have been able to advance", iter.advance());
        assertEquals("Iterator returned unexpected ConsumerNode", _sub2, iter.getNode().getConsumer());

        //verify the iterator returns the 3rd consumers node and not the 2nd.
        assertTrue("Iterator should have been able to advance", iter.advance());
        assertEquals("Iterator returned unexpected ConsumerNode", _sub3, iter.getNode().getConsumer());
    }

    /**
     * Tests that after a central node of the list is marked deleted but has not yet been removed,
     * the iterator still skips it.
     */
    public void testIteratorSkipsCentralDeletedNode()
    {
        //'delete' but don't remove the node for the 2nd consumer
        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub2).delete());
        assertNotNull("Should still have been a node present for the deleted consumer",
                getNodeForConsumer(_subList, _sub2));

        QueueConsumerList.ConsumerNodeIterator iter = _subList.iterator();

        //verify the iterator returns the 1st consumers node
        assertTrue("Iterator should have been able to advance", iter.advance());
        assertEquals("Iterator returned unexpected ConsumerNode", _sub1, iter.getNode().getConsumer());

        //verify the iterator returns the 3rd consumers node and not the 2nd.
        assertTrue("Iterator should have been able to advance", iter.advance());
        assertEquals("Iterator returned unexpected ConsumerNode", _sub3, iter.getNode().getConsumer());
    }

    /**
     * Tests that after the last node of the list is marked deleted but has not yet been removed,
     * the iterator still skips it.
     */
    public void testIteratorSkipsDeletedFinalNode()
    {
        //'delete' but don't remove the node for the 3rd consumer
        assertTrue("Deleting consumer node should have succeeded",
                   getNodeForConsumer(_subList, _sub3).delete());
        assertNotNull("Should still have been a node present for the deleted 3rd consumer",
                getNodeForConsumer(_subList, _sub3));

        QueueConsumerList.ConsumerNodeIterator iter = _subList.iterator();

        //verify the iterator returns the 1st consumers node
        assertTrue("Iterator should have been able to advance", iter.advance());
        assertEquals("Iterator returned unexpected ConsumerNode", _sub1, iter.getNode().getConsumer());

        //verify the iterator returns the 2nd consumers node
        assertTrue("Iterator should have been able to advance", iter.advance());
        assertEquals("Iterator returned unexpected ConsumerNode", _sub2, iter.getNode().getConsumer());

        //verify the iterator can no longer advance and does not return a consumer node
        assertFalse("Iterator should not have been able to advance", iter.advance());
        assertEquals("Iterator returned unexpected ConsumerNode", null, iter.getNode());
    }
}
