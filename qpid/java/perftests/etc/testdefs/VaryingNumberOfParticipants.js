
var jsonObject = {
    _tests:[]
};

var duration = 30000;
var queueName = "direct://amq.direct//varNumOfParticipants?durable='true'";

var numbersOfProducers = [1, 2, 5, 10];
var numbersOfConsumers = [1, 2, 5, 10];

for(producersIndex=0; producersIndex < numbersOfProducers.length; producersIndex++)
{
    for(consumersIndex=0; consumersIndex < numbersOfConsumers.length; consumersIndex++)
    {
        var numberOfProducers = numbersOfProducers[producersIndex];
        var numberOfConsumers = numbersOfConsumers[consumersIndex];
        var test = {
          "_name": "Varying number of participants: " + numberOfConsumers + " consumers - " + numberOfProducers + " producers - PERSISTENT",
          "_queues":[
            {
              "_name": queueName,
              "_durable": true
            }
          ],
         "_iterations":[
            {
              "_acknowledgeMode": 0
            },
            {
              "_acknowledgeMode": 1
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
}
