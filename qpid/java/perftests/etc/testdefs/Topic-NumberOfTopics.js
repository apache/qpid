var jsonObject = {
    _tests:[]
};

var duration = 30000;
var topicName = "topic://amq.topic/?routingkey='testTopic.__INDEX'";

var numbersOfTopics = [1, 10, 50, 100];

// Each test has n pairs of producers and consumers, each with a different topic

for(i=0; i < numbersOfTopics.length ; i++)
{
    var numberOfTopics = numbersOfTopics[i];
    var test = {
      "_name": numberOfTopics,
      "_clients":
        QPID.times(numberOfTopics,
            {
              "_name": "producingClient-__INDEX",
              "_connections":[
                {
                  "_name": "connection1",
                  "_factory": "connectionfactory",
                  "_sessions": [
                    {
                      "_sessionName": "session1",
                      "_producers": [
                        {
                          "_name": "Producer-__INDEX",
                          "_destinationName": topicName,
                          "_deliveryMode": 1,
                          "_maximumDuration": duration,
                          "_startDelay": 2000 // gives the consumers time to implicitly create the topic
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            "__INDEX")
        .concat(
        QPID.times(numberOfTopics,
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

