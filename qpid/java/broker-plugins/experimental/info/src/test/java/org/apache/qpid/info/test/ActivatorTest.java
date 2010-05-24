package org.apache.qpid.info.test;

import junit.framework.TestCase;
import org.apache.qpid.info.Activator;

/*
 * This test verifies whether the activator for the info service is starting Ok. 
 */
public class ActivatorTest extends TestCase
{
    private Activator activator;

    protected void setUp() throws Exception
    {
        super.setUp();
        activator = new Activator();
        activator.start(null);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        activator = null;
    }

    public void testStart()
    {
        assertNotNull(activator);
    }

    public void testGetBundleContext()
    {
        assertEquals(activator.getBundleContext(), null);
    }

}
