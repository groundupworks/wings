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

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import com.groundupworks.wings.core.Destination;
import com.groundupworks.wings.core.WingsDbHelper;
import com.groundupworks.wings.core.WingsInjector;
import com.groundupworks.wings.core.WingsService;
import com.squareup.otto.Bus;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * The public APIs of the Wings library. The client application must provide the dependencies via
 * {@link Wings#init(IWingsModule, Class[])} in its {@link android.app.Application#onCreate()}.
 *
 * @author Benedict Lau
 */
public final class Wings {

    /**
     * Flag to track whether Wings is initialized.
     */
    private static volatile boolean sIsInitialized = false;

    /**
     * The protected set of endpoint instances that Wings can share to. Though the instances are
     * returned through the APIs, the set itself is never exposed, only copies of it, so the set is
     * essentially immutable after a successful initialization.
     */
    private static volatile Set<WingsEndpoint> sEndpoints = new HashSet<WingsEndpoint>();


    /**
     * Private constructor to ensure this class cannot be instantiated.
     */
    private Wings() {
    }

    /**
     * Initializer used to pass dependencies for Wings. This method must be called in the
     * {@link android.app.Application#onCreate()}.
     *
     * @param module          the Dagger module implementing {@link com.groundupworks.wings.IWingsModule}.
     *                        Pass {@link com.groundupworks.wings.Wings.DefaultModule} to use the default
     *                        components.
     * @param endpointClazzes the endpoints that Wings can share to, passed as {@link java.lang.Class} types.
     * @return {@code true} if Wings is successfully initialized; {@code false} otherwise. Once {@link true}
     * has been returned, calling this method will have no effect, and the return value will always be
     * {@link true}.
     */
    public static synchronized final boolean init(IWingsModule module, Class<? extends WingsEndpoint>... endpointClazzes) {
        // No-op if already initialized.
        if (!sIsInitialized) {
            WingsInjector.init(module);
            try {
                final Set<WingsEndpoint> endpoints = new HashSet<WingsEndpoint>();
                final Set<Integer> endpointIds = new HashSet<Integer>();
                for (Class clazz : endpointClazzes) {
                    WingsEndpoint endpoint = (WingsEndpoint) clazz.newInstance();
                    endpoints.add(endpoint);

                    // Ensure that endpoint ids are unique.
                    if (!endpointIds.add(endpoint.getEndpointId())) {
                        return false;
                    }
                }
                sEndpoints = endpoints;
                sIsInitialized = true;
            } catch (InstantiationException e) {
                WingsInjector.getLogger().log(Wings.class, "init", e.toString());
            } catch (IllegalAccessException e) {
                WingsInjector.getLogger().log(Wings.class, "init", e.toString());
            }
        }

        return sIsInitialized;
    }

    /**
     * Gets the set of endpoint instances that Wings can share to.
     *
     * @return the set of endpoint instances.
     * @throws IllegalStateException Wings must be initialized. See {@link Wings#init(IWingsModule, Class[])}.
     */
    public static final Set<WingsEndpoint> getEndpoints() throws IllegalStateException {
        if (!sIsInitialized) {
            throw new IllegalStateException("Wings must be initialized. See Wings#init().");
        }
        return new HashSet<WingsEndpoint>(sEndpoints);
    }

    /**
     * Gets the instance of a specific endpoint that Wings can share to.
     *
     * @param endpointClazz the endpoint {@link java.lang.Class}.
     * @return the endpoint instance; or {@code null} if unavailable.
     * @throws IllegalStateException Wings must be initialized. See {@link Wings#init(IWingsModule, Class[])}.
     */
    public static final WingsEndpoint getEndpoint(Class<? extends WingsEndpoint> endpointClazz) throws IllegalStateException {
        if (!sIsInitialized) {
            throw new IllegalStateException("Wings must be initialized. See Wings#init().");
        }
        WingsEndpoint selectedEndpoint = null;
        for (WingsEndpoint endpoint : sEndpoints) {
            if (endpointClazz.isInstance(endpoint)) {
                selectedEndpoint = endpoint;
                break;
            }
        }

        return selectedEndpoint;
    }

    /**
     * Subscribes to link state changes of the endpoints. The subscribing {@link java.lang.Object} must
     * have one or more {@link com.squareup.otto.Subscribe}-annotated methods, each taking a single
     * parameter that corresponds to the {@link com.groundupworks.wings.WingsEndpoint.LinkEvent} subclass of the
     * endpoint subscribed to.
     * <p/>
     * Upon subscription, the subscriber will immediately receive an event with the current link state
     * even though the link state did not actually change. This initial value allows the subscriber
     * to reflect the initial link state in the ui.
     *
     * @param object the {@link java.lang.Object} subscribing to the link state changes of the endpoints.
     * @throws IllegalStateException Wings must be initialized. See {@link Wings#init(IWingsModule, Class[])}.
     */
    public static void subscribe(Object object) throws IllegalStateException {
        if (!sIsInitialized) {
            throw new IllegalStateException("Wings must be initialized. See Wings#init().");
        }
        WingsInjector.getBus().register(object);
    }

    /**
     * Unsubscribes to link state changes of the endpoints.
     *
     * @param object the {@link java.lang.Object} you wish to unsubscribe.
     * @throws IllegalStateException Wings must be initialized. See {@link Wings#init(IWingsModule, Class[])}.
     */
    public static void unsubscribe(Object object) throws IllegalStateException {
        if (!sIsInitialized) {
            throw new IllegalStateException("Wings must be initialized. See Wings#init().");
        }
        WingsInjector.getBus().unregister(object);
    }

    /**
     * Shares an image to the specified endpoint. The client is responsible for ensuring that the file
     * exists and the endpoint is linked.
     *
     * @param filePath      the local path to the file to share.
     * @param endpointClazz the {@link java.lang.Class} of the endpoint to share to.
     * @return {@code true} if successful; {@code false} otherwise.
     * @throws IllegalStateException Wings must be initialized. See {@link Wings#init(IWingsModule, Class[])}.
     */
    public static boolean share(String filePath, Class<? extends WingsEndpoint> endpointClazz) throws IllegalStateException {
        if (!sIsInitialized) {
            throw new IllegalStateException("Wings must be initialized. See Wings#init().");
        }
        WingsEndpoint endpoint = Wings.getEndpoint(endpointClazz);
        if (endpoint != null) {
            WingsEndpoint.LinkInfo linkInfo = endpoint.getLinkInfo();
            if (linkInfo != null
                    && WingsInjector.getDatabase().createShareRequest(filePath, new Destination(linkInfo.mDestinationId, endpoint.getEndpointId()))) {
                WingsService.startWakefulService(WingsInjector.getApplicationContext());
                return true;
            }
        }

        return false;
    }

    /**
     * The default implementation of {@link com.groundupworks.wings.IWingsModule}.
     */
    @Module(
            staticInjections = {WingsService.class, WingsDbHelper.class},
            injects = {Context.class, Looper.class, Bus.class, IWingsLogger.class, WingsService.class, WingsDbHelper.class}
    )
    public static class DefaultModule implements IWingsModule {

        /**
         * The {@link android.content.Context} to run Wings.
         */
        private final Context mContext;

        /**
         * The {@link android.os.Looper} to run background tasks.
         */
        private final Looper mLooper;

        /**
         * THe logger for debug messages.
         */
        private final IWingsLogger mLogger;

        /**
         * Constructor.
         *
         * @param application the {@link android.app.Application} running Wings.
         * @param looper      the {@link android.os.Looper} to run background tasks.
         * @param logger      the logger for debug messages.
         */
        public DefaultModule(Application application, Looper looper, IWingsLogger logger) {
            mContext = application.getApplicationContext();
            mLooper = looper;
            mLogger = logger;
        }

        @Override
        @Singleton
        @Provides
        public Context provideContext() {
            return mContext;
        }

        @Override
        @Singleton
        @Provides
        public Looper provideLooper() {
            return mLooper;
        }

        @Override
        @Singleton
        @Provides
        public IWingsLogger provideLogger() {
            return mLogger;
        }

        @Override
        @Singleton
        @Provides
        public Bus provideBus() {
            return new Bus();
        }
    }
}
