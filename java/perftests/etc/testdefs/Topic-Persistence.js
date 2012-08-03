var duration = 30000;
var topicName = "topic://amq.topic/?routingkey='testTopic'";

var jsonObject = {
    _tests: [
    {
      "_name": "Topic persistence",
      "_iterations": [
        // note that we use _durableSubscription (the JaveBeans property name)
        // rather than _isDurableSubscription (the field name)
        {
          "_deliveryMode": 1,
          "_durableSubscription": false
        },
        {
          "_deliveryMode": 2,
          "_durableSubscription": true
        }
      ],
      "_clients": [
        {
          "_name": "producingClient",
          "_connections":[
            {
              "_name": "connection1",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session1",
                  "_producers": [
                    {
                      "_name": "Producer",
                      "_destinationName": topicName,
                      "_maximumDuration": duration,
                      "_startDelay": 2000 // gives the consumers time to implicitly create the topic
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
      .concat(QPID.times(10,
        {
          "_name": "consumingClient-__INDEX",
          "_connections":[
            {
              "_name": "connection1",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session1",
                  "_consumers": [
                    {
                      "_name": "Consumer-__INDEX",
                      "_destinationName": topicName,
                      "_isTopic": true,
                      "_maximumDuration": duration,
                    }
                  ]
                }
              ]
            }
          ]
        },
        "__INDEX"))
    }]
};

