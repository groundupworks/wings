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

import com.groundupworks.wings.core.WingsService;

import java.util.Map;

/**
 * An interface for printing logs.
 *
 * @author Benedict Lau
 */
public interface IWingsLogger {

    /**
     * Logs a debug message.
     *
     * @param clazz      the {@link Class}.
     * @param methodName the name of the method.
     * @param msg        the debug message.
     */
    public void log(Class<?> clazz, String methodName, String msg);

    /**
     * Logs an event.
     *
     * @param eventName       the name of the event.
     * @param eventParameters the parameters associated with the event.
     */
    public void log(String eventName, Map<String, String> eventParameters);

    /**
     * Logs an event.
     *
     * @param eventName the name of the event.
     */
    public void log(String eventName);

    /**
     * Lifecycle callback when a {@link com.groundupworks.wings.core.WingsService} instance is created.
     *
     * @param service the {@link com.groundupworks.wings.core.WingsService} instance.
     */
    public void onWingsServiceCreated(WingsService service);

    /**
     * Lifecycle callback when a {@link com.groundupworks.wings.core.WingsService} instance is destroyed.
     *
     * @param service the {@link com.groundupworks.wings.core.WingsService} instance.
     */
    public void onWingsServiceDestroyed(WingsService service);
}
