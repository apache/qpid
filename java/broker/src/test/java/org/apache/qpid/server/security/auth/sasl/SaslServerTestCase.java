package org.apache.qpid.server.security.auth.sasl;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.security.auth.database.PrincipalDatabase;

import junit.framework.TestCase;

public abstract class SaslServerTestCase extends TestCase
{
    protected SaslServer server;
    protected String username = "u";
    protected String password = "p";
    protected String notpassword = "a";
    protected PrincipalDatabase db = new TestPrincipalDatabase();
    
    protected byte[] correctresponse;
    protected byte[] wrongresponse;
    
    public void testSucessfulAuth() throws SaslException
    {
        byte[] resp = this.server.evaluateResponse(correctresponse);
        assertNull(resp);
    }
    
    public void testFailAuth()
    {
        boolean exceptionCaught  = false;
        try
        {
            byte[] resp = this.server.evaluateResponse(wrongresponse);
        }
        catch (SaslException e)
        {
            assertEquals("Authentication failed", e.getCause().getMessage());
            exceptionCaught = true;
        }
        if (!exceptionCaught)
        {
            fail("Should have thrown SaslException");
        }
    }
    
}
