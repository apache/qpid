package org.apache.qpid.framing;

public interface CommonContentHeaderProperties extends ContentHeaderProperties
{
    AMQShortString getContentType();

    void setContentType(AMQShortString contentType);

    FieldTable getHeaders();

    void setHeaders(FieldTable headers);

    byte getDeliveryMode();

    void setDeliveryMode(byte deliveryMode);

    byte getPriority();

    void setPriority(byte priority);

    AMQShortString getCorrelationId();

    void setCorrelationId(AMQShortString correlationId);

    AMQShortString getReplyTo();

    void setReplyTo(AMQShortString replyTo);

    long getExpiration();

    void setExpiration(long expiration);

    AMQShortString getMessageId();

    void setMessageId(AMQShortString messageId);

    long getTimestamp();

    void setTimestamp(long timestamp);

    AMQShortString getType();

    void setType(AMQShortString type);

    AMQShortString getUserId();

    void setUserId(AMQShortString userId);

    AMQShortString getAppId();

    void setAppId(AMQShortString appId);

    AMQShortString getClusterId();

    void setClusterId(AMQShortString clusterId);

    AMQShortString getEncoding();

    void setEncoding(AMQShortString encoding);
}
