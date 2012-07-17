var jsonObject = {
    _tests:[]
};

var duration = 30000;
var topicName = "topic://amq.topic/?routingkey='testTopic.1'";

var numbersOfConsumers = [1, 10, 50, 100];

for(i=0; i < numbersOfConsumers.length ; i++)
{
    var numberOfConsumers = numbersOfConsumers[i];
    var test = {
      "_name": numberOfConsumers,
      "_clients":[
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
                      "_name": "Producer1",
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
      ].concat(QPID.times(numberOfConsumers,
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
                      "_maximumDuration": duration
                    }
                  ]
                }
              ]
            }
          ]
        },
        "__INDEX"))
    };

    jsonObject._tests= jsonObject._tests.concat(test);
}

