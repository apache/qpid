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
var queueName = "direct://amq.direct//varNumOfSessions?durable='true'";

var numbersOfSessions = [1, 2, 5, 10, 20, 40, 80];
var numberOfConsumingClients = 20;

for(i=0; i < numbersOfSessions.length ; i++)
{
    var sessionNumber = numbersOfSessions[i];
    var test = {
      "_name": sessionNumber,
      "_queues":[
        {
          "_name": queueName,
          "_durable": "true"
        }
      ],
      "_clients":[
        {
          "_name": "producingClient",
          "_connections":[
            {
              "_name": "connection1",
              "_factory": "connectionfactory",
              "_sessions": QPID.times(sessionNumber,
                {
                  "_sessionName": "session__SESSION_INDEX",
                  "_producers": [
                    {
                      "_name": "Producer__SESSION_INDEX",
                      "_destinationName": queueName,
                      "_deliveryMode": 2,
                      "_acknowledgeMode": 0,
                      "_maximumDuration": duration
                    }
                  ]
                },
                "__SESSION_INDEX")
            }
          ]
        },
      ].concat(QPID.times(numberOfConsumingClients,
        {
          "_name": "consumingClient__CONSUMING_CLIENT_INDEX",
          "_connections":[
            {
              "_name": "client__CONSUMING_CLIENT_INDEXconnection1",
              "_factory": "connectionfactory",
              "_sessions":
              [
                {
                  "_sessionName": "client__CONSUMING_CLIENT_INDEXsession1",
                  "_consumers": [
                    {
                      "_name": "client__CONSUMING_CLIENT_INDEXConsumer1Session1",
                      "_destinationName": queueName,
                      "_acknowledgeMode": 0,
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

