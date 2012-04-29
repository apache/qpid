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
var vhostGrid, dataStore, store, vhostStore;
var exchangeGrid, exchangeStore, exchangeDataStore;
var updateList = new Array();
var vhostTuple, exchangesTuple;


require(["dojo/store/JsonRest",
				"dojo/store/Memory",
				"dojo/store/Cache",
				"dojox/grid/DataGrid",
				"dojo/data/ObjectStore",
				"dojo/query",
				"dojo/store/Observable",
                "dojo/_base/xhr",
				"dojo/domReady!"],
	     function(JsonRest, Memory, Cache, DataGrid, ObjectStore, query, Observable, xhr)
	     {


         function UpdatableStore( query, divName, structure, func ) {


             this.query = query;

             var thisObj = this;

             xhr.get({url: query, handleAs: "json"}).then(function(data)
                             {
                             thisObj.store = Observable(Memory({data: data, idProperty: "id"}));
                             thisObj.dataStore = ObjectStore({objectStore: thisObj.store});
                             thisObj.grid = new DataGrid({
                                         store: thisObj.dataStore,
                                         structure: structure,
             					                }, divName);

                             // since we created this grid programmatically, call startup to render it
                             thisObj.grid.startup();

                             updateList.push( thisObj );
                             if( func )
                             {
                                 func(thisObj);
                             }
                             });


         }

         UpdatableStore.prototype.update = function() {
             var store = this.store;


             xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                 {
                     // handle deletes
                     // iterate over existing store... if not in new data then remove
                     store.query({ }).forEach(function(object)
                         {
                             for(var i=0; i < data.length; i++)
                             {
                                 if(data[i].id == object.id)
                                 {
                                     return;
                                 }
                             }
                             store.remove(object.id);
                             //store.notify(null, object.id);
                         });

                     // iterate over data...
                     for(var i=0; i < data.length; i++)
                     {
                         if(item = store.get(data[i].id))
                         {
                             var modified;
                             for(var propName in data[i])
                             {
                                 if(item[ propName ] != data[i][ propName ])
                                 {
                                     item[ propName ] = data[i][ propName ];
                                     modified = true;
                                 }
                             }
                             if(modified)
                             {
                                 // ... check attributes for updates
                                 store.notify(item, data[i].id);
                             }
                         }
                         else
                         {
                             // ,,, if not in the store then add
                             store.put(data[i]);
                             //store.notify(data[i], null);
                         }
                     }
                 });
         };

         exchangeTuple = new UpdatableStore("/rest/exchange", "exchanges",
                                                     [ { name: "Name",        field: "name",          width: "190px"},
                                                       { name: "Type",        field: "type",          width: "90px"},
                                                       { name: "Durable",     field: "durable",       width: "80px"},
                                                       { name: "Auto-Delete", field: "auto-delete",   width: "100px"},
                                                       { name: "Bindings",    field: "binding-count", width: "80px"}
                                                       ]);
         queueTuple = new UpdatableStore("/rest/queue", "queues",
                                            [ { name: "Name",        field: "name",          width: "240px"},
                                              { name: "Durable",     field: "durable",       width: "100px"},
                                              { name: "Auto-Delete", field: "auto-delete",   width: "100px"},
                                              { name: "Bindings",    field: "binding-count", width: "100px"} ]);
         connectionTuple = new UpdatableStore("/rest/connection", "connections",
                                                     [ { name: "Name",        field: "name",          width: "160px"},
                                                       { name: "Sessions",    field: "session-count", width: "80px"},
                                                       { name: "Msgs In",     field: "msgs-in-total", width: "80px"},
                                                       { name: "Msgs Out",    field: "msgs-out-total",width: "80px"},
                                                       { name: "Bytes In",    field: "bytes-in-total", width: "80px"},
                                                       { name: "Bytes Out",   field: "bytes-out-total",width: "80px"}]);

         vhostTuple = new UpdatableStore("/rest/virtualhost", "virtualHosts",
                                                 [ { name: "Name",        field: "name",             width: "180px"},
                                                   { name: "Connections", field: "connection-count", width: "90px"},
                                                   { name: "Queues",      field: "queue-count",      width: "90px"},
                                                   { name: "Exchanges",   field: "exchange-count",   width: "90px"},
                                                   { name: "Msgs In",     field: "msgs-in-total", width: "80px"},
                                                   { name: "Msgs Out",    field: "msgs-out-total",width: "80px"},
                                                   { name: "Bytes In",    field: "bytes-in-total", width: "80px"},
                                                   { name: "Bytes Out",   field: "bytes-out-total",width: "80px"} ],
                                        function(obj)
                                        {
                                            dojo.connect(obj.grid, "onRowClick", obj.grid, function(evt){
                                                        					var idx = evt.rowIndex,
                                                        				    item = this.getItem(idx);

                                                                            exchangeTuple.query = "/rest/exchange/"+obj.dataStore.getValue(item, "name")+"/";
                                                                            exchangeTuple.update();
                                                                            queueTuple.query =  "/rest/queue/"+obj.dataStore.getValue(item, "name")+"/";
                                                                            queueTuple.update();
                                                                            connectionTuple.query = "/rest/connection/"+obj.dataStore.getValue(item, "name")+"/";
                                                                            connectionTuple.update();

                                                        				});
                                        });


                setInterval(function(){
                   for(var i = 0; i < updateList.length; i++)
                   {
                       var obj = updateList[i];
                       obj.update();
                   }}, 5000); // every second
		 });

