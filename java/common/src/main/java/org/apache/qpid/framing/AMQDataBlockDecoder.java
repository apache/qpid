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
    { }

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
        if(bodySize > _maxFrameSize)
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR, "Incoming frame size of "+bodySize+" is larger than negotiated maximum of  " + _maxFrameSize);
        }
        in.reset();

        return (remainingAfterAttributes >= bodySize);

    }

    public <T> T createAndPopulateFrame(ProtocolVersion pv,
                                        MethodProcessor<T> processor,
                                        MarkableDataInput in)
            throws AMQFrameDecodingException, AMQProtocolVersionException, IOException
    {
        final byte type = in.readByte();

        final int channel = in.readUnsignedShort();
        final long bodySize = EncodingUtils.readUnsignedInteger(in);

        // bodySize can be zero
        if ((channel < 0) || (bodySize < 0))
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR, "Undecodable frame: type = " + type + " channel = " + channel
                + " bodySize = " + bodySize);
        }

        T result;
        switch(type)
        {
            case 1:
                result = processMethod(channel, in, processor, pv);
                break;
            case 2:
                result = ContentHeaderBody.process(channel, in, processor, bodySize);
                break;
            case 3:
                result = ContentBody.process(channel, in, processor, bodySize);
                break;
            case 8:
                result = HeartbeatBody.process(channel, in, processor, bodySize);
                break;
            default:
                throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR, "Unsupported frame type: " + type);
        }

        byte marker = in.readByte();
        if ((marker & 0xFF) != 0xCE)
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR, "End of frame marker not found. Read " + marker + " length=" + bodySize
                + " type=" + type);
        }

        return result;
    }

    public void setMaxFrameSize(final int maxFrameSize)
    {
        _maxFrameSize = maxFrameSize;
    }

    private <T> T processMethod(int channelId, MarkableDataInput in, MethodProcessor<T> dispatcher, ProtocolVersion protocolVersion)
            throws AMQFrameDecodingException, IOException
    {
        final int classAndMethod = in.readInt();

        switch (classAndMethod)
        {
            //CONNECTION_CLASS:
            case 0x000a000a:
                return ConnectionStartBody.process(in, dispatcher);
            case 0x000a000b:
                return ConnectionStartOkBody.process(in, dispatcher);
            case 0x000a0014:
                return ConnectionSecureBody.process(in, dispatcher);
            case 0x000a0015:
                return ConnectionSecureOkBody.process(in, dispatcher);
            case 0x000a001e:
                return ConnectionTuneBody.process(in, dispatcher);
            case 0x000a001f:
                return ConnectionTuneOkBody.process(in, dispatcher);
            case 0x000a0028:
                return ConnectionOpenBody.process(in, dispatcher);
            case 0x000a0029:
                return ConnectionOpenOkBody.process(in, dispatcher);
            case 0x000a002a:
                return ConnectionRedirectBody.process(in, dispatcher);
            case 0x000a0032:
                if (protocolVersion.equals(ProtocolVersion.v8_0))
                {
                    return ConnectionRedirectBody.process(in, dispatcher);
                }
                else
                {
                    return ConnectionCloseBody.process(in, dispatcher);
                }
            case 0x000a0033:
                if (protocolVersion.equals(ProtocolVersion.v8_0))
                {
                    throw newUnknownMethodException((classAndMethod >> 16), (classAndMethod & 0xFFFF), protocolVersion);
                }
                else
                {
                    return dispatcher.connectionCloseOk();
                }
            case 0x000a003c:
                if (protocolVersion.equals(ProtocolVersion.v8_0))
                {
                    return ConnectionCloseBody.process(in, dispatcher);
                }
                else
                {
                    throw newUnknownMethodException((classAndMethod >> 16), (classAndMethod & 0xFFFF), protocolVersion);
                }
            case 0x000a003d:
                if (protocolVersion.equals(ProtocolVersion.v8_0))
                {
                    return dispatcher.connectionCloseOk();
                }
                else
                {
                    throw newUnknownMethodException((classAndMethod >> 16), (classAndMethod & 0xFFFF), protocolVersion);
                }

                // CHANNEL_CLASS:

            case 0x0014000a:
                return ChannelOpenBody.process(channelId, in, dispatcher);
            case 0x0014000b:
                return ChannelOpenOkBody.process(channelId, in, protocolVersion, dispatcher);
            case 0x00140014:
                return ChannelFlowBody.process(channelId, in, dispatcher);
            case 0x00140015:
                return ChannelFlowOkBody.process(channelId, in, dispatcher);
            case 0x0014001e:
                return ChannelAlertBody.process(channelId, in, dispatcher);
            case 0x00140028:
                return ChannelCloseBody.process(channelId, in, dispatcher);
            case 0x00140029:
                return dispatcher.channelCloseOk(channelId);

            // ACCESS_CLASS:

            case 0x001e000a:
                return AccessRequestBody.process(channelId, in, dispatcher);
            case 0x001e000b:
                return AccessRequestOkBody.process(channelId, in, dispatcher);

            // EXCHANGE_CLASS:

            case 0x0028000a:
                return ExchangeDeclareBody.process(channelId, in, dispatcher);
            case 0x0028000b:
                return dispatcher.exchangeDeclareOk(channelId);
            case 0x00280014:
                return ExchangeDeleteBody.process(channelId, in, dispatcher);
            case 0x00280015:
                return dispatcher.exchangeDeleteOk(channelId);
            case 0x00280016:
                return ExchangeBoundBody.process(channelId, in, dispatcher);
            case 0x00280017:
                return ExchangeBoundOkBody.process(channelId, in, dispatcher);


            // QUEUE_CLASS:

            case 0x0032000a:
                return QueueDeclareBody.process(channelId, in, dispatcher);
            case 0x0032000b:
                return QueueDeclareOkBody.process(channelId, in, dispatcher);
            case 0x00320014:
                return QueueBindBody.process(channelId, in, dispatcher);
            case 0x00320015:
                return dispatcher.queueBindOk(channelId);
            case 0x0032001e:
                return QueuePurgeBody.process(channelId, in, dispatcher);
            case 0x0032001f:
                return QueuePurgeOkBody.process(channelId, in, dispatcher);
            case 0x00320028:
                return QueueDeleteBody.process(channelId, in, dispatcher);
            case 0x00320029:
                return QueueDeleteOkBody.process(channelId, in, dispatcher);
            case 0x00320032:
                return QueueUnbindBody.process(channelId, in, dispatcher);
            case 0x00320033:
                return dispatcher.queueUnbindOk(channelId);


            // BASIC_CLASS:

            case 0x003c000a:
                return BasicQosBody.process(channelId, in, dispatcher);
            case 0x003c000b:
                return dispatcher.basicQosOk(channelId);
            case 0x003c0014:
                return BasicConsumeBody.process(channelId, in, dispatcher);
            case 0x003c0015:
                return BasicConsumeOkBody.process(channelId, in, dispatcher);
            case 0x003c001e:
                return BasicCancelBody.process(channelId, in, dispatcher);
            case 0x003c001f:
                return BasicCancelOkBody.process(channelId, in, dispatcher);
            case 0x003c0028:
                return BasicPublishBody.process(channelId, in, dispatcher);
            case 0x003c0032:
                return BasicReturnBody.process(channelId, in, dispatcher);
            case 0x003c003c:
                return BasicDeliverBody.process(channelId, in, dispatcher);
            case 0x003c0046:
                return BasicGetBody.process(channelId, in, dispatcher);
            case 0x003c0047:
                return BasicGetOkBody.process(channelId, in, dispatcher);
            case 0x003c0048:
                return BasicGetEmptyBody.process(channelId, in, dispatcher);
            case 0x003c0050:
                return BasicAckBody.process(channelId, in, dispatcher);
            case 0x003c005a:
                return BasicRejectBody.process(channelId, in, dispatcher);
            case 0x003c0064:
                return BasicRecoverBody.process(channelId, in, protocolVersion, dispatcher);
            case 0x003c0065:
                return dispatcher.basicRecoverSyncOk(channelId);
            case 0x003c0066:
                return BasicRecoverSyncBody.process(channelId, in, dispatcher);
            case 0x003c006e:
                return BasicRecoverSyncBody.process(channelId, in, dispatcher);
            case 0x003c006f:
                return dispatcher.basicRecoverSyncOk(channelId);

            // TX_CLASS:

            case 0x005a000a:
                return dispatcher.txSelect(channelId);
            case 0x005a000b:
                return dispatcher.txSelectOk(channelId);
            case 0x005a0014:
                return dispatcher.txCommit(channelId);
            case 0x005a0015:
                return dispatcher.txCommitOk(channelId);
            case 0x005a001e:
                return dispatcher.txRollback(channelId);
            case 0x005a001f:
                return dispatcher.txRollbackOk(channelId);

            default:
                throw newUnknownMethodException((classAndMethod >> 16), (classAndMethod & 0xFFFF), protocolVersion);

        }
    }

    private AMQFrameDecodingException newUnknownMethodException(final int classId, final int methodId, ProtocolVersion protocolVersion)
    {
        return new AMQFrameDecodingException(AMQConstant.COMMAND_INVALID,
                                             "Method " + methodId + " unknown in AMQP version " + protocolVersion
                                             + " (while trying to decode class " + classId + " method " + methodId + ".");
    }


}
