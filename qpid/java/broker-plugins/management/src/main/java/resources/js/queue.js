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
                "dojo/dom",
				"dojo/domReady!"],
	     function(JsonRest, Memory, Cache, DataGrid, ObjectStore, query, Observable, xhr, dom)
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
                         }
                     }
                 });
         };


         queueUpdater = new Object();

         function formatBytes(amount)
         {
            this.units = "bytes";
            this.value = 0;

            if(amount < 1000)
            {
                this.units = "bytes";
                this.value = amount;
            }
            else if(amount < 1000 * 1024)
            {
                this.units = "Kb";
                this.value = amount / 1024
                this.value = this.value.toPrecision(3);
            }
            else if(amount < 1000 * 1024 * 1024)
            {
                this.units = "Mb";
                this.value = amount / (1024 * 1024)
                this.value = this.value.toPrecision(3);
            }

         }

         queueUpdater.update = function() {
            xhr.get({url: "/rest/queue/test/queue", handleAs: "json"}).then(function(data)
                 {

                    queueData = data[0];
                    stats = queueData[ "statistics" ];
                    dom.byId("name").innerHTML = queueData[ "name" ];
                    dom.byId("state").innerHTML = queueData[ "state" ];
                    dom.byId("durable").innerHTML = queueData[ "durable" ];
                    dom.byId("lifetimePolicy").innerHTML = queueData[ "lifetimePolicy" ];

                    dom.byId("queueDepthMessages").innerHTML = stats["queueDepthMessages"];
                    bytesDepth = new formatBytes( stats["queueDepthBytes"] );
                    dom.byId("queueDepthBytes").innerHTML = "(" + bytesDepth.value;
                    dom.byId("queueDepthBytesUnits").innerHTML = bytesDepth.units + ")"


                 });
         };

         updateList.push( queueUpdater );

         queueUpdater.update();

         setInterval(function(){
               for(var i = 0; i < updateList.length; i++)
               {
                   var obj = updateList[i];
                   obj.update();
               }}, 5000);
     });

