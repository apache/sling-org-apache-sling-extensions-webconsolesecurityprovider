package org.apache.sling.extensions.webconsolesecurityprovider.internal;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.jcr.Repository;

import org.apache.felix.webconsole.WebConsoleSecurityProvider;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;

public class ServiceListenerTest {

    @Rule
    public OsgiContext context = new OsgiContext();
    
    @Mock
    Repository repository;
    
    @Mock
    AuthenticationSupport authenticationSupport;
    
    @Mock
    Authenticator authenticator;
    
    
    ServicesListener listener;
    
    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

    }
    
    @After
    public void shutdown() {
        listener.deactivate();
    }
    
    @Test
    public void testDefaultAuth() {
        listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
        assertNoSecurityProviderRegistered();
        
        context.registerService(Repository.class,repository);
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
    public void testWithSlingAuth() {
        try {
            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.SLING_AUTH);
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertNoSecurityProviderRegistered();
            
            context.registerService(Repository.class,repository);
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
    public void testWithForcedJcrAuth() {
        try {
            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.JCR_AUTH);
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertNoSecurityProviderRegistered();
            
            // no matter what is registered, always the auth against the repo needs to be there
            
            context.registerService(Repository.class,repository);
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
    public void testGetAuthType() {
        try {
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertEquals(ServicesListener.AuthType.DEFAULT, listener.getAuthType());

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.JCR_AUTH);
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertEquals(ServicesListener.AuthType.JCR, listener.getAuthType());

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.SLING_AUTH);
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertEquals(ServicesListener.AuthType.SLING, listener.getAuthType());

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, "invalid");
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertEquals(ServicesListener.AuthType.DEFAULT, listener.getAuthType());
        } finally {
            System.getProperties().remove(ServicesListener.WEBCONSOLE_AUTH_TYPE);
        }
    }

    @Test
    public void testGetTargetState() {
        try {
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, false));
            assertEquals(ServicesListener.State.PROVIDER_JCR, listener.getTargetState(false, true));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, false));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, true));

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.JCR_AUTH);
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, false));
            assertEquals(ServicesListener.State.PROVIDER_JCR, listener.getTargetState(false, true));
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(true, false));
            assertEquals(ServicesListener.State.PROVIDER_JCR, listener.getTargetState(true, true));

            System.setProperty(ServicesListener.WEBCONSOLE_AUTH_TYPE, ServicesListener.SLING_AUTH);
            listener = new ServicesListener(wrapForValidProperties(context.bundleContext()));
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, false));
            assertEquals(ServicesListener.State.NONE, listener.getTargetState(false, true));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, false));
            assertEquals(ServicesListener.State.PROVIDER_SLING, listener.getTargetState(true, true));
        } finally {
            System.getProperties().remove(ServicesListener.WEBCONSOLE_AUTH_TYPE);
        }
    }

    // until https://issues.apache.org/jira/browse/SLING-11505 is implemented
    private BundleContext wrapForValidProperties(BundleContext bc) {
        BundleContext spy = Mockito.spy(bc);
        Mockito.when(spy.getProperty(Mockito.anyString())).thenAnswer(invocation -> {
            String key = (String) invocation.getArguments()[0];
            return System.getProperty(key);
        });
        return spy;
    }

    // Helpers
    
    private void assertRepositoryRegistered() { 
        assertTrue("Expected to have the repository registered",getSecurityProvider() instanceof SlingWebConsoleSecurityProvider);
    }
    
    private void assertSlingAuthRegistered() {
        assertTrue("Expected to have SlingAuth registered",getSecurityProvider() instanceof SlingWebConsoleSecurityProvider2); 
    }
    
    private void assertNoSecurityProviderRegistered () {
        assertNull(getSecurityProvider());
    }
    
    private WebConsoleSecurityProvider getSecurityProvider() {
        return context.getService(WebConsoleSecurityProvider.class);
    }
    
  

    
    
    
}
