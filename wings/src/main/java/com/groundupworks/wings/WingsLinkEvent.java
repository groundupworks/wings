/*
 * Copyright (C) 2014 Benedict Lau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.groundupworks.wings;

/**
 * The base class of an event emitted when the link state of an endpoint changes. The event is also
 * emitted to a subscriber immediately after subscription.
 *
 * @author Benedict Lau
 */
public abstract class WingsLinkEvent {

    /**
     * The endpoint associated with the link state change.
     */
    private Class mEndpointClazz;

    /**
     * The current link state.
     */
    private boolean mIsLinked;

    /**
     * Protected constructor that subclasses must call.
     *
     * @param endpointClazz the endpoint {@link java.lang.Class} associated with the link state change.
     * @param isLinked      the current link state.
     */
    protected WingsLinkEvent(Class<? extends WingsEndpoint> endpointClazz, boolean isLinked) {
        mEndpointClazz = endpointClazz;
        mIsLinked = isLinked;
    }

    /**
     * Gets the endpoint associated with the link state change.
     *
     * @return the endpoint {@link java.lang.Class}.
     */
    public Class<? extends WingsEndpoint> getEndpoint() {
        return mEndpointClazz;
    }

    /**
     * Gets the current link state.
     *
     * @return true if current link state for this endpoint is linked; false otherwise.
     */
    public final boolean isLinked() {
        return mIsLinked;
    }
}
