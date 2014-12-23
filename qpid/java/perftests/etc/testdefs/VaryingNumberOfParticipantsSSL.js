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
var jsonObject = {
    _tests:[]
};

var duration = 30000;
var queueName = "direct://amq.direct//varNumOfParticipantsSsl?durable='true'";

var numbersOfProducers = [1, 2, 5, 10, 20];
var numbersOfConsumers = [1, 2, 5, 10, 20];

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
            }
          ],
          "_clients":
            QPID.times(numberOfProducers,
            {
              "_name": "producingClient__PRODUCING_CLIENT_INDEX",
              "_connections":[
                {
                  "_name": "connection1",
                  "_factory": "sslconnectionfactory",
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
                  "_factory": "sslconnectionfactory",
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
