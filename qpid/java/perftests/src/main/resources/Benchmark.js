
var jsonObject = {
    _tests:[]
};

var duration = 30000;
var queueName = "direct://amq.direct//benchmark?durable='true'";

var numbersOfParticipants = [1, 2, 5, 10];

for(participantIndex=0; participantIndex < numbersOfParticipants.length; participantIndex++)
{
    var numberOfProducers = numbersOfParticipants[participantIndex];
    var numberOfConsumers = numbersOfParticipants[participantIndex];
    var test = {
      "_name": "" + numberOfProducers + " producer(s) and " + numberOfConsumers + " consumer(s), each on separate connections, persistent messaging with transactional sessions",
      "_queues":[
        {
          "_name": queueName,
          "_durable": true
        }
      ],
      "_clients":
        QPID.times(numberOfProducers,
        {
          "_name": "producingClient__PRODUCING_CLIENT_INDEX",
          "_connections":[
            {
              "_name": "connection1",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session1",
                  "_acknowledgeMode": 0,
                  "_producers": [
                    {
                      "_name": "Producer__PRODUCING_CLIENT_INDEX",
                      "_destinationName": queueName,
                      "_maximumDuration": duration,
                      "_deliveryMode": 2,
                      "_messageSize": 1024
                    }
                  ]
                }
              ]
            }
          ]
        },
        "__PRODUCING_CLIENT_INDEX")
        .concat(QPID.times(numberOfConsumers,
        {
          "_name": "consumingClient__CONSUMING_CLIENT_INDEX",
          "_connections":[
            {
              "_name": "connection1",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session1",
                  "_acknowledgeMode": 0,
                  "_consumers": [
                    {
                      "_name": "Consumer__CONSUMING_CLIENT_INDEX",
                      "_destinationName": queueName,
                      "_maximumDuration": duration
                    }
                  ]
                }
              ]
            }
          ]
        },
        "__CONSUMING_CLIENT_INDEX"))
    };

    jsonObject._tests= jsonObject._tests.concat(test);
}
