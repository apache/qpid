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


define(["dojo/_base/xhr",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/_base/lang",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/UpdatableStore",
        "qpid/common/util",
        "dojox/grid/EnhancedGrid",
        "qpid/common/grid/EnhancedFilter",
        "dojox/grid/enhanced/plugins/NestedSorting",
        "dojo/domReady!"],
       function (xhr, parser, array, lang, properties, updater, UpdatableStore, util, EnhancedGrid, EnhancedFilter, NestedSorting) {

          /*
           * Construct GridUpdater from the following arguments:
           * serviceUrl - service URL to fetch data for the grid. Optional, if data is specified
           * data - array containing data for the grid. Optional, if serviceUrl is specified
           * node - dom node or dom node id to
           * structure,
           * funct,
           * gridProperties,
           * gridConstructor
           */
           function GridUpdater(args) {

             var self = this;

             // GridUpdater fields
             this.updatable = args.hasOwnProperty("updatable") ? args.updatable : true ;
             this.serviceUrl = args.serviceUrl;
             this.updatableStore = null;
             this.grid = null;
             this.onUpdate = args.onUpdate;
             this._args = args;
             this.appendData = args.append;
             this.appendLimit = args.appendLimit;

             // default grid properties
             var gridProperties = {
                 autoHeight: true,
                 updateDelay: 0, // no delay updates when receiving notifications from a datastore
                 plugins: {
                   pagination: {
                     defaultPageSize: 25,
                     pageSizes: [10, 25, 50, 100],
                     description: true,
                     sizeSwitch: true,
                     pageStepper: true,
                     gotoButton: true,
                     maxPageStep: 4,
                     position: "bottom"
                 },
                 enhancedFilter: {}
                }
             };

             var filterPluginFound = false;

             // merge args grid properties with default grid properties
             if(args && args.gridProperties)
             {
               var argProperties = args.gridProperties;
               for(var argProperty in argProperties)
               {
                   if(argProperties.hasOwnProperty(argProperty))
                   {
                       if (argProperty == "plugins")
                       {
                         var argPlugins = argProperties[ argProperty ];
                         for(var argPlugin in argPlugins)
                         {
                           if (argPlugin == "filter")
                           {
                               // we need to switch off filtering in EnhancedFilter
                               filterPluginFound = true;
                           }
                           if(argPlugins.hasOwnProperty(argPlugin))
                           {
                             var argPluginProperties = argPlugins[ argPlugin ];
                             if (argPluginProperties && gridProperties.plugins.hasOwnProperty(argPlugin))
                             {
                               var gridPlugin = gridProperties.plugins[ argPlugin ];
                               for(var pluginProperty in argPluginProperties)
                               {
                                 if(argPluginProperties.hasOwnProperty(pluginProperty))
                                 {
                                   gridPlugin[pluginProperty] = argPluginProperties[pluginProperty];
                                 }
                               }
                             }
                             else
                             {
                               gridProperties.plugins[ argPlugin ] = argPlugins[ argPlugin ];
                             }
                           }
                         }
                       }
                       else
                       {
                         gridProperties[ argProperty ] = argProperties[ argProperty ];
                       }
                   }
               }
             }

             if (filterPluginFound)
             {
                 gridProperties.plugins.enhancedFilter.disableFiltering = true;
             }

             var updatableStoreFactory = function(data)
             {
               try
               {
                 self.updatableStore = new UpdatableStore(data, self._args.node, self._args.structure,
                     self._args.funct, gridProperties, self._args.GridConstructor || EnhancedGrid, self.appendData);
               }
               catch(e)
               {
                 console.error(e);
                 throw e;
               }
               self.grid = self.updatableStore.grid;
               self.grid.updater = self;
               if (self.onUpdate)
               {
                 self.onUpdate(data);
               }
               if (self.serviceUrl)
               {
                 updater.add(self);
               }
             };

             if (args && args.serviceUrl)
             {
               var requestUrl = lang.isFunction(this.serviceUrl) ? this.serviceUrl() : this.serviceUrl;
               xhr.get({url: requestUrl, sync: properties.useSyncGet, handleAs: "json"}).then(updatableStoreFactory, util.errorHandler);
             }
             else if (args && args.data)
             {
               updatableStoreFactory(args.data);
             }
           }

           GridUpdater.prototype.destroy = function()
           {
             updater.remove(this);
             if (this.updatableStore)
             {
                 this.updatableStore.close();
                 this.updatableStore = null;
             }
             if (this.grid)
             {
                 this.grid.destroy();
                 this.grid = null;
             }
           };

           GridUpdater.prototype.refresh = function(data)
           {
               this.updating = true;
               try
               {
                   if (this.appendData ? this.updatableStore.append(data, this.appendLimit): this.updatableStore.update(data))
                   {
                     // EnhancedGrid with Filter plugin has "filter" layer.
                     // The filter expression needs to be re-applied after the data update
                     var filterLayer = this.grid.layer("filter");
                     if ( filterLayer && filterLayer.filterDef)
                     {
                         var currentFilter = filterLayer.filterDef();

                         if (currentFilter)
                         {
                             // re-apply filter in the filter layer
                             filterLayer.filterDef(currentFilter);
                         }
                     }

                     // refresh grid to render updates
                     this.grid._refresh();
                   }
               }
               finally
               {
                   this.updating = false;
                   if (this.onUpdate)
                   {
                     this.onUpdate(data);
                   }
               }
           }

           GridUpdater.prototype.update = function()
           {
             if (this.updatable)
             {
                 this.performUpdate();
             }
           };

           GridUpdater.prototype.performUpdate = function()
           {
                 var self = this;
                 var requestUrl = lang.isFunction(this.serviceUrl) ? this.serviceUrl() : this.serviceUrl;
                 var requestArguments = {url: requestUrl, sync: properties.useSyncGet, handleAs: "json"};
                 xhr.get(requestArguments).then(function(data){self.refresh(data);});
           };

           GridUpdater.prototype.performRefresh = function(data)
           {
               if (!this.updating)
               {
                   this.refresh(data);
               }
           };

           return GridUpdater;
       });
