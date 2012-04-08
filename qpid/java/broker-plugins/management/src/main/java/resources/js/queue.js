var updateList = new Array();
var bindingsTuple;


require(["dojo/store/JsonRest",
         "dojo/json",
				"dojo/store/Memory",
				"dojo/store/Cache",
				"dojox/grid/DataGrid",
				"dojo/data/ObjectStore",
				"dojo/query",
				"dojo/store/Observable",
                "dojo/_base/xhr",
                "dojo/dom",
				"dojo/domReady!"],
	     function(JsonRest, json, Memory, Cache, DataGrid, ObjectStore, query, Observable, xhr, dom)
	     {

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

            urlQuery = dojo.queryToObject(dojo.doc.location.search.substr((dojo.doc.location.search[0] === "?" ? 1 : 0)));
            this.query = "/rest/queue/"+ urlQuery.vhost + "/" + urlQuery.queue;


            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                             {
                                thisObj.queueData = data[0];

                                flattenStatistics( thisObj.queueData );

                                thisObj.updateHeader();
                                thisObj.bindingsGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                          thisObj.queueData.bindings, "bindings",
                                                         [ { name: "Exchange",    field: "exchange",      width: "90px"},
                                                           { name: "Binding Key", field: "name",          width: "120px"},
                                                           { name: "Arguments",   field: "argumentString",     width: "200px"}
                                                         ]);

                                thisObj.consumersGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                           thisObj.queueData.consumers, "consumers",
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

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                 {
                    thisObj.queueData = data[0];
                    flattenStatistics( thisObj.queueData )

                    var bindings = thisObj.queueData[ "bindings" ];
                    var consumers = thisObj.queueData[ "consumers" ];

                    for(var i=0; i < bindings.length; i++)
                    {
                        bindings[i].argumentString = json.stringify(bindings[i].arguments);
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

                    var sampleTime = new Date();
                    var messageIn = thisObj.queueData["totalEnqueuedMessages"];
                    var bytesIn = thisObj.queueData["totalEnqueuedBytes"];
                    var messageOut = thisObj.queueData["totalDequeuedMessages"];
                    var bytesOut = thisObj.queueData["totalDequeuedBytes"];

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

