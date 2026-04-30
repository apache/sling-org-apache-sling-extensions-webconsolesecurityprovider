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

import javax.jcr.Credentials;
import javax.jcr.LoginException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.service.cm.ConfigurationException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;

/**
 *
 */
public class SlingWebConsoleSecurityProviderTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

    private Repository mockRepo;
    private SlingWebConsoleSecurityProvider provider;

    @Before
    public void before() {
        mockRepo = Mockito.spy(context.resourceResolver().adaptTo(Session.class).getRepository());
        provider = new SlingWebConsoleSecurityProvider(context.bundleContext(), mockRepo);
    }

    /**
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.SlingWebConsoleSecurityProvider#updated(java.util.Dictionary)}.
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
     * Test method for {@link org.apache.sling.extensions.webconsolesecurityprovider.internal.SlingWebConsoleSecurityProvider#authenticate(java.lang.String, java.lang.String)}.
     */
    @Test
    public void testAuthenticate() throws RepositoryException, ConfigurationException {
        // user does not exist
        assertNull(provider.authenticate("test1", "test1"));

        // null password for code coverage
        assertNull(provider.authenticate("test1", null));

        // create a user
        UserManager userManager = ((JackrabbitSession) mockRepo.login()).getUserManager();
        userManager.createUser("test1", "test1");

        // user does exist, but not in the configuration
        assertNull(provider.authenticate("test1", "test1"));

        // add the user to the configuration
        provider.updated(new Hashtable<>(Map.of("users", "test1")));

        // user does exist, and also in the configuration
        assertEquals("test1", provider.authenticate("test1", "test1"));

        // a second user who is in a group
        final @NotNull User user2 = userManager.createUser("test2", "test2");
        final @NotNull Group group1 = userManager.createGroup("group1");
        group1.addMember(user2);

        // user does exist and a not a member of a configured group
        assertNull(provider.authenticate("test2", "test2"));

        // add the group to the configuration
        provider.updated(new Hashtable<>(Map.of("groups", "group1")));

        // user does exist and a member of a configured group
        assertEquals("test2", provider.authenticate("test2", "test2"));
    }

    @Test
    public void testAuthenticateWithoutJackrabbitSession() throws RepositoryException {
        // simulate the login return some non-jackrabbit session impl
        final Session mockSession = Mockito.mock(Session.class);
        Mockito.doReturn(mockSession).when(mockRepo).login(any(Credentials.class));

        // user does not exist
        assertNull(provider.authenticate("test1", "test1"));
    }

    @Test
    public void testAuthenticateWithLoginException() throws RepositoryException {
        // simulate the login throwing an exception
        Mockito.doThrow(LoginException.class).when(mockRepo).login(any(Credentials.class));

        // user does not exist
        assertNull(provider.authenticate("test1", "test1"));
    }

    @Test
    public void testAuthenticateWithOtherException() throws RepositoryException {
        // simulate the login throwing an exception
        Mockito.doThrow(RuntimeException.class).when(mockRepo).login(any(Credentials.class));

        // user does not exist
        assertNull(provider.authenticate("test1", "test1"));
    }
}
