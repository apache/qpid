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
 */
package org.apache.qpid.server.model.testmodels.lifecycle;

import java.util.Collections;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.test.utils.QpidTestCase;

public class AbstractConfiguredObjectTest extends QpidTestCase
{

    public void testOpeningResultsInErroredStateWhenResolutionFails() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnPostResolve(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setThrowExceptionOnPostResolve(false);
        object.setAttributes(Collections.<String, Object>singletonMap(Port.DESIRED_STATE, State.ACTIVE));
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testOpeningInERROREDStateAfterFailedOpenOnDesiredStateChangeToActive() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setThrowExceptionOnOpen(false);
        object.setAttributes(Collections.<String, Object>singletonMap(Port.DESIRED_STATE, State.ACTIVE));
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testOpeningInERROREDStateAfterFailedOpenOnStart() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setThrowExceptionOnOpen(false);
        object.start();
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testDeletionERROREDStateAfterFailedOpenOnDelete() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.delete();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }

    public void testDeletionInERROREDStateAfterFailedOpenOnDesiredStateChangeToDelete() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setAttributes(Collections.<String, Object>singletonMap(Port.DESIRED_STATE, State.DELETED));
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }


    public void testCreationWithExceptionThrownFromValidationOnCreate() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnValidationOnCreate(true);
        try
        {
            object.create();
            fail("IllegalConfigurationException is expected to be thrown");
        }
        catch(IllegalConfigurationException e)
        {
            //pass
        }
        assertFalse("Unexpected opened", object.isOpened());
    }

    public void testCreationWithoutExceptionThrownFromValidationOnCreate() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnValidationOnCreate(false);
        object.create();
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testCreationWithExceptionThrownFromOnOpen() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        try
        {
            object.create();
            fail("Exception should have been re-thrown");
        }
        catch (RuntimeException re)
        {
            // pass
        }

        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }

    public void testCreationWithExceptionThrownFromOnCreate() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnCreate(true);
        try
        {
            object.create();
            fail("Exception should have been re-thrown");
        }
        catch (RuntimeException re)
        {
            // pass
        }

        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }

    public void testUnresolvedChildInERROREDStateIsNotValidatedOrOpenedOrAttainedDesiredStateOnParentOpen() throws Exception
    {
        TestConfiguredObject parent = new TestConfiguredObject("parent");
        TestConfiguredObject child1 = new TestConfiguredObject("child1", parent, parent.getTaskExecutor());
        child1.registerWithParents();
        TestConfiguredObject child2 = new TestConfiguredObject("child2", parent, parent.getTaskExecutor());
        child2.registerWithParents();

        child1.setThrowExceptionOnPostResolve(true);

        parent.open();

        assertTrue("Parent should be resolved", parent.isResolved());
        assertTrue("Parent should be validated", parent.isValidated());
        assertTrue("Parent should be opened", parent.isOpened());
        assertEquals("Unexpected parent state", State.ACTIVE, parent.getState());

        assertTrue("Child2 should be resolved", child2.isResolved());
        assertTrue("Child2 should be validated", child2.isValidated());
        assertTrue("Child2 should be opened", child2.isOpened());
        assertEquals("Unexpected child2 state", State.ACTIVE, child2.getState());

        assertFalse("Child2 should not be resolved", child1.isResolved());
        assertFalse("Child1 should not be validated", child1.isValidated());
        assertFalse("Child1 should not be opened", child1.isOpened());
        assertEquals("Unexpected child1 state", State.ERRORED, child1.getState());
    }

    public void testUnvalidatedChildInERROREDStateIsNotOpenedOrAttainedDesiredStateOnParentOpen() throws Exception
    {
        TestConfiguredObject parent = new TestConfiguredObject("parent");
        TestConfiguredObject child1 = new TestConfiguredObject("child1", parent, parent.getTaskExecutor());
        child1.registerWithParents();
        TestConfiguredObject child2 = new TestConfiguredObject("child2", parent, parent.getTaskExecutor());
        child2.registerWithParents();

        child1.setThrowExceptionOnValidate(true);

        parent.open();

        assertTrue("Parent should be resolved", parent.isResolved());
        assertTrue("Parent should be validated", parent.isValidated());
        assertTrue("Parent should be opened", parent.isOpened());
        assertEquals("Unexpected parent state", State.ACTIVE, parent.getState());

        assertTrue("Child2 should be resolved", child2.isResolved());
        assertTrue("Child2 should be validated", child2.isValidated());
        assertTrue("Child2 should be opened", child2.isOpened());
        assertEquals("Unexpected child2 state", State.ACTIVE, child2.getState());

        assertTrue("Child1 should be resolved", child1.isResolved());
        assertFalse("Child1 should not be validated", child1.isValidated());
        assertFalse("Child1 should not be opened", child1.isOpened());
        assertEquals("Unexpected child1 state", State.ERRORED, child1.getState());
    }

}
