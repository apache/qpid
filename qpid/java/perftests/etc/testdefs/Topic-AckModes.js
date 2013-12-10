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
var duration = 30000;
var topicName = "topic://amq.topic/?routingkey='testTopic'";

var jsonObject = {
    _tests: [
    {
      "_name": "Topic ack modes",
      "_iterations": [
        {
          "_acknowledgeMode": 0
        },
        {
          "_acknowledgeMode": 1
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
                      "_isTopic": true,
                      "_deliveryMode": 1,
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

