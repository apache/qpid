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

for (var i=0; i<2; i++)
{
    var deliveryMode = i+1;
    var acknowledgeMode = ((i==0) ? 1 : 0);
    var durable = (deliveryMode == 2);
    var suffix = durable ? "PERSISTENT" : "NON-PERSISTENT";
    var queueName = "direct://amq.direct//queue-selectors-" + suffix + "?durable='" + durable + "'";
    var consumerNumbers = [1, 2, 4, 8, 16, 32];
    for (var j=0; j<consumerNumbers.length; j++)
    {
        var consumerNumber = consumerNumbers[j];
        var testName = "Queues with selectors: " +consumerNumber + " consumers - 1 producer - non overlapping - " + suffix;
        var test = {
                "_name": testName,
                "_queues":[
                  {
                    "_name": queueName,
                    "_durable": durable,
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
                    "_messageProviders": [
                      {
                          "_name": "messageProvider",
                          "_messageProperties": {
                              "id": {
                                  "@def": "range",
                                  "_lower": 1,
                                  "_upper": consumerNumber,
                                  "_type": "int"
                              }
                          }
                      }
                    ],
                    "_connections":[
                      {
                        "_name": "connection1",
                        "_factory": "connectionfactory",
                        "_sessions": [
                          {
                            "_sessionName": "session1",
                            "_acknowledgeMode": acknowledgeMode,
                            "_producers": [
                              {
                                "_name": "Producer1",
                                "_destinationName": queueName,
                                "_maximumDuration": 60000,
                                "_deliveryMode": deliveryMode,
                                "_messageSize": 1024,
                                "_messageProviderName": "messageProvider"
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  },
                  {
                    "_name": "consumingClient",
                    "_connections":[]
                  }
                ]
              };
        for(var n=0; n<consumerNumber; n++)
        {
            var consumerConnection = {
                    "_name": "connection" + n,
                    "_factory": "connectionfactory",
                    "_sessions": [
                      {
                        "_sessionName": "session" + n,
                        "_acknowledgeMode": acknowledgeMode,
                        "_consumers": [
                          {
                            "_name": "Consumer" + n,
                            "_destinationName": queueName,
                            "_maximumDuration": 60000,
                            "_selector": "id=" + ( n + 1)
                          }
                        ]
                      }
                    ]
                  };
            test._clients[1]._connections.push(consumerConnection);
        }
        jsonObject._tests.push(test);
    }
}

