package org.apache.qpid.example.publisher;

import org.apache.log4j.Logger;

/**
 * Exception thrown by monitor when cannot send a message marked for immediate delivery
 * Author: Marnie McCormack
 * Date: 18-Jul-2006
 * Time: 11:13:23
 * Copyright JPMorgan Chase 2006
 */
public class UndeliveredMessageException extends Exception {

    private int _errorCode;

    public UndeliveredMessageException(String message)
    {
        super(message);
    }

    public UndeliveredMessageException(String msg, Throwable t)
    {
        super(msg, t);
    }

    public UndeliveredMessageException(int errorCode, String msg, Throwable t)
    {
        super(msg + " [error code " + errorCode + ']', t);
        _errorCode = errorCode;
    }

    public UndeliveredMessageException(int errorCode, String msg)
    {
        super(msg + " [error code " + errorCode + ']');
        _errorCode = errorCode;
    }

    public UndeliveredMessageException(Logger logger, String msg, Throwable t)
    {
        this(msg, t);
        logger.error(getMessage(), this);
    }

    public UndeliveredMessageException(Logger logger, String msg)
    {
        this(msg);
        logger.error(getMessage(), this);
    }

    public UndeliveredMessageException(Logger logger, int errorCode, String msg)
    {
        this(errorCode, msg);
        logger.error(getMessage(), this);
    }

    public int getErrorCode()
    {
        return _errorCode;
    }
}
