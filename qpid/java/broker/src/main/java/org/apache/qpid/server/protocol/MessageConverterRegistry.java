package org.apache.qpid.server.protocol;

import java.util.HashMap;
import java.util.Map;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class MessageConverterRegistry
{
    private static Map<Class<? extends ServerMessage>, Map<Class<? extends ServerMessage>, MessageConverter>>
            _converters =
            new HashMap<Class<? extends ServerMessage>, Map<Class<? extends ServerMessage>, MessageConverter>>();

    static
    {

        for(MessageConverter<? extends ServerMessage, ? extends ServerMessage> converter : (new QpidServiceLoader<MessageConverter>()).instancesOf(MessageConverter.class))
        {
            Map<Class<? extends ServerMessage>, MessageConverter> map = _converters.get(converter.getInputClass());
            if(map == null)
            {
                map = new HashMap<Class<? extends ServerMessage>, MessageConverter>();
                _converters.put(converter.getInputClass(), map);
            }
            map.put(converter.getOutputClass(),converter);
        }
    }

    public static <M  extends ServerMessage,N  extends ServerMessage> MessageConverter<M, N> getConverter(Class<M> from, Class<N> to)
    {
        Map<Class<? extends ServerMessage>, MessageConverter> map = _converters.get(from);
        return map == null ? null : map.get(to);
    }
}
