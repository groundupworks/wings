/*
 * Copyright (C) 2012 Benedict Lau
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
package com.groundupworks.wings.facebook;

import android.os.Bundle;
import android.text.TextUtils;

/**
 * A model object to contain Facebook settings. An instance of {@link FacebookSettings} cannot be directly constructed,
 * but through one of its static newInstance() methods, which does validation internally to ensure the validity of the
 * constructed instance.
 *
 * @author Benedict Lau
 */
public class FacebookSettings {

    //
    // Bundle keys.
    //

    private static final String BUNDLE_KEY_DESTINATION_ID = "destinationId";

    private static final String BUNDLE_KEY_ACCOUNT_NAME = "accountName";

    private static final String BUNDLE_KEY_ALBUM_NAME = "albumName";

    private static final String BUNDLE_KEY_ALBUM_GRAPH_PATH = "albumGraphPath";

    private static final String BUNDLE_KEY_PAGE_ACCESS_TOKEN = "pageAccessToken";

    private static final String BUNDLE_KEY_PHOTO_PRIVACY = "photoPrivacy";

    //
    // Account settings.
    //

    private int mDestinationId;

    private String mAccountName;

    private String mAlbumName;

    private String mAlbumGraphPath;

    private String mPageAccessToken = null;

    private String mPhotoPrivacy = null;

    /**
     * Private constructor.
     *
     * @param destinationId   the destination id.
     * @param accountName     the user name associated with the account.
     * @param albumName       the name of the album to share to.
     * @param albumGraphPath  the graph path of the album to share to.
     * @param pageAccessToken the Page access token. Only used for sharing to Pages. May be null.
     * @param photoPrivacy    the privacy level of shared photos. Only used for albums with 'custom' privacy level. May be null.
     */
    private FacebookSettings(int destinationId, String accountName, String albumName, String albumGraphPath, String pageAccessToken, String photoPrivacy) {
        mDestinationId = destinationId;
        mAccountName = accountName;
        mAlbumName = albumName;
        mAlbumGraphPath = albumGraphPath;
        mPageAccessToken = pageAccessToken;
        mPhotoPrivacy = photoPrivacy;
    }

    //
    // Package private methods.
    //

    /**
     * Creates a new {@link FacebookSettings} instance.
     *
     * @param destinationId   the destination id.
     * @param accountName     the user name associated with the account.
     * @param albumName       the name of the album to share to.
     * @param albumGraphPath  the graph path of the album to share to.
     * @param pageAccessToken the Page access token. Only used for sharing to Pages. May be null.
     * @param photoPrivacy    the privacy level of shared photos. Only used for albums with 'custom' privacy level. May be null.
     * @return a new {@link FacebookSettings} instance; or null if any of the params are invalid.
     */
    static FacebookSettings newInstance(int destinationId, String accountName, String albumName, String albumGraphPath, String pageAccessToken, String photoPrivacy) {
        FacebookSettings settings = null;

        if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(albumName) && !TextUtils.isEmpty(albumGraphPath)) {
            if (destinationId == FacebookEndpoint.DestinationId.PROFILE) {
                settings = new FacebookSettings(destinationId, accountName, albumName, albumGraphPath, pageAccessToken, photoPrivacy);
            } else if (destinationId == FacebookEndpoint.DestinationId.PAGE && !TextUtils.isEmpty(pageAccessToken)) {
                settings = new FacebookSettings(destinationId, accountName, albumName, albumGraphPath, pageAccessToken, photoPrivacy);
            }
        }

        return settings;
    }

    /**
     * Creates a new {@link FacebookSettings} instance from a {@link Bundle} created by the {@link #toBundle()} method.
     *
     * @param bundle the {@link Bundle}.
     * @return a new {@link FacebookSettings} instance; or null if the {@link Bundle} is invalid.
     */
    static FacebookSettings newInstance(Bundle bundle) {
        FacebookSettings settings = null;

        int destinationId = bundle.getInt(BUNDLE_KEY_DESTINATION_ID);
        String accountName = bundle.getString(BUNDLE_KEY_ACCOUNT_NAME);
        String albumName = bundle.getString(BUNDLE_KEY_ALBUM_NAME);
        String albumGraphPath = bundle.getString(BUNDLE_KEY_ALBUM_GRAPH_PATH);
        String pageAccessToken = bundle.getString(BUNDLE_KEY_PAGE_ACCESS_TOKEN);
        String photoPrivacy = bundle.getString(BUNDLE_KEY_PHOTO_PRIVACY);

        if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(albumName) && !TextUtils.isEmpty(albumGraphPath)) {
            if (destinationId == FacebookEndpoint.DestinationId.PROFILE) {
                settings = new FacebookSettings(destinationId, accountName, albumName, albumGraphPath, pageAccessToken, photoPrivacy);
            } else if (destinationId == FacebookEndpoint.DestinationId.PAGE && !TextUtils.isEmpty(pageAccessToken)) {
                settings = new FacebookSettings(destinationId, accountName, albumName, albumGraphPath, pageAccessToken, photoPrivacy);
            }
        }

        return settings;
    }

    /**
     * Creates a {@link Bundle} from the {@link FacebookSettings}.
     *
     * @return the {@link Bundle}.
     */
    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(BUNDLE_KEY_DESTINATION_ID, mDestinationId);
        bundle.putString(BUNDLE_KEY_ACCOUNT_NAME, mAccountName);
        bundle.putString(BUNDLE_KEY_ALBUM_NAME, mAlbumName);
        bundle.putString(BUNDLE_KEY_ALBUM_GRAPH_PATH, mAlbumGraphPath);
        if (!TextUtils.isEmpty(mPageAccessToken)) {
            bundle.putString(BUNDLE_KEY_PAGE_ACCESS_TOKEN, mPageAccessToken);
        }
        if (!TextUtils.isEmpty(mPhotoPrivacy)) {
            bundle.putString(BUNDLE_KEY_PHOTO_PRIVACY, mPhotoPrivacy);
        }

        return bundle;
    }

    /**
     * @return the destination id.
     */
    int getDestinationId() {
        return mDestinationId;
    }

    /**
     * @return the user name associated with the account.
     */
    String getAccountName() {
        return mAccountName;
    }

    /**
     * @return the name of the album to share to.
     */
    String getAlbumName() {
        return mAlbumName;
    }

    /**
     * @return the graph path of the album to share to.
     */
    String getAlbumGraphPath() {
        return mAlbumGraphPath;
    }

    /**
     * @return the Page access token. Only used for sharing to Pages. May be null.
     */
    String optPageAccessToken() {
        return mPageAccessToken;
    }

    /**
     * @return the privacy level of shared photos. Only used for albums with 'custom' privacy level. May be null.
     */
    String optPhotoPrivacy() {
        return mPhotoPrivacy;
    }
}
