package org.apache.qpid;

/**
 * This exception is used by the serial class (imp RFC 1982)
 *
 */
public class SerialException extends ArithmeticException
{
    /**
     * Constructs an <code>SerialException</code> with the specified
     * detail message.
     *
     * @param message The exception message.
     */
    public SerialException(String message)
    {
        super(message);
   }
}
