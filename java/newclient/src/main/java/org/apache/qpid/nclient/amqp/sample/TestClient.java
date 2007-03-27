package org.apache.qpid.nclient.amqp.sample;

import java.util.StringTokenizer;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.nclient.amqp.AMQPConnection;
import org.apache.qpid.nclient.transport.AMQPConnectionURL;
import org.apache.qpid.nclient.transport.ConnectionURL;
import org.apache.qpid.nclient.transport.TransportConnection;
import org.apache.qpid.nclient.transport.TransportConnectionFactory;
import org.apache.qpid.nclient.transport.TransportConnectionFactory.ConnectionType;

/**
 * This class illustrates the usage of the API
 * Notes this is just a simple demo.
 * 
 * I have used Helper classes to keep the code cleaner.
 */
public class TestClient
{
    private byte major;
    private byte minor;
    private ConnectionURL _url;
    
    public AMQPConnection openConnection() throws Exception
    {
	_url = new AMQPConnectionURL("");
	TransportConnection conn = TransportConnectionFactory.createTransportConnection(_url, ConnectionType.VM);
	return new AMQPConnection(conn);
    }
    
    public void handleProtocolNegotiation(AMQPConnection con) throws Exception
    {
	// ConnectionStartBody
	ConnectionStartBody connectionStartBody = con.openTCPConnection();
	major = connectionStartBody.getMajor();
	minor = connectionStartBody.getMajor();
	
	FieldTable clientProperties = FieldTableFactory.newFieldTable();        
        clientProperties.put(new AMQShortString(ClientProperties.instance.toString()),"Test"); // setting only the client id
        
        final String locales = new String(connectionStartBody.getLocales(), "utf8");
        final StringTokenizer tokenizer = new StringTokenizer(locales, " ");
        
        final String mechanism = SecurityHelper.chooseMechanism(connectionStartBody.getMechanisms()); 
            
        SaslClient sc = Sasl.createSaslClient(new String[]{mechanism},
                null, "AMQP", "localhost",
                null, SecurityHelper.createCallbackHandler(mechanism,_url));
        
	ConnectionStartOkBody connectionStartOkBody = 
	    ConnectionStartOkBody.createMethodBody(major, minor, clientProperties, 
		                                   new AMQShortString(tokenizer.nextToken()), 
		                                   new AMQShortString(mechanism), 
		                                   (sc.hasInitialResponse() ? sc.evaluateChallenge(new byte[0]) : null));
	// ConnectionSecureBody 
	ConnectionSecureBody connectionSecureBody = con.startOk(connectionStartOkBody);
	
	ConnectionSecureOkBody connectionSecureOkBody = ConnectionSecureOkBody.createMethodBody(
							major,minor,sc.evaluateChallenge(connectionSecureBody.getChallenge()));
	
	// Assuming the server is not going to send another challenge
	ConnectionTuneBody connectionTuneBody = (ConnectionTuneBody)con.secureOk(connectionSecureOkBody);
	
	// Using broker supplied values
	ConnectionTuneOkBody connectionTuneOkBody = 
	    	ConnectionTuneOkBody.createMethodBody(major,minor,
	    					      connectionTuneBody.getChannelMax(),
	    					      connectionTuneBody.getFrameMax(),
	    					      connectionTuneBody.getHeartbeat());
	con.tuneOk(connectionTuneOkBody);
    }

    public static void main(String[] args)
    {
	TestClient test = new TestClient();
	AMQPConnection con = test.openConnection();
        
    }

}
