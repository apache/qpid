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
package org.apache.qpid.disttest;

import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.test.utils.QpidTestCase;


public class VisitorTest extends QpidTestCase
{

    public void testStringVisited() throws Exception
    {
        Object argument = new String();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(argument);

        assertSame(argument, visitor._string);
    }

    public void testCommandVisited() throws Exception
    {
        Object argument = new TestCommand();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(argument);

        assertSame(argument, visitor._testCommand);
    }

    public void testNoVisitIntegerImplementatiom() throws Exception
    {
        Integer argument = Integer.valueOf(1);

        TestVisitor visitor = new TestVisitor();

        try
        {
            visitor.visit(argument);
            fail("Exception not thrown");
        }
        catch (DistributedTestException e)
        {
            assertNotNull(e.getCause());
            assertEquals(NoSuchMethodException.class, e.getCause().getClass());
        }
    }

    class TestVisitor extends Visitor
    {
        String _string = null;
        TestCommand _testCommand = null;

        public void visit(String string)
        {
            _string = string;
        }

        public void visit(TestCommand command)
        {
            _testCommand = command;
        }
    }

    class TestCommand extends Command
    {

        public TestCommand()
        {
            super(null);
        }
    }
}
