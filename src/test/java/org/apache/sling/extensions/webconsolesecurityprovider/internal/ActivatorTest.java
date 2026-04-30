/*
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
 */
package org.apache.sling.extensions.webconsolesecurityprovider.internal;

import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Test.None;
import org.osgi.framework.BundleContext;

/**
 *
 */
public class ActivatorTest {

    @Rule
    public OsgiContext context = new OsgiContext();

    private Activator activator = new Activator();

    /**
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.Activator#start(org.osgi.framework.BundleContext)}.
     */
    @Test(expected = None.class)
    public void testStart() throws Exception {
        final @NotNull BundleContext bundleContext = context.bundleContext();
        activator.start(bundleContext);
    }

    /**
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.Activator#stop(org.osgi.framework.BundleContext)}.
     */
    @Test(expected = None.class)
    public void testStop() throws Exception {
        final @NotNull BundleContext bundleContext = context.bundleContext();

        // start
        activator.start(bundleContext);

        // stop
        activator.stop(bundleContext);

        // stop one more time for code coverage
        activator.stop(bundleContext);
    }
}
