/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.framing;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.codec.MarkableDataInput;
import org.apache.qpid.protocol.AMQConstant;

public class AMQDataBlockDecoder
{

    private Logger _logger = LoggerFactory.getLogger(AMQDataBlockDecoder.class);
    private int _maxFrameSize = AMQConstant.FRAME_MIN_SIZE.getCode();

    public AMQDataBlockDecoder()
    {
    }

    public boolean decodable(MarkableDataInput in) throws AMQFrameDecodingException, IOException
    {
        final int remainingAfterAttributes = in.available() - (1 + 2 + 4 + 1);
        // type, channel, body length and end byte
        if (remainingAfterAttributes < 0)
        {
            return false;
        }

        in.mark(8);
        in.skip(1 + 2);


        // Get an unsigned int, lifted from MINA ByteBuffer getUnsignedInt() 
        final long bodySize = in.readInt() & 0xffffffffL;
        if (bodySize > _maxFrameSize)
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR,
                                                "Incoming frame size of "
                                                + bodySize
                                                + " is larger than negotiated maximum of  "
                                                + _maxFrameSize);
        }
        in.reset();

        return (remainingAfterAttributes >= bodySize);

    }

    public void processInput(MethodProcessor processor,
                             MarkableDataInput in)
            throws AMQFrameDecodingException, AMQProtocolVersionException, IOException
    {
        final byte type = in.readByte();

        final int channel = in.readUnsignedShort();
        final long bodySize = EncodingUtils.readUnsignedInteger(in);

        // bodySize can be zero
        if ((channel < 0) || (bodySize < 0))
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR,
                                                "Undecodable frame: type = " + type + " channel = " + channel
                                                + " bodySize = " + bodySize);
        }

        switch (type)
        {
            case 1:
                processMethod(channel, in, processor);
                break;
            case 2:
                ContentHeaderBody.process(channel, in, processor, bodySize);
                break;
            case 3:
                ContentBody.process(channel, in, processor, bodySize);
                break;
            case 8:
                HeartbeatBody.process(channel, in, processor, bodySize);
                break;
            default:
                throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR, "Unsupported frame type: " + type);
        }

        byte marker = in.readByte();
        if ((marker & 0xFF) != 0xCE)
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR,
                                                "End of frame marker not found. Read " + marker + " length=" + bodySize
                                                + " type=" + type);
        }

    }

    public void setMaxFrameSize(final int maxFrameSize)
    {
        _maxFrameSize = maxFrameSize;
    }

    private void processMethod(int channelId,
                               MarkableDataInput in,
                               MethodProcessor dispatcher)
            throws AMQFrameDecodingException, IOException
    {
        final int classAndMethod = in.readInt();
        int classId = classAndMethod >> 16;
        int methodId = classAndMethod & 0xFFFF;
        dispatcher.setCurrentMethod(classId, methodId);
        try
        {
            switch (classAndMethod)
            {
                //CONNECTION_CLASS:
                case 0x000a000a:
                    ConnectionStartBody.process(in, dispatcher);
                    break;
                case 0x000a000b:
                    ConnectionStartOkBody.process(in, dispatcher);
                    break;
                case 0x000a0014:
                    ConnectionSecureBody.process(in, dispatcher);
                    break;
                case 0x000a0015:
                    ConnectionSecureOkBody.process(in, dispatcher);
                    break;
                case 0x000a001e:
                    ConnectionTuneBody.process(in, dispatcher);
                    break;
                case 0x000a001f:
                    ConnectionTuneOkBody.process(in, dispatcher);
                    break;
                case 0x000a0028:
                    ConnectionOpenBody.process(in, dispatcher);
                    break;
                case 0x000a0029:
                    ConnectionOpenOkBody.process(in, dispatcher);
                    break;
                case 0x000a002a:
                    ConnectionRedirectBody.process(in, dispatcher);
                    break;
                case 0x000a0032:
                    if (dispatcher.getProtocolVersion().equals(ProtocolVersion.v8_0))
                    {
                        ConnectionRedirectBody.process(in, dispatcher);
                    }
                    else
                    {
                        ConnectionCloseBody.process(in, dispatcher);
                    }
                    break;
                case 0x000a0033:
                    if (dispatcher.getProtocolVersion().equals(ProtocolVersion.v8_0))
                    {
                        throw newUnknownMethodException(classId, methodId,
                                                        dispatcher.getProtocolVersion());
                    }
                    else
                    {
                        dispatcher.receiveConnectionCloseOk();
                    }
                    break;
                case 0x000a003c:
                    if (dispatcher.getProtocolVersion().equals(ProtocolVersion.v8_0))
                    {
                        ConnectionCloseBody.process(in, dispatcher);
                    }
                    else
                    {
                        throw newUnknownMethodException(classId, methodId,
                                                        dispatcher.getProtocolVersion());
                    }
                    break;
                case 0x000a003d:
                    if (dispatcher.getProtocolVersion().equals(ProtocolVersion.v8_0))
                    {
                        dispatcher.receiveConnectionCloseOk();
                    }
                    else
                    {
                        throw newUnknownMethodException(classId, methodId,
                                                        dispatcher.getProtocolVersion());
                    }
                    break;

                // CHANNEL_CLASS:

                case 0x0014000a:
                    ChannelOpenBody.process(channelId, in, dispatcher);
                    break;
                case 0x0014000b:
                    ChannelOpenOkBody.process(channelId, in, dispatcher.getProtocolVersion(), dispatcher);
                    break;
                case 0x00140014:
                    ChannelFlowBody.process(channelId, in, dispatcher);
                    break;
                case 0x00140015:
                    ChannelFlowOkBody.process(channelId, in, dispatcher);
                    break;
                case 0x0014001e:
                    ChannelAlertBody.process(channelId, in, dispatcher);
                    break;
                case 0x00140028:
                    ChannelCloseBody.process(channelId, in, dispatcher);
                    break;
                case 0x00140029:
                    dispatcher.receiveChannelCloseOk(channelId);
                    break;

                // ACCESS_CLASS:

                case 0x001e000a:
                    AccessRequestBody.process(channelId, in, dispatcher);
                    break;
                case 0x001e000b:
                    AccessRequestOkBody.process(channelId, in, dispatcher);
                    break;

                // EXCHANGE_CLASS:

                case 0x0028000a:
                    ExchangeDeclareBody.process(channelId, in, dispatcher);
                    break;
                case 0x0028000b:
                    dispatcher.receiveExchangeDeclareOk(channelId);
                    break;
                case 0x00280014:
                    ExchangeDeleteBody.process(channelId, in, dispatcher);
                    break;
                case 0x00280015:
                    dispatcher.receiveExchangeDeleteOk(channelId);
                    break;
                case 0x00280016:
                    ExchangeBoundBody.process(channelId, in, dispatcher);
                    break;
                case 0x00280017:
                    ExchangeBoundOkBody.process(channelId, in, dispatcher);
                    break;


                // QUEUE_CLASS:

                case 0x0032000a:
                    QueueDeclareBody.process(channelId, in, dispatcher);
                    break;
                case 0x0032000b:
                    QueueDeclareOkBody.process(channelId, in, dispatcher);
                    break;
                case 0x00320014:
                    QueueBindBody.process(channelId, in, dispatcher);
                    break;
                case 0x00320015:
                    dispatcher.receiveQueueBindOk(channelId);
                    break;
                case 0x0032001e:
                    QueuePurgeBody.process(channelId, in, dispatcher);
                    break;
                case 0x0032001f:
                    QueuePurgeOkBody.process(channelId, in, dispatcher);
                    break;
                case 0x00320028:
                    QueueDeleteBody.process(channelId, in, dispatcher);
                    break;
                case 0x00320029:
                    QueueDeleteOkBody.process(channelId, in, dispatcher);
                    break;
                case 0x00320032:
                    QueueUnbindBody.process(channelId, in, dispatcher);
                    break;
                case 0x00320033:
                    dispatcher.receiveQueueUnbindOk(channelId);
                    break;


                // BASIC_CLASS:

                case 0x003c000a:
                    BasicQosBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c000b:
                    dispatcher.receiveBasicQosOk(channelId);
                    break;
                case 0x003c0014:
                    BasicConsumeBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0015:
                    BasicConsumeOkBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c001e:
                    BasicCancelBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c001f:
                    BasicCancelOkBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0028:
                    BasicPublishBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0032:
                    BasicReturnBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c003c:
                    BasicDeliverBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0046:
                    BasicGetBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0047:
                    BasicGetOkBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0048:
                    BasicGetEmptyBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0050:
                    BasicAckBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c005a:
                    BasicRejectBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c0064:
                    BasicRecoverBody.process(channelId, in, dispatcher.getProtocolVersion(), dispatcher);
                    break;
                case 0x003c0065:
                    dispatcher.receiveBasicRecoverSyncOk(channelId);
                    break;
                case 0x003c0066:
                    BasicRecoverSyncBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c006e:
                    BasicRecoverSyncBody.process(channelId, in, dispatcher);
                    break;
                case 0x003c006f:
                    dispatcher.receiveBasicRecoverSyncOk(channelId);
                    break;

                // TX_CLASS:

                case 0x005a000a:
                    dispatcher.receiveTxSelect(channelId);
                    break;
                case 0x005a000b:
                    dispatcher.receiveTxSelectOk(channelId);
                    break;
                case 0x005a0014:
                    dispatcher.receiveTxCommit(channelId);
                    break;
                case 0x005a0015:
                    dispatcher.receiveTxCommitOk(channelId);
                    break;
                case 0x005a001e:
                    dispatcher.receiveTxRollback(channelId);
                    break;
                case 0x005a001f:
                    dispatcher.receiveTxRollbackOk(channelId);
                    break;

                default:
                    throw newUnknownMethodException(classId, methodId,
                                                    dispatcher.getProtocolVersion());

            }
        }
        finally
        {
            dispatcher.setCurrentMethod(0,0);
        }
    }

    private AMQFrameDecodingException newUnknownMethodException(final int classId,
                                                                final int methodId,
                                                                ProtocolVersion protocolVersion)
    {
        return new AMQFrameDecodingException(AMQConstant.COMMAND_INVALID,
                                             "Method "
                                             + methodId
                                             + " unknown in AMQP version "
                                             + protocolVersion
                                             + " (while trying to decode class "
                                             + classId
                                             + " method "
                                             + methodId
                                             + ".");
    }


}
