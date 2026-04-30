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

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.service.cm.ConfigurationException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.withSettings;

/**
 *
 */
public class SlingWebConsoleSecurityProvider2Test {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

    private SlingWebConsoleSecurityProvider2 provider;

    private AuthenticationSupport support;
    private Authenticator authenticator;

    @Before
    public void before() {
        support = Mockito.mock(AuthenticationSupport.class);
        authenticator = Mockito.mock(Authenticator.class);
        provider = new SlingWebConsoleSecurityProvider2(support, authenticator);
    }

    /**
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.SlingWebConsoleSecurityProvider2#updated(java.util.Dictionary)}.
     */
    @Test
    public void testUpdated() throws ConfigurationException {
        provider.updated(null);
        assertEquals(ConfigConstants.PROP_DEFAULT_USERS, provider.users);
        assertEquals(ConfigConstants.PROP_DEFAULT_GROUPS, provider.groups);

        provider.updated(new Hashtable<>(Map.of()));
        assertEquals(ConfigConstants.PROP_DEFAULT_USERS, provider.users);
        assertEquals(ConfigConstants.PROP_DEFAULT_GROUPS, provider.groups);

        provider.updated(new Hashtable<>(Map.of("users", "user1", "groups", "group1")));
        assertEquals(Set.of("user1"), provider.users);
        assertEquals(Set.of("group1"), provider.groups);

        provider.updated(new Hashtable<>(
                Map.of("users", new String[] {"user1", "user2"}, "groups", new String[] {"group1", "group2"})));
        assertEquals(Set.of("user1", "user2"), provider.users);
        assertEquals(Set.of("group1", "group2"), provider.groups);
    }

    /**
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.SlingWebConsoleSecurityProvider2#authenticate(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse)}.
     */
    @Test
    public void testAuthenticate() throws RepositoryException, ConfigurationException {
        final @NotNull MockSlingJakartaHttpServletRequest request = context.jakartaRequest();
        final @NotNull MockSlingJakartaHttpServletResponse response = context.jakartaResponse();

        // security not handled
        Mockito.doReturn(false)
                .when(support)
                .handleSecurity(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertNull(provider.authenticate(request, response));

        // security handled without ResourceResolver (set by AuthenticationSupport)
        Mockito.doReturn(true)
                .when(support)
                .handleSecurity(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertNull(provider.authenticate(request, response));

        // security handled with ResourceResolver (set by AuthenticationSupport)
        final @NotNull ResourceResolver rr = context.resourceResolver();
        request.setAttribute(AuthenticationSupport.REQUEST_ATTRIBUTE_RESOLVER, rr);
        // for code coverage simulate no auth challenge path
        request.setAuthType("FORMS");
        assertNull(provider.authenticate(request, response));

        // for code coverage simulate auth challenge path
        request.setAuthType(null);
        assertNull(provider.authenticate(request, response));

        // create a user
        UserManager userManager = ((JackrabbitSession) rr.adaptTo(Session.class)).getUserManager();
        final @NotNull User user1 = userManager.createUser("admin", "test1");

        // user does exist, and in the configuration
        assertEquals("admin", provider.authenticate(request, response));

        // change the users in the configuration
        provider.updated(new Hashtable<>(Map.of("users", "test1")));

        // user does exist, but not in the configuration
        assertNull(provider.authenticate(request, response));

        // add user to a group
        final @NotNull Group group1 = userManager.createGroup("group1");
        group1.addMember(user1);
        final @NotNull Group group2 = userManager.createGroup("group2");

        // change the users in the configuration
        provider.updated(new Hashtable<>(Map.of("users", "test1", "groups", "group2")));

        // user does exist, but not in the groups configuration
        assertNull(provider.authenticate(request, response));

        // add to the configured group
        group2.addMember(user1);
        // user does exist, and in the groups configuration
        assertEquals("admin", provider.authenticate(request, response));
    }

    @Test
    public void testAuthenticateWithNoSession() {
        final @NotNull MockSlingJakartaHttpServletRequest request = context.jakartaRequest();
        final @NotNull MockSlingJakartaHttpServletResponse response = context.jakartaResponse();

        // security handled
        Mockito.doReturn(true)
                .when(support)
                .handleSecurity(any(HttpServletRequest.class), any(HttpServletResponse.class));

        // security handled with ResourceResolver (set by AuthenticationSupport)
        final @NotNull ResourceResolver rr = Mockito.spy(context.resourceResolver());
        request.setAttribute(AuthenticationSupport.REQUEST_ATTRIBUTE_RESOLVER, rr);

        // simulate an null session
        Mockito.doReturn(null).when(rr).adaptTo(Session.class);

        // expected no user resolved and null returned
        assertNull(provider.authenticate(request, response));
    }

    @Test
    public void testAuthenticateWithCaughtException() throws RepositoryException {
        final @NotNull MockSlingJakartaHttpServletRequest request = context.jakartaRequest();
        final @NotNull MockSlingJakartaHttpServletResponse response = context.jakartaResponse();

        // security handled
        Mockito.doReturn(true)
                .when(support)
                .handleSecurity(any(HttpServletRequest.class), any(HttpServletResponse.class));

        // security handled with ResourceResolver (set by AuthenticationSupport)
        final @NotNull ResourceResolver rr = Mockito.spy(context.resourceResolver());
        request.setAttribute(AuthenticationSupport.REQUEST_ATTRIBUTE_RESOLVER, rr);

        // simulate an exception thrown
        final Session mockSession =
                Mockito.mock(Session.class, withSettings().extraInterfaces(JackrabbitSession.class));
        Mockito.doThrow(UnsupportedRepositoryOperationException.class)
                .when((JackrabbitSession) mockSession)
                .getUserManager();
        Mockito.doReturn(mockSession).when(rr).adaptTo(Session.class);

        // expected exception caught, message logged and null returned
        assertNull(provider.authenticate(request, response));
    }

    @Test
    public void testAuthenticateWithoutJackrabbitSession() throws RepositoryException {
        final Session mockSession = Mockito.mock(Session.class);
        Mockito.doReturn("test1").when(mockSession).getUserID();
        assertNull(provider.authenticate(mockSession));
    }

    /**
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.SlingWebConsoleSecurityProvider2#logout(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse)}.
     */
    @Test(expected = org.junit.Test.None.class)
    public void testLogout() {
        final @NotNull MockSlingJakartaHttpServletRequest request = context.jakartaRequest();
        final @NotNull MockSlingJakartaHttpServletResponse response = context.jakartaResponse();
        provider.logout(request, response);
    }

    /**
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.SlingWebConsoleSecurityProvider2#authorize(java.lang.Object, java.lang.String)}.
     */
    @Test
    public void testAuthorize() {
        assertTrue(provider.authorize("testuser1", "role1"));
    }
}
