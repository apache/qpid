package org.apache.qpid.server.security.access.plugins;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.security.AccessControl;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class DefaultAccessControlFactoryTest extends QpidTestCase
{
    public void testCreateInstanceWhenAclFileIsNotPresent()
    {
        DefaultAccessControlFactory factory = new DefaultAccessControlFactory();
        Map<String, Object> attributes = new HashMap<String, Object>();
        AccessControl acl = factory.createInstance(attributes);
        assertNull("ACL was created without a configuration file", acl);
    }

    public void testCreateInstanceWhenAclFileIsSpecified()
    {
        File aclFile = TestFileUtils.createTempFile(this, ".acl", "ACL ALLOW all all");
        DefaultAccessControlFactory factory = new DefaultAccessControlFactory();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(DefaultAccessControlFactory.ATTRIBUTE_ACL_FILE, aclFile.getAbsolutePath());
        AccessControl acl = factory.createInstance(attributes);

        assertNotNull("ACL was not created from acl file: " + aclFile.getAbsolutePath(), acl);
    }

    public void testCreateInstanceWhenAclFileIsSpecifiedButDoesNotExist()
    {
        File aclFile = new File(TMP_FOLDER, "my-non-existing-acl-" + System.currentTimeMillis());
        assertFalse("ACL file " + aclFile.getAbsolutePath() + " actually exists but should not", aclFile.exists());
        DefaultAccessControlFactory factory = new DefaultAccessControlFactory();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(DefaultAccessControlFactory.ATTRIBUTE_ACL_FILE, aclFile.getAbsolutePath());
        try
        {
            factory.createInstance(attributes);
            fail("It should not be possible to create ACL from non existing file");
        }
        catch (IllegalConfigurationException e)
        {
            assertTrue("Unexpected exception message", Pattern.matches("ACL file '.*' is not found", e.getMessage()));
        }
    }

    public void testCreateInstanceWhenAclFileIsSpecifiedAsNonString()
    {
        DefaultAccessControlFactory factory = new DefaultAccessControlFactory();
        Map<String, Object> attributes = new HashMap<String, Object>();
        Integer aclFile = new Integer(0);
        attributes.put(DefaultAccessControlFactory.ATTRIBUTE_ACL_FILE, aclFile);
        try
        {
            factory.createInstance(attributes);
            fail("It should not be possible to create ACL from Integer");
        }
        catch (IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message", "Expected '" + DefaultAccessControlFactory.ATTRIBUTE_ACL_FILE
                    + "' attribute value of type String but was " + Integer.class + ": " + aclFile, e.getMessage());
        }
    }
}
