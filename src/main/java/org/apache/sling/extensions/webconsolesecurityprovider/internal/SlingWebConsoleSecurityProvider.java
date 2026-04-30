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
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import java.util.Dictionary;
import java.util.Iterator;
import java.util.Set;

import org.apache.felix.webconsole.internal.servlet.BasicWebConsoleSecurityProvider;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
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
public class SlingWebConsoleSecurityProvider extends BasicWebConsoleSecurityProvider implements ManagedService {

    /** default logger */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected Set<String> users = PROP_DEFAULT_USERS;
    protected Set<String> groups = PROP_DEFAULT_GROUPS;

    private Repository repository;

    public SlingWebConsoleSecurityProvider(
            @NotNull final BundleContext bundleContext, @NotNull final Object repository) {
        super(bundleContext);
        this.repository = (Repository) repository;
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
     * Authenticates and authorizes the user identified by the user name and
     * password. The check applied to authorize access consists of the following
     * steps:
     * <ol>
     * <li>User name and password are able to create a JCR session with the
     * default repository workspace. If such a session cannot be created, the
     * user is denied access.</li>
     * <li>If the user is listed in the configured set of granted users, access
     * is granted to all of the Web Console.</li>
     * <li>If the user is a member of one of the groups configured to grant
     * access to their members, access is granted to all of the Web Console.</li>
     * </ol>
     * <p>
     * If the user name and password cannot be used to login to the default
     * workspace of the repository or if the user neither one of the configured
     * set of granted users or is not a member of the configured set of groups
     * access is denied to the Web Console.
     *
     * @param userName The name of the user to grant access for
     * @param password The password to authenticate the user. This may be
     *            <code>null</code> to assume an empty password.
     * @return The <code>userName</code> is currently returned to indicate
     *         successful authentication.
     * @throws NullPointerException if <code>userName</code> is
     *             <code>null</code>.
     */
    @Override
    public @Nullable Object authenticate(@NotNull String userName, @Nullable String password) {
        final Credentials creds =
                new SimpleCredentials(userName, (password == null) ? new char[0] : password.toCharArray());
        Session session = null;
        try {
            session = repository.login(creds);
            if (session instanceof JackrabbitSession jrSession) {
                UserManager umgr = jrSession.getUserManager();
                String userId = session.getUserID();
                Authorizable a = umgr.getAuthorizable(userId);
                if (a instanceof User) {
                    // check users
                    if (users.contains(userId)) {
                        return userName;
                    }

                    // check groups
                    Iterator<Group> gi = a.memberOf();
                    while (gi.hasNext()) {
                        if (groups.contains(gi.next().getID())) {
                            return userName;
                        }
                    }

                    logger.debug("authenticate: User {} is denied Web Console access", userName);
                } else {
                    logger.error("authenticate: Expected user ID {} to refer to a user", userId);
                }
            } else {
                logger.info(
                        "authenticate: Jackrabbit Session required to grant access to the Web Console for {}; got {}",
                        userName,
                        session.getClass());
            }
        } catch (final LoginException re) {
            logger.info(
                    "authenticate: User "
                            + userName
                            + " failed to authenticate with the repository for Web Console access",
                    re);
        } catch (final Exception re) {
            logger.info(
                    "authenticate: Generic problem trying grant User " + userName + " access to the Web Console", re);
        } finally {
            if (session != null) {
                session.logout();
            }
        }

        // no success (see log)
        return null;
    }
}
