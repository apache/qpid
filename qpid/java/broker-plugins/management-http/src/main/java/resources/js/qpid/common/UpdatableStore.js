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
define(["dojo/store/Memory",
				"dojox/grid/DataGrid",
				"dojo/data/ObjectStore",
				"dojo/store/Observable"], function (Memory, DataGrid, ObjectStore, Observable) {

    function UpdatableStore( data, divName, structure, func, props, Grid ) {

        var that = this;
        var GridType = DataGrid;

        that.store = Observable(Memory({data: data, idProperty: "id"}));
        that.dataStore = ObjectStore({objectStore: that.store});

        var gridProperties = {  store: that.dataStore,
                     structure: structure,
                      autoHeight: true
                 };
        if(props) {
            for(var prop in props) {
                if(props.hasOwnProperty(prop))
                {
                    gridProperties[ prop ] = props[ prop ];
                }
            }
        }

        if(Grid)
        {
            GridType = Grid;
        }

        that.grid = new GridType(gridProperties, divName);

        // since we created this grid programmatically, call startup to render it
        that.grid.startup();

        if( func )
        {
            func(that);
        }

    }

    UpdatableStore.prototype.update = function(data)
    {

        var store = this.store;
        var theItem;

        // handle deletes
        // iterate over existing store... if not in new data then remove
        store.query({ }).forEach(function(object) {
                                     if(data) {
                                         for(var i=0; i < data.length; i++) {
                                             if(data[i].id == object.id) {
                                                 return;
                                             }
                                         }
                                     }
                                     store.remove(object.id);

                                 });

        // iterate over data...
        if(data) {
            for(var i=0; i < data.length; i++) {
                if(theItem = store.get(data[i].id)) {
                    var modified;
                    for(var propName in data[i]) {
                        if(data[i].hasOwnProperty(propName)) {
                            if(theItem[ propName ] != data[i][ propName ]) {
                                theItem[ propName ] = data[i][ propName ];
                                modified = true;
                            }
                        }
                    }
                    if(modified) {
                        // ... check attributes for updates
                        store.notify(theItem, data[i].id);
                    }
                } else {
                    // ,,, if not in the store then add
                    store.put(data[i]);
                }
            }
        }

    };
    return UpdatableStore;
});
