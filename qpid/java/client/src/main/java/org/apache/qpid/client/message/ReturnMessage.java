package org.apache.qpid.client.message;

import org.apache.qpid.framing.AMQShortString;

public class ReturnMessage extends UnprocessedMessage_0_8
{
    final private AMQShortString  _replyText;
    final private int _replyCode;

    public ReturnMessage(int channelId,AMQShortString exchange,AMQShortString routingKey,AMQShortString replyText,int replyCode)
    {
        super(channelId,-1,null,exchange,routingKey,false);
        _replyText = replyText;
        _replyCode = replyCode;
    }

    public int getReplyCode()
    {
        return _replyCode;
    }

    public AMQShortString getReplyText()
    {
        return _replyText;
    }
}
