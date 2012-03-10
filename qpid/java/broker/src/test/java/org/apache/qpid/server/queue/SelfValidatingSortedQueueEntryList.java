/*
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

import junit.framework.Assert;

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.SortedQueueEntryImpl.Colour;

/**
 * Test extension of SortedQueueEntryList that provides data structure validation tests.
 * @see SortedQueueEntryList
 */
public class SelfValidatingSortedQueueEntryList extends SortedQueueEntryList
{
    public SelfValidatingSortedQueueEntryList(AMQQueue queue, String propertyName)
    {
        super(queue, propertyName);
    }

    @Override /** Overridden to automatically check queue properties before and after. */
    public SortedQueueEntryImpl add(final ServerMessage message)
    {
        assertQueueProperties(); //before add
        final SortedQueueEntryImpl result = super.add(message);
        assertQueueProperties(); //after add
        return result;
    }

    @Override /** Overridden to automatically check queue properties before and after. */
    public void entryDeleted(SortedQueueEntryImpl entry)
    {
        assertQueueProperties(); //before delete
        super.entryDeleted(entry);
        assertQueueProperties(); //after delete
    }

    public void assertQueueProperties()
    {
        assertRootIsBlack();
        assertTreeIntegrity();
        assertChildrenOfRedAreBlack();
        assertLeavesSameBlackPath();
    }

    public void assertRootIsBlack()
    {
        if(!isNodeColour(getRoot(), Colour.BLACK))
        {
            Assert.fail("Root Not Black");
        }
    }

    public void assertTreeIntegrity()
    {
        assertTreeIntegrity(getRoot());
    }

    public void assertTreeIntegrity(final SortedQueueEntryImpl node)
    {
        if(node == null)
        {
            return;
        }
        if(node.getLeft() != null)
        {
            if(node.getLeft().getParent() == node)
            {
                assertTreeIntegrity(node.getLeft());
            }
            else
            {
                Assert.fail("Tree integrity compromised");
            }
        }
        if(node.getRight() != null)
        {
            if(node.getRight().getParent() == node)
            {
                assertTreeIntegrity(node.getRight());
            }
            else
            {
                Assert.fail("Tree integrity compromised");
            }

        }
    }

    public void assertLeavesSameBlackPath()
    {
        assertLeavesSameBlackPath(getRoot());
    }

    public int assertLeavesSameBlackPath(final SortedQueueEntryImpl node)
    {
        if(node == null)
        {
            return 1;
        }
        final int left = assertLeavesSameBlackPath(node.getLeft());
        final int right = assertLeavesSameBlackPath(node.getLeft());
        if(left == right)
        {
            return isNodeColour(node, Colour.BLACK) ? 1 + left : left;
        }
        else
        {
            Assert.fail("Unequal paths to leaves");
            return 1; //compiler
        }
    }

    public void assertChildrenOfRedAreBlack()
    {
        assertChildrenOfRedAreBlack(getRoot());
    }

    public void assertChildrenOfRedAreBlack(final SortedQueueEntryImpl node)
    {
        if(node == null)
        {
            return;
        }
        else if(node.getColour() == Colour.BLACK)
        {
            assertChildrenOfRedAreBlack(node.getLeft());
            assertChildrenOfRedAreBlack(node.getRight());
        }
        else
        {
            if(isNodeColour(node.getLeft(), Colour.BLACK)
                    && isNodeColour(node.getRight(), Colour.BLACK))
            {
                assertChildrenOfRedAreBlack(node.getLeft());
                assertChildrenOfRedAreBlack(node.getRight());
            }
            else
            {
                Assert.fail("Children of Red are not both black");
            }
        }
    }
}
