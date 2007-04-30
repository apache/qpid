package org.apache.qpid.nclient.core;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;


public class AMQPException extends Exception
{
    private int _errorCode;

    public AMQPException(String message)
    {
        super(message);
    }

    public AMQPException(String msg, Throwable t)
    {
        super(msg, t);
    }

    public AMQPException(int errorCode, String msg, Throwable t)
    {
        super(msg + " [error code " + errorCode + ']', t);
        _errorCode = errorCode;
    }

    public AMQPException(int errorCode, String msg)
    {
        super(msg + " [error code " + errorCode + ']');
        _errorCode = errorCode;
    }

    public AMQPException(Logger logger, String msg, Throwable t)
    {
        this(msg, t);
        logger.error(getMessage(), this);
    }

    public AMQPException(Logger logger, String msg)
    {
        this(msg);
        logger.error(getMessage(), this);
    }

    public AMQPException(Logger logger, int errorCode, String msg)
    {
        this(errorCode, msg);
        logger.error(getMessage(), this);
    }

    public int getErrorCode()
    {
        return _errorCode;
    }
 }
