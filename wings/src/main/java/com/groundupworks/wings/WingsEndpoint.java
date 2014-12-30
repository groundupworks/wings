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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.app.Fragment;

import com.groundupworks.wings.core.ShareRequest;
import com.groundupworks.wings.core.WingsDbHelper;
import com.groundupworks.wings.core.WingsInjector;
import com.squareup.otto.Bus;

import java.util.Set;

/**
 * An interface to define an endpoint that Wings can share to.
 *
 * @author Benedict Lau
 */
public abstract class WingsEndpoint {

    /**
     * The logger for debug messages.
     */
    protected static final IWingsLogger sLogger = WingsInjector.getLogger();

    /**
     * The {@link android.content.Context} that Wings is running on.
     */
    protected final Context mContext = WingsInjector.getApplicationContext();

    /**
     * The {@link android.os.Handler} to post background tasks.
     */
    protected final Handler mHandler = new Handler(WingsInjector.getWorkerLooper());

    /**
     * The Wings database.
     */
    protected final WingsDbHelper mDatabase = WingsInjector.getDatabase();

    /**
     * The event bus to communicate link events.
     */
    private final Bus mBus = WingsInjector.getBus();

    /**
     * Protected constructor.
     */
    protected WingsEndpoint() {
        mBus.register(this);
    }

    /**
     * Notifies subscribers that the link state has changed. Each endpoint implementation must call
     * this method with its own {@link com.groundupworks.wings.WingsEndpoint.LinkEvent} subclass when the
     * link state has changed.
     *
     * @param event the {@link com.groundupworks.wings.WingsEndpoint.LinkEvent} implementation associated with
     *              the specific endpoint.
     * @param <T>   the type for the {@link com.groundupworks.wings.WingsEndpoint.LinkEvent} subclass.
     */
    protected <T extends WingsEndpoint.LinkEvent> void notifyLinkStateChanged(T event) {
        mBus.post(event);
    }

    /**
     * The id that is unique to each endpoint.
     *
     * @return the endpoint id.
     */
    public abstract int getEndpointId();

    /**
     * Starts a link request.
     *
     * @param activity the {@link Activity}.
     * @param fragment the {@link Fragment}. May be null.
     */
    public abstract void startLinkRequest(Activity activity, Fragment fragment);

    /**
     * Unlinks an account.
     */
    public abstract void unlink();

    /**
     * Checks if the user is linked.
     *
     * @return true if an account is linked; false otherwise.
     */
    public abstract boolean isLinked();

    /**
     * A convenience method that must be called in the onResume() of any {@link android.app.Activity}
     * or {@link android.support.v4.app.Fragment} that uses
     * {@link #startLinkRequest(android.app.Activity, android.support.v4.app.Fragment)}.
     */
    public abstract void onResumeImpl();

    /**
     * A convenience method that must be called in the onActivityResult() of any {@link android.app.Activity}
     * or {@link android.support.v4.app.Fragment} that uses
     * {@link #startLinkRequest(android.app.Activity, android.support.v4.app.Fragment)}.
     *
     * @param activity    the {@link android.app.Activity}.
     * @param fragment    the {@link android.support.v4.app.Fragment}. May be null.
     * @param requestCode the integer request code originally supplied to startActivityForResult(), allowing you to identify who
     *                    this result came from.
     * @param resultCode  the integer result code returned by the child activity through its setResult().
     * @param data        an Intent, which can return result data to the caller (various data can be attached to Intent
     *                    "extras").
     */
    public abstract void onActivityResultImpl(Activity activity, Fragment fragment, int requestCode, int resultCode, Intent data);

    /**
     * Gets the information associated with the link.
     *
     * @return the {@link com.groundupworks.wings.WingsEndpoint.LinkInfo}; or null if unlinked.
     */
    public abstract LinkInfo getLinkInfo();

    /**
     * Process share requests by sharing to the linked account. This should be called in a background
     * thread.
     *
     * @return a set of {@link com.groundupworks.wings.WingsEndpoint.ShareNotification}s representing the results of the processed {@link ShareRequest}.
     * May be null or an empty set.
     */
    public abstract Set<ShareNotification> processShareRequests();

    /**
     * Produces a {@link com.groundupworks.wings.WingsEndpoint.LinkEvent} subclass reflecting the current link
     * state of the endpoint. This event is emitted to a subscriber immediately after subscription.
     * <p/>
     * The implementation of this method must be annotated with {@link com.squareup.otto.Produce}.
     *
     * @param <T> the type for the {@link com.groundupworks.wings.WingsEndpoint.LinkEvent} subclass.
     * @return the {@link com.groundupworks.wings.WingsEndpoint.LinkEvent} implementation associated with
     * the specific endpoint.
     */
    public abstract <T extends WingsEndpoint.LinkEvent> T produceLinkEvent();

    /**
     * The base interface for destination id.
     */
    public interface DestinationId {

        /**
         * The destination id when unlinked.
         */
        public static final int UNLINKED = Integer.MIN_VALUE;
    }

    /**
     * An interface for a {@link android.app.Notification} published by Wings.
     */
    public static interface ShareNotification {

        /**
         * @return a unique identifier for the notification within the {@link android.app.Application}.
         */
        int getId();

        /**
         * @return the title for the notification. Must not be null.
         */
        String getTitle();

        /**
         * @return the message for the notification. Must not be null.
         */
        String getMessage();

        /**
         * @return the ticker text for the notification. Must not be null.
         */
        String getTicker();

        /**
         * @return the {@link Intent} to launch an {@link Activity} when the notification is clicked. Must not be null.
         */
        Intent getIntent();
    }

    /**
     * The base class of an event emitted when the link state of an endpoint changes. The event is also
     * emitted to a subscriber immediately after subscription.
     */
    public static abstract class LinkEvent {

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
        protected LinkEvent(Class<? extends WingsEndpoint> endpointClazz, boolean isLinked) {
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

    /**
     * Information associated with the link.
     */
    public static class LinkInfo {

        /**
         * The user name.
         */
        public final String mUserName;

        /**
         * The destination id.
         */
        public final int mDestinationId;

        /**
         * The destination description.
         */
        public final String mDestinationDescription;

        /**
         * Constructor.
         *
         * @param userName               the user name.
         * @param destinationId          the destination id.
         * @param destinationDescription the destination description.
         */
        public LinkInfo(String userName, int destinationId, String destinationDescription) {
            mUserName = userName;
            mDestinationId = destinationId;
            mDestinationDescription = destinationDescription;
        }
    }
}
