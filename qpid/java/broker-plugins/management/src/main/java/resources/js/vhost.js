var updateList = new Array();
var bindingsTuple;


require(["dojo/store/JsonRest",
				"dojo/store/Memory",
				"dojo/store/Cache",
				"dojox/grid/DataGrid",
				"dojo/data/ObjectStore",
				"dojo/query",
				"dojo/store/Observable",
                "dojo/_base/xhr",
                "dojo/dom",
				"dojo/domReady!"],
	     function(JsonRest, Memory, Cache, DataGrid, ObjectStore, query, Observable, xhr, dom)
	     {


             function UpdatableStore( data, divName, structure, func )
             {



                 var thisObj = this;


                 thisObj.store = Observable(Memory({data: data, idProperty: "id"}));
                 thisObj.dataStore = ObjectStore({objectStore: thisObj.store});
                 thisObj.grid = new DataGrid({
                             store: thisObj.dataStore,
                             structure: structure,
                             autoHeight: true
                                    }, divName);

                 // since we created this grid programmatically, call startup to render it
                 thisObj.grid.startup();


                 if( func )
                 {
                     func(thisObj);
                 }

             }

            UpdatableStore.prototype.update = function(bindingData)
            {
                 data = bindingData;
                 var store = this.store;


                 // handle deletes
                 // iterate over existing store... if not in new data then remove
                 store.query({ }).forEach(function(object)
                     {
                         if(data)
                         {
                             for(var i=0; i < data.length; i++)
                             {
                                 if(data[i].id == object.id)
                                 {
                                     return;
                                 }
                             }
                         }
                         store.remove(object.id);
                         //store.notify(null, object.id);
                     });

                 // iterate over data...
                 if(data)
                 {
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
                         }
                     }
                 }

            };



         function formatBytes(amount)
         {
            this.units = "B";
            this.value = 0;

            if(amount < 1000)
            {
                this.units = "B";
                this.value = amount;
            }
            else if(amount < 1000 * 1024)
            {
                this.units = "KB";
                this.value = amount / 1024
                this.value = this.value.toPrecision(3);
            }
            else if(amount < 1000 * 1024 * 1024)
            {
                this.units = "MB";
                this.value = amount / (1024 * 1024)
                this.value = this.value.toPrecision(3);
            }
            else if(amount < 1000 * 1024 * 1024 * 1024)
            {
                this.units = "GB";
                this.value = amount / (1024 * 1024 * 1024)
                this.value = this.value.toPrecision(3);
            }

         }


         function formatTime(amount)
         {
            this.units = "ms";
            this.value = 0;

            if(amount < 1000)
            {
                this.units = "ms";
                this.value = amount;
            }
            else if(amount < 1000 * 60)
            {
                this.units = "s";
                this.value = amount / 1000
                this.value = this.value.toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60)
            {
                this.units = "min";
                this.value = amount / (1000 * 60)
                this.value = this.value.toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60 * 24)
            {
                this.units = "hr";
                this.value = amount / (1000 * 60 * 60)
                this.value = this.value.toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60 * 24 * 7)
            {
                this.units = "d";
                this.value = amount / (1000 * 60 * 60 * 24)
                this.value = this.value.toPrecision(3);
            }
            else if(amount < 1000 * 60 * 60 * 24 * 365)
            {
                this.units = "wk";
                this.value = amount / (1000 * 60 * 60 * 24 * 7)
                this.value = this.value.toPrecision(3);
            }
            else
            {
                this.units = "yr";
                this.value = amount / (1000 * 60 * 60 * 24 * 365)
                this.value = this.value.toPrecision(3);
            }

         }

         function flattenStatistics(data)
         {
             var stats = data[ "statistics" ];

             // flatten statistics into attributes
             for(var propName in stats)
             {
                 data[ propName ] = stats[ propName ];
             }

         }


         function Updater()
         {
            this.name = dom.byId("name");
            this.state = dom.byId("state");
            this.durable = dom.byId("durable");
            this.lifetimePolicy = dom.byId("lifetimePolicy");

            urlQuery = dojo.queryToObject(dojo.doc.location.search.substr((dojo.doc.location.search[0] === "?" ? 1 : 0)));
            this.query = "/rest/virtualhost/"+ urlQuery.vhost ;


            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                             {
                                thisObj.vhostData = data[0];
                                var stats = thisObj.vhostData[ "statistics" ];

                                // flatten statistics into attributes
                                flattenStatistics( thisObj.vhostData );
                                if(thisObj.vhostData.queues)
                                {
                                    for(var i = 0; i < thisObj.vhostData.queues.length; i++)
                                    {
                                        flattenStatistics( thisObj.vhostData.queues[i]);
                                    }
                                }
                                if(thisObj.vhostData.exchanges)
                                {
                                    for(var i = 0; i < thisObj.vhostData.exchanges.length; i++)
                                    {
                                        flattenStatistics( thisObj.vhostData.exchanges[i]);
                                    }
                                }

                                thisObj.updateHeader();
                                thisObj.queuesGrid = new UpdatableStore(thisObj.vhostData.queues, "queues",
                                                         [ { name: "Name",    field: "name",      width: "90px"},
                                                           { name: "Messages", field: "queueDepthMessages", width: "90px"},
                                                           { name: "Arguments",   field: "arguments",     width: "200px"}
                                                         ],
                                                         function(obj)
                                                         {
                                                             dojo.connect(obj.grid, "onRowDblClick", obj.grid,
                                                             function(evt){
                                                                    var idx = evt.rowIndex,
                                                                    item = this.getItem(idx);

                                                                    url = "/queue?vhost="
                                                                     + thisObj.vhostData.name + "&queue=" +
                                                                    obj.dataStore.getValue(item,"name");

                                                                    window.location = url;

                                                            });
                                                         } );

                                thisObj.exchangesGrid = new UpdatableStore(thisObj.vhostData.exchanges, "exchanges",
                                                         [ { name: "Name",    field: "name",      width: "120px"},
                                                           { name: "Type", field: "type", width: "120px"},
                                                           { name: "Binding Count", field: "bindingCount",
                                                           width: "90px"}
                                                         ],
                                                           function(obj)
                                                           {
                                                               dojo.connect(obj.grid, "onRowDblClick", obj.grid,
                                                               function(evt){
                                                                      var idx = evt.rowIndex,
                                                                      item = this.getItem(idx);

                                                                      url = "/exchange?vhost="
                                                                       + thisObj.vhostData.name + "&exchange=" +
                                                                      obj.dataStore.getValue(item,"name");

                                                                      window.location = url;

                                                              });
                                                           } );


                                thisObj.connectionsGrid = new UpdatableStore(thisObj.vhostData.connections,
                                                         "connections",
                                                         [ { name: "Name",    field: "name",      width: "150px"},
                                                           { name: "Sessions", field: "sessionCount", width: "70px"},
                                                           { name: "Msgs In", field: "msgInRate",
                                                           width: "80px"},
                                                           { name: "Bytes In", field: "bytesInRate",
                                                              width: "80px"},
                                                           { name: "Msgs Out", field: "msgOutRate",
                                                           width: "80px"},
                                                           { name: "Bytes Out", field: "bytesOutRate",
                                                              width: "80px"}
                                                         ]);



                             });

         }

         Updater.prototype.updateHeader = function()
         {
            this.name.innerHTML = this.vhostData[ "name" ];
            this.state.innerHTML = this.vhostData[ "state" ];
            this.durable.innerHTML = this.vhostData[ "durable" ];
            this.lifetimePolicy.innerHTML = this.vhostData[ "lifetimePolicy" ];


         }

         Updater.prototype.update = function()
         {

            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                 {
                    thisObj.vhostData = data[0];
                    var stats = thisObj.vhostData[ "statistics" ];

                    // flatten statistics into attributes
                    for(var propName in stats)
                    {
                        thisObj.vhostData[ propName ] = stats[ propName ];
                    }



                    var connections = thisObj.vhostData[ "connections" ];
                    if(connections)
                    {
                        for(var i=0; i < connections.length; i++)
                        {
                            var stats = connections[i][ "statistics" ];

                            // flatten statistics into attributes
                            for(var propName in stats)
                            {
                                connections[i][ propName ] = stats[ propName ];
                            }
                        }
                    }
                    var queues = thisObj.vhostData[ "queues" ];
                    if(queues)
                    {
                        for(var i=0; i < queues.length; i++)
                        {
                            var stats = queues[i][ "statistics" ];

                            // flatten statistics into attributes
                            for(var propName in stats)
                            {
                                queues[i][ propName ] = stats[ propName ];
                            }
                        }
                    }
                    var exchanges = thisObj.vhostData[ "exchanges" ];
                    if(exchanges)
                    {
                        for(var i=0; i < exchanges.length; i++)
                        {
                            var stats = exchanges[i][ "statistics" ];

                            // flatten statistics into attributes
                            for(var propName in stats)
                            {
                                exchanges[i][ propName ] = stats[ propName ];
                            }
                        }
                    }
                    thisObj.updateHeader();


                    // update alerting info
                    alertRepeatGap = new formatTime( thisObj.vhostData["alertRepeatGap"] );

                    dom.byId("alertRepeatGap").innerHTML = alertRepeatGap.value;
                    dom.byId("alertRepeatGapUnits").innerHTML = alertRepeatGap.units;


                    alertMsgAge = new formatTime( thisObj.vhostData["alertThresholdMessageAge"] );

                    dom.byId("alertThresholdMessageAge").innerHTML = alertMsgAge.value;
                    dom.byId("alertThresholdMessageAgeUnits").innerHTML = alertMsgAge.units;

                    alertMsgSize = new formatBytes( thisObj.vhostData["alertThresholdMessageSize"] );

                    dom.byId("alertThresholdMessageSize").innerHTML = alertMsgSize.value;
                    dom.byId("alertThresholdMessageSizeUnits").innerHTML = alertMsgSize.units;

                    alertQueueDepth = new formatBytes( thisObj.vhostData["alertThresholdQueueDepthBytes"] );

                    dom.byId("alertThresholdQueueDepthBytes").innerHTML = alertQueueDepth.value;
                    dom.byId("alertThresholdQueueDepthBytesUnits").innerHTML = alertQueueDepth.units;

                    dom.byId("alertThresholdQueueDepthMessages").innerHTML = thisObj.vhostData["alertThresholdQueueDepthMessages"];

                    stats = thisObj.vhostData[ "statistics" ];

                    var sampleTime = new Date();
                    var messageIn = stats["messagesIn"];
                    var bytesIn = stats["bytesIn"];
                    var messageOut = stats["messagesOut"];
                    var bytesOut = stats["bytesOut"];

                    if(thisObj.sampleTime)
                    {
                        var samplePeriod = sampleTime.getTime() - thisObj.sampleTime.getTime();

                        var msgInRate = (1000 * (messageIn - thisObj.messageIn)) / samplePeriod;
                        var msgOutRate = (1000 * (messageOut - thisObj.messageOut)) / samplePeriod;
                        var bytesInRate = (1000 * (bytesIn - thisObj.bytesIn)) / samplePeriod;
                        var bytesOutRate = (1000 * (bytesOut - thisObj.bytesOut)) / samplePeriod;

                        dom.byId("msgInRate").innerHTML = msgInRate.toFixed(0);
                        bytesInFormat = new formatBytes( bytesInRate );
                        dom.byId("bytesInRate").innerHTML = "(" + bytesInFormat.value;
                        dom.byId("bytesInRateUnits").innerHTML = bytesInFormat.units + "/s)"

                        dom.byId("msgOutRate").innerHTML = msgOutRate.toFixed(0);
                        bytesOutFormat = new formatBytes( bytesOutRate );
                        dom.byId("bytesOutRate").innerHTML = "(" + bytesOutFormat.value;
                        dom.byId("bytesOutRateUnits").innerHTML = bytesOutFormat.units + "/s)"

                        if(connections && thisObj.connections)
                        {
                            for(var i=0; i < connections.length; i++)
                            {
                                var connection = connections[i];
                                for(var j = 0; j < thisObj.connections.length; j++)
                                {
                                    var oldConnection = thisObj.connections[j];
                                    if(oldConnection.id == connection.id)
                                    {
                                        var msgOutRate = (1000 * (connection.messagesOut - oldConnection.messagesOut)) /
                                                        samplePeriod;
                                        connection.msgOutRate = msgOutRate.toFixed(0) + "msg/s";

                                        var bytesOutRate = (1000 * (connection.bytesOut - oldConnection.bytesOut)) /
                                                        samplePeriod
                                        var bytesOutRateFormat = new formatBytes( bytesOutRate );
                                        connection.bytesOutRate = bytesOutRateFormat.value + bytesOutRateFormat.units + "/s";


                                        var msgInRate = (1000 * (connection.messagesIn - oldConnection.messagesIn)) /
                                                                                                samplePeriod;
                                        connection.msgInRate = msgInRate.toFixed(0) + "msg/s";

                                        var bytesInRate = (1000 * (connection.bytesIn - oldConnection.bytesIn)) /
                                                        samplePeriod
                                        var bytesInRateFormat = new formatBytes( bytesInRate );
                                        connection.bytesInRate = bytesInRateFormat.value + bytesInRateFormat.units + "/s";
                                    }


                                }

                            }
                        }
                    }

                    thisObj.sampleTime = sampleTime;
                    thisObj.messageIn = messageIn;
                    thisObj.bytesIn = bytesIn;
                    thisObj.messageOut = messageOut;
                    thisObj.bytesOut = bytesOut;
                    thisObj.connections = connections;

                    // update queues
                    thisObj.queuesGrid.update(thisObj.vhostData.queues)

                    // update exchanges
                    thisObj.exchangesGrid.update(thisObj.vhostData.exchanges)

                    // update connections
                    thisObj.connectionsGrid.update(thisObj.vhostData.connections)


                 });
         };

         updater = new Updater();

         updateList.push( updater );

         updater.update();

         setInterval(function(){
               for(var i = 0; i < updateList.length; i++)
               {
                   var obj = updateList[i];
                   obj.update();
               }}, 5000);
     });

