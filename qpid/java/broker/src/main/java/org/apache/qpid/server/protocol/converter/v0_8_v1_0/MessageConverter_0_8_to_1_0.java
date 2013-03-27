package org.apache.qpid.server.protocol.converter.v0_8_v1_0;

import java.util.ArrayList;
import java.util.List;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v1_0.MessageConverter_to_1_0;
import org.apache.qpid.server.protocol.v1_0.MessageMetaData_1_0;

public class MessageConverter_0_8_to_1_0 extends MessageConverter_to_1_0<AMQMessage>
{
    @Override
    public Class<AMQMessage> getInputClass()
    {
        return AMQMessage.class;
    }

    protected MessageMetaData_1_0 convertMetaData(final AMQMessage serverMessage, SectionEncoder sectionEncoder)
    {

        List<Section> sections = new ArrayList<Section>(3);

        Header header = new Header();

        header.setDurable(serverMessage.isPersistent());

        BasicContentHeaderProperties contentHeader =
                (BasicContentHeaderProperties) serverMessage.getContentHeaderBody().getProperties();

        header.setPriority(UnsignedByte.valueOf(contentHeader.getPriority()));
        final long expiration = serverMessage.getExpiration();
        final long arrivalTime = serverMessage.getArrivalTime();

        if(expiration > arrivalTime)
        {
            header.setTtl(UnsignedInteger.valueOf(expiration - arrivalTime));
        }
        sections.add(header);


        Properties props = new Properties();

        /*
            TODO: The following properties are not currently set:

            creationTime
            groupId
            groupSequence
            replyToGroupId
            to
        */

        props.setContentEncoding(Symbol.valueOf(contentHeader.getEncodingAsString()));

        props.setContentType(Symbol.valueOf(contentHeader.getContentTypeAsString()));

        // Modify the content type when we are dealing with java object messages produced by the Qpid 0.x client
        if(props.getContentType() == Symbol.valueOf("application/java-object-stream"))
        {
            props.setContentType(Symbol.valueOf("application/x-java-serialized-object"));
        }

        final AMQShortString correlationId = contentHeader.getCorrelationId();
        if(correlationId != null)
        {
            props.setCorrelationId(new Binary(correlationId.getBytes()));
        }

        final AMQShortString messageId = contentHeader.getMessageId();
        if(messageId != null)
        {
            props.setMessageId(new Binary(messageId.getBytes()));
        }
        props.setReplyTo(String.valueOf(contentHeader.getReplyTo()));

        props.setSubject(serverMessage.getRoutingKey());
        if(contentHeader.getUserId() != null)
        {
            props.setUserId(new Binary(contentHeader.getUserId().getBytes()));
        }

        sections.add(props);

        sections.add(new ApplicationProperties(FieldTable.convertToMap(contentHeader.getHeaders())));

        return new MessageMetaData_1_0(sections, sectionEncoder);
    }

}
