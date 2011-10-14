package org.apache.qpid.messaging.address;

public class AddressException extends Exception 
{
    public AddressException(String message)
    {
        super(message);
    }
    
    public AddressException(String message, Throwable cause)
    {
        super(message,cause);
    }
}
