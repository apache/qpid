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


         function QueueUpdater()
         {
            this.name = dom.byId("name");
            this.state = dom.byId("state");
            this.durable = dom.byId("durable");
            this.lifetimePolicy = dom.byId("lifetimePolicy");
            this.queueDepthMessages = dom.byId("queueDepthMessages");
            this.queueDepthBytes = dom.byId("queueDepthBytes");
            this.queueDepthBytesUnits = dom.byId("queueDepthBytesUnits");
            this.unacknowledgedMessages = dom.byId("unacknowledgedMessages");
            this.unacknowledgedBytes = dom.byId("unacknowledgedBytes");
            this.unacknowledgedBytesUnits = dom.byId("unacknowledgedBytesUnits");

            var thisObj = this;

            xhr.get({url: "/rest/queue/test/queue", handleAs: "json"}).then(function(data)
                             {
                                thisObj.queueData = data[0];
                                var stats = thisObj.queueData[ "statistics" ];

                                // flatten statistics into attributes
                                for(var propName in stats)
                                {
                                    thisObj.queueData[ propName ] = stats[ propName ];
                                }

                                thisObj.updateHeader();
                                thisObj.bindingsGrid = new UpdatableStore(thisObj.queueData.bindings, "bindings",
                                                         [ { name: "Exchange",    field: "exchange",      width: "90px"},
                                                           { name: "Binding Key", field: "name",          width: "120px"},
                                                           { name: "Arguments",   field: "arguments",     width: "200px"}
                                                         ]);

                                thisObj.consumersGrid = new UpdatableStore(thisObj.queueData.consumers, "consumers",
                                                         [ { name: "Name",    field: "name",      width: "70px"},
                                                           { name: "Mode", field: "distributionMode", width: "70px"},
                                                           { name: "Msgs Rate", field: "msgRate",
                                                           width: "150px"},
                                                           { name: "Bytes Rate", field: "bytesRate",
                                                              width: "150px"}
                                                         ]);



                             });

         }

         QueueUpdater.prototype.updateHeader = function()
         {
            this.name.innerHTML = this.queueData[ "name" ];
            this.state.innerHTML = this.queueData[ "state" ];
            this.durable.innerHTML = this.queueData[ "durable" ];
            this.lifetimePolicy.innerHTML = this.queueData[ "lifetimePolicy" ];

            this.queueDepthMessages.innerHTML = this.queueData["queueDepthMessages"];
            bytesDepth = new formatBytes( this.queueData["queueDepthBytes"] );
            this.queueDepthBytes.innerHTML = "(" + bytesDepth.value;
            this.queueDepthBytesUnits.innerHTML = bytesDepth.units + ")"

            this.unacknowledgedMessages.innerHTML = this.queueData["unacknowledgedMessages"];
            bytesDepth = new formatBytes( this.queueData["unacknowledgedBytes"] );
            this.unacknowledgedBytes.innerHTML = "(" + bytesDepth.value;
            this.unacknowledgedBytesUnits.innerHTML = bytesDepth.units + ")"

         }

         QueueUpdater.prototype.update = function()
         {

            var thisObj = this;

            xhr.get({url: "/rest/queue/test/queue", handleAs: "json"}).then(function(data)
                 {
                    thisObj.queueData = data[0];
                    var stats = thisObj.queueData[ "statistics" ];

                    // flatten statistics into attributes
                    for(var propName in stats)
                    {
                        thisObj.queueData[ propName ] = stats[ propName ];
                    }

                    var bindings = thisObj.queueData[ "bindings" ];
                    for(var i=0; i < bindings.length; i++)
                    {
                        bindings[i].arguments = dojo.toJson(bindings[i].arguments);
                        bindings[i].argumentString = dojo.toJson(bindings[i].arguments);

                    }


                    var consumers = thisObj.queueData[ "consumers" ];
                    if(consumers)
                    {
                        for(var i=0; i < consumers.length; i++)
                        {
                            var stats = consumers[i][ "statistics" ];

                            // flatten statistics into attributes
                            for(var propName in stats)
                            {
                                consumers[i][ propName ] = stats[ propName ];
                            }
                        }
                    }
                    thisObj.updateHeader();


                    // update alerting info
                    alertRepeatGap = new formatTime( thisObj.queueData["alertRepeatGap"] );

                    dom.byId("alertRepeatGap").innerHTML = alertRepeatGap.value;
                    dom.byId("alertRepeatGapUnits").innerHTML = alertRepeatGap.units;


                    alertMsgAge = new formatTime( thisObj.queueData["alertThresholdMessageAge"] );

                    dom.byId("alertThresholdMessageAge").innerHTML = alertMsgAge.value;
                    dom.byId("alertThresholdMessageAgeUnits").innerHTML = alertMsgAge.units;

                    alertMsgSize = new formatBytes( thisObj.queueData["alertThresholdMessageSize"] );

                    dom.byId("alertThresholdMessageSize").innerHTML = alertMsgSize.value;
                    dom.byId("alertThresholdMessageSizeUnits").innerHTML = alertMsgSize.units;

                    alertQueueDepth = new formatBytes( thisObj.queueData["alertThresholdQueueDepthBytes"] );

                    dom.byId("alertThresholdQueueDepthBytes").innerHTML = alertQueueDepth.value;
                    dom.byId("alertThresholdQueueDepthBytesUnits").innerHTML = alertQueueDepth.units;

                    dom.byId("alertThresholdQueueDepthMessages").innerHTML = thisObj.queueData["alertThresholdQueueDepthMessages"];

                    stats = thisObj.queueData[ "statistics" ];

                    var sampleTime = new Date();
                    var messageIn = stats["totalEnqueuedMessages"];
                    var bytesIn = stats["totalEnqueuedBytes"];
                    var messageOut = stats["totalDequeuedMessages"];
                    var bytesOut = stats["totalDequeuedBytes"];

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

                        if(consumers && thisObj.consumers)
                        {
                            for(var i=0; i < consumers.length; i++)
                            {
                                var consumer = consumers[i];
                                for(var j = 0; j < thisObj.consumers.length; j++)
                                {
                                    var oldConsumer = thisObj.consumers[j];
                                    if(oldConsumer.id == consumer.id)
                                    {
                                        var msgRate = (1000 * (consumer.messagesOut - oldConsumer.messagesOut)) /
                                                        samplePeriod;
                                        consumer.msgRate = msgRate.toFixed(0) + "msg/s";

                                        var bytesRate = (1000 * (consumer.bytesOut - oldConsumer.bytesOut)) /
                                                        samplePeriod
                                        var bytesRateFormat = new formatBytes( bytesRate );
                                        consumer.bytesRate = bytesRateFormat.value + bytesRateFormat.units + "/s";
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
                    thisObj.consumers = consumers;

                    // update bindings
                    thisObj.bindingsGrid.update(thisObj.queueData.bindings)

                    // update consumers
                    thisObj.consumersGrid.update(thisObj.queueData.consumers)


                 });
         };

         queueUpdater = new QueueUpdater();

         updateList.push( queueUpdater );

         queueUpdater.update();

         setInterval(function(){
               for(var i = 0; i < updateList.length; i++)
               {
                   var obj = updateList[i];
                   obj.update();
               }}, 5000);
     });

