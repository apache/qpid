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

/**
 * 
 * @author sorin
 * 
 * Activator class for the tracking services
 */

package org.apache.qpid.info;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.qpid.info.util.HttpPoster;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator
{

    BundleContext _ctx = null;

    public void start(BundleContext ctx) throws Exception
    {
        if (null != ctx)
        {
            BrokerInfoServiceImpl service = new BrokerInfoServiceImpl(ctx);
            ctx.registerService(BrokerInfoService.class.getName(), service,
                    null);
            _ctx = ctx;
            HttpPoster hp;
            try
            {
                Properties props = new Properties();
                String QPID_WORK = System.getenv("QPID_WORK");
                props.load(new FileInputStream(QPID_WORK + File.separator
                        + "etc" + File.separator + "qpidinfo.properties"));
                hp = new HttpPoster(props, service.invoke().toXML());
                hp.run();
            } catch (Exception ex)
            {
                // Silently drop any exception
            }
        }
    }

    public BundleContext getBundleContext()
    {
        return _ctx;
    }

    public void stop(BundleContext ctx) throws Exception
    {
        // no need to do anything here, osgi will unregister the service for us
    }

}
