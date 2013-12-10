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
package org.apache.qpid.ra.admin;

import java.net.URISyntaxException;

import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;

public class QpidBindingURL  extends AMQBindingURL {

	private String _url;

	public QpidBindingURL(String url) throws URISyntaxException {
		super(url);

		if (!url.contains(BindingURL.OPTION_ROUTING_KEY) || getRoutingKey() == null) {
			setOption(BindingURL.OPTION_ROUTING_KEY, null);
		}

		this._url = url;
	}

	@Override
	public String getURL() {
		return _url;
	}

	@Override
	public String toString() {
		return _url;
	}

}
