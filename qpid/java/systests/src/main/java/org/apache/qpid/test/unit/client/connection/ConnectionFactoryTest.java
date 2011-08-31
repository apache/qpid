package org.apache.qpid.test.unit.client.connection;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class ConnectionFactoryTest extends QpidBrokerTestCase
{

    /**
     * The username & password specified should not override the default
     * specified in the URL.
     */
    public void testCreateConnectionWithUsernamePassword() throws Exception
    {
        String URL = "amqp://guest:guest@clientID/test?brokerlist='tcp://localhost:5672'";
        AMQConnectionFactory factory = new AMQConnectionFactory(URL);
        
        AMQConnection con = (AMQConnection)factory.createConnection();
        assertEquals("Usernames used is different from the one in URL","guest",con.getConnectionURL().getUsername());
        assertEquals("Password used is different from the one in URL","guest",con.getConnectionURL().getPassword());
     
        try
        {
            AMQConnection con2 = (AMQConnection)factory.createConnection("user","pass");
            assertEquals("Usernames used is different from the one in URL","user",con2.getConnectionURL().getUsername());
            assertEquals("Password used is different from the one in URL","pass",con2.getConnectionURL().getPassword());
        }
        catch(Exception e)
        {
            // ignore
        }
        
        AMQConnection con3 = (AMQConnection)factory.createConnection();
        assertEquals("Usernames used is different from the one in URL","guest",con3.getConnectionURL().getUsername());
        assertEquals("Password used is different from the one in URL","guest",con3.getConnectionURL().getPassword());
    }
    
}
