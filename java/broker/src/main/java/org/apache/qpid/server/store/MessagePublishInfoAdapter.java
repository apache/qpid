package org.apache.qpid.server.store;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;

public class MessagePublishInfoAdapter
{
    private final byte _majorVersion;
    private final byte _minorVersion;
    private final int _classId;
    private final int _methodId;


    public MessagePublishInfoAdapter(byte majorVersion, byte minorVersion)
    {
        _majorVersion = majorVersion;
        _minorVersion = minorVersion;
        _classId = BasicPublishBody.getClazz(majorVersion,minorVersion);
        _methodId = BasicPublishBody.getMethod(majorVersion,minorVersion);
    }

    public BasicPublishBody toMethodBody(MessagePublishInfo pubInfo)
    {
        return new BasicPublishBody(_majorVersion,
                                    _minorVersion,
                                    _classId,
                                    _methodId,
                                    pubInfo.getExchange(),
                                    pubInfo.isImmediate(),
                                    pubInfo.isMandatory(),
                                    pubInfo.getRoutingKey(),
                                    0) ; // ticket
    }

    public MessagePublishInfo toMessagePublishInfo(final BasicPublishBody body)
    {
        return new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return body.getExchange();
            }

            public boolean isImmediate()
            {
                return body.getImmediate();
            }

            public boolean isMandatory()
            {
                return body.getMandatory();
            }

            public AMQShortString getRoutingKey()
            {
                return body.getRoutingKey();
            }
        };
    }
}
