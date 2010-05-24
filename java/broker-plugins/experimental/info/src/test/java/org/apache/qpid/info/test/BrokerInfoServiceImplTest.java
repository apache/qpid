package org.apache.qpid.info.test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.qpid.info.BrokerInfoServiceImpl;
import org.apache.qpid.info.Info;
import junit.framework.TestCase;

/*
 * This test verifies the invoke() method for the info service making sure that the parameters are returned
 */
public class BrokerInfoServiceImplTest extends TestCase
{

    BrokerInfoServiceImpl bisi = null;

    public void testBrokerInfoServiceImpl()
    {
        bisi = new BrokerInfoServiceImpl(null);
        assertNotNull(bisi);
    }

    @SuppressWarnings("unchecked")
    public void testInvoke()
    {
        bisi = new BrokerInfoServiceImpl(null);
        assertNotNull(bisi);
        Info<? extends Map<String, String>> info = (Info<? extends Map<String, String>>) bisi
                .invoke();
        assertNotNull(info);
        Properties props = info.toProps();
        assertNotNull(props);
        List<String> qpidProps = Arrays.asList("java.class.path",
                "java.vm.name", "java.class.version", "os.arch", "os.name",
                "os.version", "sun.arch.data.model", "user.dir", "user.name",
                "user.timezone");
        for (String tag : qpidProps)
        {
            assertNotNull(props.getProperty(tag));
        }
    }

}
