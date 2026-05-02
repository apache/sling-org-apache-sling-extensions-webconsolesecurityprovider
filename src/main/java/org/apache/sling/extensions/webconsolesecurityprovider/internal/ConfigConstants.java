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

import java.util.Collections;
import java.util.Set;

/**
 * This is the common constants for the two provider implementations.
 */
class ConfigConstants {

    private ConfigConstants() {
        /* This utility class should not be instantiated */
    }

    // name of the property providing list of authorized users
    static final String PROP_USERS = "users";

    // default user being authorized
    static final Set<String> PROP_DEFAULT_USERS = Set.of("admin");

    // name of the property providing list of groups whose members are
    // authorized
    static final String PROP_GROUPS = "groups";

    // default user being authorized
    static final Set<String> PROP_DEFAULT_GROUPS = Collections.emptySet();
}
