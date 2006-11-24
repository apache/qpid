package org.apache.qpid.example.publisher;

import org.apache.log4j.Logger;

/**
 * Author: Marnie McCormack
 * Date: 18-Jul-2006
 * Time: 11:13:23
 * Copyright JPMorgan Chase 2006
 */
public class MessageFactoryException extends Exception {

    private int _errorCode;

    public MessageFactoryException(String message)
    {
        super(message);
    }

    public MessageFactoryException(String msg, Throwable t)
    {
        super(msg, t);
    }

    public MessageFactoryException(int errorCode, String msg, Throwable t)
    {
        super(msg + " [error code " + errorCode + ']', t);
        _errorCode = errorCode;
    }

    public MessageFactoryException(int errorCode, String msg)
    {
        super(msg + " [error code " + errorCode + ']');
        _errorCode = errorCode;
    }

    public MessageFactoryException(Logger logger, String msg, Throwable t)
    {
        this(msg, t);
        logger.error(getMessage(), this);
    }

    public MessageFactoryException(Logger logger, String msg)
    {
        this(msg);
        logger.error(getMessage(), this);
    }

    public MessageFactoryException(Logger logger, int errorCode, String msg)
    {
        this(errorCode, msg);
        logger.error(getMessage(), this);
    }

    public int getErrorCode()
    {
        return _errorCode;
    }
}

