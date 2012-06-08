package org.apache.qpid.server.model;

import java.util.Map;

public interface PasswordCredentialManagingAuthenticationProvider extends AuthenticationProvider
{
    void createUser(String username, String password, Map<String, String> attributes);

    void deleteUser(String user);

    void setPassword(String username, String password);

    Map<String, Map<String,String>>  getUsers();

}
