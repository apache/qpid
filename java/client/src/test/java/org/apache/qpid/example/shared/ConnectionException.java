package org.apache.qpid.example.shared;

import org.apache.log4j.Logger;

/**
 * Author: Marnie McCormack
 * Date: 18-Jul-2006
 * Time: 11:13:23
 * Copyright JPMorgan Chase 2006
 */
public class ConnectionException extends Exception {

    private int _errorCode;

    public ConnectionException(String message)
    {
        super(message);
    }

    public ConnectionException(String msg, Throwable t)
    {
        super(msg, t);
    }

    public ConnectionException(int errorCode, String msg, Throwable t)
    {
        super(msg + " [error code " + errorCode + ']', t);
        _errorCode = errorCode;
    }

    public ConnectionException(int errorCode, String msg)
    {
        super(msg + " [error code " + errorCode + ']');
        _errorCode = errorCode;
    }

    public ConnectionException(Logger logger, String msg, Throwable t)
    {
        this(msg, t);
        logger.error(getMessage(), this);
    }

    public ConnectionException(Logger logger, String msg)
    {
        this(msg);
        logger.error(getMessage(), this);
    }

    public ConnectionException(Logger logger, int errorCode, String msg)
    {
        this(errorCode, msg);
        logger.error(getMessage(), this);
    }

    public int getErrorCode()
    {
        return _errorCode;
    }
}
