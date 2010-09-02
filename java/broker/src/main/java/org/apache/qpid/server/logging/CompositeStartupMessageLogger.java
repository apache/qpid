package org.apache.qpid.server.logging;

public class CompositeStartupMessageLogger extends AbstractRootMessageLogger
{
    private RootMessageLogger[] _loggers;
    
    public CompositeStartupMessageLogger(RootMessageLogger[] loggers)
    {
        super();
        _loggers = loggers;
    }

    @Override
    public void rawMessage(String message, String logHierarchy)
    {
        for(RootMessageLogger l : _loggers)
        {
            l.rawMessage(message, logHierarchy);
        }
    }

    @Override
    public void rawMessage(String message, Throwable throwable, String logHierarchy)
    {
        for(RootMessageLogger l : _loggers)
        {
            l.rawMessage(message, throwable, logHierarchy);
        }
    }

}
