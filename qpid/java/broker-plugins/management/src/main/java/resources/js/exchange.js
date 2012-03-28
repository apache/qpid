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
                                var stats = thisObj.exchangeData[ "statistics" ];

                                // flatten statistics into attributes
                                for(var propName in stats)
                                {
                                    thisObj.exchangeData[ propName ] = stats[ propName ];
                                }

                                thisObj.updateHeader();
                                thisObj.bindingsGrid = new UpdatableStore(thisObj.exchangeData.bindings, "bindings",
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
                    var stats = thisObj.exchangeData[ "statistics" ];

                    // flatten statistics into attributes
                    for(var propName in stats)
                    {
                        thisObj.exchangeData[ propName ] = stats[ propName ];
                    }

                    var bindings = thisObj.exchangeData[ "bindings" ];
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


                    var consumers = thisObj.exchangeData[ "consumers" ];
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


                    stats = thisObj.exchangeData[ "statistics" ];

                    var sampleTime = new Date();
                    var messageIn = stats["messagesIn"];
                    var bytesIn = stats["bytesIn"];
                    var messageDrop = stats["messagesDropped"];
                    var bytesDrop = stats["bytesDropped"];

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

