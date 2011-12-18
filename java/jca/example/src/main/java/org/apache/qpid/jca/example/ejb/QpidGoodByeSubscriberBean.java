package org.apache.qpid.jca.example.ejb;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.Message;
import javax.jms.MessageListener;

@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
    @ActivationConfigProperty(propertyName = "destination", propertyValue = "@qpid.goodbye.topic.jndi.name@"),
    @ActivationConfigProperty(propertyName = "connectionURL", propertyValue = "@broker.url@"),
    @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "NotDurable"),
    @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "hello.Topic"),
    @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "10")
})

public class QpidGoodByeSubscriberBean implements MessageListener
{

    @Override
    public void onMessage(Message message)
    {
        System.out.println(message);
    }

}
