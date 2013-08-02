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
define(["dojo/dom",
        "dijit/registry",
        "dijit/layout/ContentPane",
        "dojox/html/entities",
        "qpid/management/Broker",
        "qpid/management/VirtualHost",
        "qpid/management/Exchange",
        "qpid/management/Queue",
        "qpid/management/Connection",
        "qpid/management/AuthenticationProvider",
        "qpid/management/GroupProvider",
        "qpid/management/group/Group",
        "qpid/management/KeyStore",
        "qpid/management/TrustStore",
        "qpid/management/AccessControlProvider",
        "qpid/management/Port",
        "qpid/management/Plugin",
        "qpid/management/logs/LogViewer",
        "dojo/ready",
        "dojo/domReady!"],
       function (dom, registry, ContentPane, entities, Broker, VirtualHost, Exchange, Queue, Connection, AuthProvider,
                 GroupProvider, Group, KeyStore, TrustStore, AccessControlProvider, Port, Plugin, LogViewer, ready) {
           var controller = {};

           var constructors = { broker: Broker, virtualhost: VirtualHost, exchange: Exchange,
                                queue: Queue, connection: Connection,
                                authenticationprovider: AuthProvider, groupprovider: GroupProvider,
                                group: Group, keystore: KeyStore, truststore: TrustStore,
                                accesscontrolprovider: AccessControlProvider, port: Port,
                                plugin: Plugin, logViewer: LogViewer};

           var tabDiv = dom.byId("managedViews");

           ready(function() {
               controller.tabContainer = registry.byId("managedViews");
           });


           controller.viewedObjects = {};

           controller.show = function(objType, name, parent) {

               function generateName(obj)
               {
                    if(obj) {
                        var name = "";
                        if(obj.parent)
                        {
                            for(var prop in obj.parent) {
                                if(obj.parent.hasOwnProperty(prop)) {
                                    name = name + generateName( obj.parent[ prop ]);
                                }
                            }

                        }
                        return name + parent.type +":" + parent.name + "/"
                    }
               }

               var that = this;
               var objId = generateName(parent) + objType+":"+name;
               if( this.viewedObjects[ objId ] ) {
                   this.tabContainer.selectChild(this.viewedObjects[ objId ].contentPane);
               } else {
                   var Constructor = constructors[ objType ];
                   if(Constructor) {
                       var obj = new Constructor(name, parent, this);
                       this.viewedObjects[ objId ] = obj;

                       var contentPane = new ContentPane({ region: "center" ,
                                                           title: entities.encode(obj.getTitle()),
                                                           closable: true,
                                                           onClose: function() {
                                                               obj.close();
                                                               delete that.viewedObjects[ objId ];
                                                               return true;
                                                           }
                       });
                       this.tabContainer.addChild( contentPane );
                       obj.open(contentPane);
                       contentPane.startup();
                       if(obj.startup) {
                           obj.startup();
                       }
                       this.tabContainer.selectChild( contentPane );
                   }

               }

           };

           ready(function() {
               controller.show("broker","");
           });


           return controller;
       });
