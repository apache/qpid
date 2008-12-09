package org.apache.qpid.server.security.auth.database;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;

public class HashedUser implements Principal
{
    private static final Logger _logger = Logger.getLogger(HashedUser.class);

    String _name;
    char[] _password;
    byte[] _encodedPassword = null;
    private boolean _modified = false;
    private boolean _deleted = false;

    HashedUser(String[] data) throws UnsupportedEncodingException
    {
        if (data.length != 2)
        {
            throw new IllegalArgumentException("User Data should be length 2, username, password");
        }

        _name = data[0];

        byte[] encoded_password = data[1].getBytes(Base64MD5PasswordFilePrincipalDatabase.DEFAULT_ENCODING);

        Base64 b64 = new Base64();
        byte[] decoded = b64.decode(encoded_password);

        _encodedPassword = encoded_password;

        _password = new char[decoded.length];

        int index = 0;
        for (byte c : decoded)
        {
            _password[index++] = (char) c;
        }
    }

    public HashedUser(String name, char[] password)
    {
        _name = name;
        setPassword(password);
    }

    public String getName()
    {
        return _name;
    }

    public String toString()
    {
        return _name;
    }

    char[] getPassword()
    {
        return _password;
    }

    void setPassword(char[] password)
    {
        _password = password;
        _modified = true;
        _encodedPassword = null;
    }

    byte[] getEncodedPassword() throws EncoderException, UnsupportedEncodingException, NoSuchAlgorithmException
    {
        if (_encodedPassword == null)
        {
            encodePassword();
        }
        return _encodedPassword;
    }

    private void encodePassword() throws EncoderException, UnsupportedEncodingException, NoSuchAlgorithmException
    {
        byte[] byteArray = new byte[_password.length];
        int index = 0;
        for (char c : _password)
        {
            byteArray[index++] = (byte) c;
        }
        _encodedPassword = (new Base64()).encode(byteArray);
    }

    public boolean isModified()
    {
        return _modified;
    }

    public boolean isDeleted()
    {
        return _deleted;
    }

    public void delete()
    {
        _deleted = true;
    }

    public void saved()
    {
        _modified = false;
    }

}