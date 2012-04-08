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




         function ExchangeUpdater()
         {
            this.name = dom.byId("name");
            this.state = dom.byId("state");
            this.durable = dom.byId("durable");
            this.lifetimePolicy = dom.byId("lifetimePolicy");

            urlQuery = dojo.queryToObject(dojo.doc.location.search.substr((dojo.doc.location.search[0] === "?" ? 1 : 0)));
            this.query = "/rest/exchange/"+ urlQuery.vhost + "/" + urlQuery.exchange;


            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                             {
                                thisObj.exchangeData = data[0];
                                flattenStatistics( thisObj.exchangeData );

                                thisObj.updateHeader();
                                thisObj.bindingsGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                          thisObj.exchangeData.bindings, "bindings",
                                                         [ { name: "Queue",    field: "queue",      width: "90px"},
                                                           { name: "Binding Key", field: "name",          width: "120px"},
                                                           { name: "Arguments",   field: "argumentString",     width: "200px"}
                                                         ]);

                             });

         }

         ExchangeUpdater.prototype.updateHeader = function()
         {
            this.name.innerHTML = this.exchangeData[ "name" ];
            this.state.innerHTML = this.exchangeData[ "state" ];
            this.durable.innerHTML = this.exchangeData[ "durable" ];
            this.lifetimePolicy.innerHTML = this.exchangeData[ "lifetimePolicy" ];

         }

         ExchangeUpdater.prototype.update = function()
         {

            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                 {
                    thisObj.exchangeData = data[0];

                    flattenStatistics( thisObj.exchangeData );

                    var bindings = thisObj.exchangeData[ "bindings" ];
                    var consumers = thisObj.exchangeData[ "consumers" ];

                    for(var i=0; i < bindings.length; i++)
                    {
                        if(bindings[i].arguments)
                        {
                            bindings[i].argumentString = dojo.toJson(bindings[i].arguments);
                        }
                        else
                        {
                            bindings[i].argumentString = "";
                        }
                    }



                    thisObj.updateHeader();


                    stats = thisObj.exchangeData[ "statistics" ];

                    var messageIn = thisObj.exchangeData["messagesIn"];
                    var bytesIn = thisObj.exchangeData["bytesIn"];
                    var messageDrop = thisObj.exchangeData["messagesDropped"];
                    var bytesDrop = thisObj.exchangeData["bytesDropped"];

                    if(thisObj.sampleTime)
                    {
                        var samplePeriod = sampleTime.getTime() - thisObj.sampleTime.getTime();

                        var msgInRate = (1000 * (messageIn - thisObj.messageIn)) / samplePeriod;
                        var msgDropRate = (1000 * (messageDrop - thisObj.messageDrop)) / samplePeriod;
                        var bytesInRate = (1000 * (bytesIn - thisObj.bytesIn)) / samplePeriod;
                        var bytesDropRate = (1000 * (bytesDrop - thisObj.bytesDrop)) / samplePeriod;

                        dom.byId("msgInRate").innerHTML = msgInRate.toFixed(0);
                        bytesInFormat = new formatBytes( bytesInRate );
                        dom.byId("bytesInRate").innerHTML = "(" + bytesInFormat.value;
                        dom.byId("bytesInRateUnits").innerHTML = bytesInFormat.units + "/s)"

                        dom.byId("msgDropRate").innerHTML = msgDropRate.toFixed(0);
                        bytesDropFormat = new formatBytes( bytesDropRate );
                        dom.byId("bytesDropRate").innerHTML = "(" + bytesDropFormat.value;
                        dom.byId("bytesDropRateUnits").innerHTML = bytesDropFormat.units + "/s)"

                    }

                    thisObj.sampleTime = sampleTime;
                    thisObj.messageIn = messageIn;
                    thisObj.bytesIn = bytesIn;
                    thisObj.messageDrop = messageDrop;
                    thisObj.bytesDrop = bytesDrop;
                    thisObj.consumers = consumers;

                    // update bindings
                    thisObj.bindingsGrid.update(thisObj.exchangeData.bindings)

                 });
         };

         exchangeUpdater = new ExchangeUpdater();

         updateList.push( exchangeUpdater );

         exchangeUpdater.update();

         setInterval(function(){
               for(var i = 0; i < updateList.length; i++)
               {
                   var obj = updateList[i];
                   obj.update();
               }}, 5000);
     });

