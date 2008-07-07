package org.apache.qpid.server.security.auth.sasl;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;

import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;

public class TestPrincipalDatabase implements PrincipalDatabase
{

    public boolean createPrincipal(Principal principal, char[] password)
    {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean deletePrincipal(Principal principal) throws AccountNotFoundException
    {
        // TODO Auto-generated method stub
        return false;
    }

    public Map<String, AuthenticationProviderInitialiser> getMechanisms()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public Principal getUser(String username)
    {
        // TODO Auto-generated method stub
        return null;
    }

    public List<Principal> getUsers()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public void setPassword(Principal principal, PasswordCallback callback) throws IOException,
            AccountNotFoundException
    {
        callback.setPassword("p".toCharArray());
    }

    public boolean updatePassword(Principal principal, char[] password) throws AccountNotFoundException
    {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean verifyPassword(String principal, char[] password) throws AccountNotFoundException
    {
        // TODO Auto-generated method stub
        return false;
    }

}
