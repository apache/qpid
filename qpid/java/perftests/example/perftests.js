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

jsonObject._tests= jsonObject._tests.concat(
    QPID.transform(
    {
      "_name": "Multiple clients: 1 consumer - 1 producer - PERSISTENT - message size=1024",
      "_queues":[
        {
          "_name": "direct://amq.direct//varying-consumers?durable='true'",
          "_durable": true,
          "_attributes":
            {
              "x-qpid-capacity": 10485760,
              "x-qpid-flow-resume-capacity": 8388608
            }
        }
      ],
      "_clients":[
        {
          "_name": "producingClient",
          "_connections":[
            {
              "_name": "connection__INDEX_",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session__INDEX_",
                  "_acknowledgeMode": 1,
                  "_producers": [
                    {
                      "_name": "Producer__INDEX_",
                      "_destinationName": "direct://amq.direct//varying-consumers?durable='true'",
                      "_maximumDuration": 60000,
                      "_deliveryMode": 2,
                      "_messageSize": 1024
                    }
                  ]
                }
              ]
            }
          ]
        },
        {
          "_name": "consumingClient",
          "_connections":[
            {
              "_name": "connection__INDEX_",
              "_factory": "connectionfactory",
              "_sessions": [
                {
                  "_sessionName": "session__INDEX_",
                  "_acknowledgeMode": 1,
                  "_consumers": [
                    {
                      "_name": "Consumer__INDEX_",
                      "_destinationName": "direct://amq.direct//varying-consumers?durable='true'",
                      "_maximumDuration": 60000
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    },
    function(json)
    {
        var producerNumbers = [1, 2, 5, 10];
        var consumerNumbers = [1, 2, 5, 10];
        var results = [];
        for( var i =0; i < producerNumbers.length; i++)
        {
            for( var j = 0; j < consumerNumbers.length; j++)
            {
                var test = QPID.cloneJSON(json);
                test._name = "Multiple clients: " + consumerNumbers[j] + " consumer - " + producerNumbers[i] + " producer";
                results.push(test);
                test._clients[0]._connections = QPID.times(producerNumbers[i], test._clients[0]._connections[0], "__INDEX_")
                test._clients[1]._connections = QPID.times(consumerNumbers[j], test._clients[1]._connections[0], "__INDEX_")
                for (var k =0; k< consumerNumbers[j]; k++)
                {
                    test._clients[1]._connections[k]._sessions[0]._consumers[0]._name = "consumer_" + k;
                    test._clients[1]._connections[k]._sessions[0]._sessionName = "session_" + k;
                    test._clients[1]._connections[k]._name = "connection_" + k;
                    test._clients[1]._connections[k]._sessions[0]._consumers[0]._maximumDuration = 30000;
                }
                for (var k =0; k< producerNumbers[i]; k++)
                {
                    test._clients[0]._connections[k]._sessions[0]._producers[0]._name = "producer_" + k;
                    test._clients[0]._connections[k]._sessions[0]._sessionName = "session_" + k;
                    test._clients[0]._connections[k]._name = "connection_" + k;
                    test._clients[0]._connections[k]._sessions[0]._producers[0]._maximumDuration= 30000;
                    test._queues[0]._attributes["x-qpid-capacity"] = Math.round(10485760/producerNumbers[i]);
                    test._queues[0]._attributes["x-qpid-flow-resume-capacity"] = Math.round(8388608/producerNumbers[i]);
                }
            }
        }
        return results;
    }
    )
);

