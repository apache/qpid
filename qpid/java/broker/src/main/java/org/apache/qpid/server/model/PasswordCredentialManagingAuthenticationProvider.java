package org.apache.qpid.server.model;

import java.util.Map;

import javax.security.auth.login.AccountNotFoundException;

public interface PasswordCredentialManagingAuthenticationProvider extends AuthenticationProvider
{
    boolean createUser(String username, String password, Map<String, String> attributes);

    void deleteUser(String user) throws AccountNotFoundException;

    void setPassword(String username, String password) throws AccountNotFoundException;

    Map<String, Map<String,String>>  getUsers();

}
