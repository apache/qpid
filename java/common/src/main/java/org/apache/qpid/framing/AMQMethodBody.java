package org.apache.qpid.framing;

import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.protocol.AMQConstant;

/**
 * Created by IntelliJ IDEA.
 * User: U146758
 * Date: 08-Mar-2007
 * Time: 11:30:28
 * To change this template use File | Settings | File Templates.
 */
public interface AMQMethodBody extends AMQBody
{
    AMQChannelException getChannelNotFoundException(int channelId);

    AMQChannelException getChannelException(AMQConstant code, String message);

    AMQChannelException getChannelException(AMQConstant code, String message, Throwable cause);

    AMQConnectionException getConnectionException(AMQConstant code, String message);

    AMQConnectionException getConnectionException(AMQConstant code, String message, Throwable cause);
}
