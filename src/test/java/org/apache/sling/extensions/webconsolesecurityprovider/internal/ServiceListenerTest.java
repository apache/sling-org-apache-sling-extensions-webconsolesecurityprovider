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

import javax.jcr.Repository;

import org.apache.felix.webconsole.spi.SecurityProvider;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OsgiContextExtension.class)
class ServiceListenerTest {

    public OsgiContext context = new OsgiContext();

    @Mock
    Repository repository;

    @Mock
    AuthenticationSupport authenticationSupport;

    @Mock
    Authenticator authenticator;

    ServicesListener listener;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void shutdown() {
        if (listener != null) {
            listener.deactivate();
        }
    }

    @Test
    void testDefaultAuth() {
        listener = new ServicesListener(context.bundleContext());
        assertNoSecurityProviderRegistered();

        context.registerService(Repository.class, repository);
        listener.notifyChange();
        assertRepositoryRegistered();

        context.registerService(AuthenticationSupport.class, authenticationSupport);
        listener.notifyChange();
        assertRepositoryRegistered();

        context.registerService(Authenticator.class, authenticator);
        listener.notifyChange();
        assertSlingAuthRegistered();
    }

    @Test
    void testWithSlingAuth() {
        try {
            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.SLING_AUTH);
            listener = new ServicesListener(context.bundleContext());
            assertNoSecurityProviderRegistered();

            context.registerService(Repository.class, repository);
            listener.notifyChange();
            assertNoSecurityProviderRegistered();

            context.registerService(AuthenticationSupport.class, authenticationSupport);
            listener.notifyChange();
            assertNoSecurityProviderRegistered();

            context.registerService(Authenticator.class, authenticator);
            listener.notifyChange();
            assertSlingAuthRegistered();
        } finally {
            System.getProperties().remove(ServicesListener.WEBCONSOLE_AUTH_TYPE);
        }
    }

    @Test
    void testWithForcedJcrAuth() {
        try {
            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.JCR_AUTH);
            listener = new ServicesListener(context.bundleContext());
            assertNoSecurityProviderRegistered();

            // no matter what is registered, always the auth against the repo needs to be there

            context.registerService(Repository.class, repository);
            listener.notifyChange();
            assertRepositoryRegistered();

            context.registerService(AuthenticationSupport.class, authenticationSupport);
            listener.notifyChange();
            assertRepositoryRegistered();

            context.registerService(Authenticator.class, authenticator);
            listener.notifyChange();
            assertRepositoryRegistered();
        } finally {
            System.getProperties().remove(ServicesListener.WEBCONSOLE_AUTH_TYPE);
        }
    }

    @Test
    void testGetAuthType() {
        try {
            listener = new ServicesListener(context.bundleContext());
            assertEquals(ServicesListener.AuthType.DEFAULT, listener.getAuthType());

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.JCR_AUTH);
            listener = new ServicesListener(context.bundleContext());
            assertEquals(ServicesListener.AuthType.JCR, listener.getAuthType());

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.SLING_AUTH);
            listener = new ServicesListener(context.bundleContext());
            assertEquals(ServicesListener.AuthType.SLING, listener.getAuthType());

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, "invalid");
            listener = new ServicesListener(context.bundleContext());
            assertEquals(ServicesListener.AuthType.DEFAULT, listener.getAuthType());
        } finally {
            System.getProperties().remove(ServicesListener.WEBCONSOLE_AUTH_TYPE);
        }
    }

    @Test
    void testGetTargetState() {
        try {
            listener = new ServicesListener(context.bundleContext());
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, false));
            assertEquals(ServicesListener.State.PROVIDER_JCR, listener.getTargetState(false, true));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, false));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, true));

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.JCR_AUTH);
            listener = new ServicesListener(context.bundleContext());
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, false));
            assertEquals(ServicesListener.State.PROVIDER_JCR, listener.getTargetState(false, true));
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(true, false));
            assertEquals(ServicesListener.State.PROVIDER_JCR, listener.getTargetState(true, true));

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.SLING_AUTH);
            listener = new ServicesListener(context.bundleContext());
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, false));
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, true));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, false));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, true));
        } finally {
            System.getProperties().remove(ServicesListener.WEBCONSOLE_AUTH_TYPE);
        }
    }

    // Helpers

    private void assertRepositoryRegistered() {
        assertTrue(
                getSecurityProvider() instanceof SlingWebConsoleSecurityProvider,
                "Expected to have the repository registered");
    }

    private void assertSlingAuthRegistered() {
        assertTrue(
                getSecurityProvider() instanceof SlingWebConsoleSecurityProvider2,
                "Expected to have SlingAuth registered");
    }

    private void assertNoSecurityProviderRegistered() {
        assertNull(getSecurityProvider());
    }

    private SecurityProvider getSecurityProvider() {
        return context.getService(SecurityProvider.class);
    }
}
