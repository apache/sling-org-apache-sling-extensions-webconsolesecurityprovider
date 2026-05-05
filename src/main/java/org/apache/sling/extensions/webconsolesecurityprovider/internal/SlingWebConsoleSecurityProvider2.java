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

import java.util.Dictionary;
import java.util.Iterator;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.felix.webconsole.spi.SecurityProvider;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.extensions.webconsolesecurityprovider.internal.ConfigConstants.PROP_DEFAULT_GROUPS;
import static org.apache.sling.extensions.webconsolesecurityprovider.internal.ConfigConstants.PROP_DEFAULT_USERS;
import static org.apache.sling.extensions.webconsolesecurityprovider.internal.ConfigConstants.PROP_GROUPS;
import static org.apache.sling.extensions.webconsolesecurityprovider.internal.ConfigConstants.PROP_USERS;

/**
 * The <code>SlingWebConsoleSecurityProvider</code> is security provider for the
 * Apache Felix Web Console which validates the user name and password by loging
 * into the repository and the checking whether the user is allowed access.
 * Access granted by the {@link #authenticate(String, String)} method applies to
 * all of the Web Console since the {@link #authorize(Object, String)} method
 * always returns <code>true</code>.
 * <p>
 * This security provider requires a JCR Repository to operate. Therefore it is
 * only registered as a security provider service once such a JCR Repository is
 * available.
 */
public class SlingWebConsoleSecurityProvider2 implements SecurityProvider, ManagedService {

    /** default logger */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected Set<String> users = PROP_DEFAULT_USERS;
    protected Set<String> groups = PROP_DEFAULT_GROUPS;

    private final AuthenticationSupport authentiationSupport;

    private final Authenticator authenticator;

    private final String slingContextPath;

    public SlingWebConsoleSecurityProvider2(
            @NotNull final Object support, @NotNull final Object authenticator, @NotNull String slingContextPath) {
        this.authentiationSupport = (AuthenticationSupport) support;
        this.authenticator = (Authenticator) authenticator;
        this.slingContextPath = slingContextPath;
    }

    /**
     * Handle configuration
     * @see org.osgi.service.cm.ManagedService#updated(java.util.Dictionary)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void updated(@Nullable Dictionary<String, ?> properties) throws ConfigurationException {
        final Converter converter = Converters.standardConverter();
        this.users = converter
                .convert(properties == null ? null : properties.get(PROP_USERS))
                .defaultValue(PROP_DEFAULT_USERS)
                .to(Set.class);
        this.groups = converter
                .convert(properties == null ? null : properties.get(PROP_GROUPS))
                .defaultValue(PROP_DEFAULT_GROUPS)
                .to(Set.class);
    }

    /**
     * All users authenticated with the repository and being a member of the
     * authorized groups are granted access for all roles in the Web Console.
     */
    @Override
    public boolean authorize(@NotNull Object user, @NotNull String role) {
        logger.debug("authorize: Grant user {} access for role {}", user, role);
        return true;
    }

    /**
     * @see org.apache.felix.webconsole.spi.SecurityProvider#authenticate(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse)
     */
    @Override
    public @Nullable Object authenticate(
            @NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
        try {
            if (this.authentiationSupport.handleSecurity(request, response)) {
                // get ResourceResolver (set by AuthenticationSupport)
                Object resolverObject = request.getAttribute(AuthenticationSupport.REQUEST_ATTRIBUTE_RESOLVER);
                if (resolverObject instanceof ResourceResolver resolver) {
                    final Session session = resolver.adaptTo(Session.class);
                    if (session != null) {
                        try {
                            final User u = this.authenticate(session);
                            if (u != null) {
                                return u.getID();
                            }
                        } catch (final Exception re) {
                            logger.info(
                                    "authenticate: Generic problem trying grant User access to the Web Console", re);
                        }
                    }
                }
                if (request.getAuthType() == null) {
                    this.authenticator.login(request, response);
                }
            }
        } finally {
            this.authentiationSupport.finishSecurity(request, response);
        }
        return null;
    }

    protected @Nullable User authenticate(@NotNull final Session session) throws RepositoryException {
        String userId = session.getUserID();
        if (session instanceof JackrabbitSession jrSession) {
            UserManager umgr = jrSession.getUserManager();
            Authorizable a = umgr.getAuthorizable(userId);
            if (a instanceof User u) {

                // check users
                if (users.contains(userId)) {
                    return u;
                }

                // check groups
                Iterator<Group> gi = a.memberOf();
                while (gi.hasNext()) {
                    if (groups.contains(gi.next().getID())) {
                        return u;
                    }
                }

                logger.info("authenticate: User {} is denied Web Console access", userId);
            } else {
                logger.error("authenticate: Expected user ID {} to refer to a user", userId);
            }
        } else {
            logger.info(
                    "authenticate: Jackrabbit Session required to grant access to the Web Console for {}; got {}",
                    userId,
                    session.getClass());
        }
        return null;
    }

    @Override
    public void logout(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        // SLING-13178 - we want the context path to be whatever was used to login
        //   to ensure the sling.formauth cookie using the same "path" value when
        //   clearing the cookie
        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getContextPath() {
                return slingContextPath;
            }
        };

        this.authenticator.logout(wrappedRequest, response);
    }
}
