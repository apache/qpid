package org.apache.qpid.disttest.controller.config;

import org.apache.qpid.disttest.message.CreateConsumerCommand;

public class ConsumerConfig extends ParticipantConfig
{
    private boolean _isTopic;
    private boolean _isDurableSubscription;
    private boolean _isBrowsingSubscription;
    private String _selector;
    private boolean _noLocal;
    private boolean _synchronous;

    // For Gson
    public ConsumerConfig()
    {
        _isTopic = false;
        _isDurableSubscription = false;
        _isBrowsingSubscription = false;
        _selector = null;
        _noLocal = false;
        _synchronous = true;
    }

    public ConsumerConfig(
            String consumerName,
            String destinationName,
            long numberOfMessages,
            int batchSize,
            long maximumDuration,
            boolean isTopic,
            boolean isDurableSubscription,
            boolean isBrowsingSubscription,
            String selector,
            boolean noLocal,
            boolean synchronous)
    {
        super(consumerName, destinationName, numberOfMessages, batchSize, maximumDuration);

        _isTopic = isTopic;
        _isDurableSubscription = isDurableSubscription;
        _isBrowsingSubscription = isBrowsingSubscription;
        _selector = selector;
        _noLocal = noLocal;
        _synchronous = synchronous;
    }

    public CreateConsumerCommand createCommand(String sessionName)
    {
        CreateConsumerCommand createConsumerCommand = new CreateConsumerCommand();

        setParticipantProperties(createConsumerCommand);

        createConsumerCommand.setSessionName(sessionName);
        createConsumerCommand.setTopic(_isTopic);
        createConsumerCommand.setDurableSubscription(_isDurableSubscription);
        createConsumerCommand.setBrowsingSubscription(_isBrowsingSubscription);
        createConsumerCommand.setSelector(_selector);
        createConsumerCommand.setNoLocal(_noLocal);
        createConsumerCommand.setSynchronous(_synchronous);

        return createConsumerCommand;
    }

}
