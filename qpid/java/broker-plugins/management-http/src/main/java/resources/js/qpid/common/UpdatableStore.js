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
define(["dojo/json",
        "qpid/common/util",
        "dojo/store/Memory",
        "dojox/grid/DataGrid",
        "dojo/data/ObjectStore",
        "dojo/store/Observable"], function (json, util, Memory, DataGrid, ObjectStore, Observable) {

    function UpdatableStore( data, divName, structure, func, props, Grid, notObservable ) {

        var that = this;
        var GridType = DataGrid;

        that.memoryStore = new Memory({data: data, idProperty: "id"});
        that.store = notObservable ? that.memoryStore : new Observable(that.memoryStore);
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
        var changed = false;
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
                                     changed = true;
                                 });

        // iterate over data...
        if(data) {
            for(var i=0; i < data.length; i++)
            {
                if(theItem = store.get(data[i].id))
                {
                  var modified = !util.equals(theItem, data[i]);
                  if(modified)
                  {
                    if (store.notify)
                    {
                      // Seems that we are required to update the item that the store already holds
                      for(var propName in data[i])
                      {
                        if(data[i].hasOwnProperty(propName))
                        {
                          if(theItem[ propName ] != data[i][ propName ])
                          {
                            theItem[ propName ] = data[i][ propName ];
                          }
                        }
                      }
                      // and tell it we have done so
                      store.notify(theItem, data[i].id);
                    }
                    else
                    {
                      store.put(data[i], {overwrite: true});
                    }
                    changed = true;
                  }
                } else {
                    // if not in the store then add
                    store.put(data[i]);
                    changed = true;
                }
            }
        }

        return changed;
    };

    function removeItemsFromArray(items, numberToRemove)
    {
      if (items)
      {
        if (numberToRemove > 0 && items.length > 0)
        {
          if (numberToRemove >= items.length)
          {
            numberToRemove = numberToRemove - items.length;
            items.length = 0
          }
          else
          {
            items.splice(0, numberToRemove);
            numberToRemove = 0;
          }
        }
      }
      return numberToRemove;
    };

    UpdatableStore.prototype.append = function(data, limit)
    {
        var changed = false;
        var items = this.memoryStore.data;

        if (limit)
        {
          var totalSize = items.length + (data ? data.length : 0);
          var numberToRemove = totalSize - limit;

          if (numberToRemove > 0)
          {
            changed = true;
            numberToRemove = removeItemsFromArray(items, numberToRemove);
            if (numberToRemove > 0)
            {
              removeItemsFromArray(data, numberToRemove);
            }
          }
        }

        if (data && data.length > 0)
        {
          changed = true;
          items.push.apply(items, data);
        }

        this.memoryStore.setData(items);
        return changed;
    };

    UpdatableStore.prototype.close = function()
    {
        this.dataStore.close();
        this.dataStore = null;
        this.store = null;
        this.memoryStore = null;
    };
    return UpdatableStore;
});
