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
package com.groundupworks.wings.dropbox;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CreateFolderErrorException;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;
import com.groundupworks.wings.WingsEndpoint;
import com.groundupworks.wings.core.Destination;
import com.groundupworks.wings.core.ShareRequest;
import com.squareup.otto.Produce;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Wings endpoint for Dropbox.
 *
 * @author Benedict Lau
 */
public class DropboxEndpoint extends WingsEndpoint {

    /**
     * Dropbox endpoint id.
     */
    private static final int ENDPOINT_ID = 1;

    /**
     * Dropbox client config.
     */
    private static final DbxRequestConfig DROPBOX_CLIENT_CONFIG = DbxRequestConfig
            .newBuilder("com.groundupworks.wings.dropbox")
            .build();

    /**
     * A lock object used to synchronize access on {@link #mDropboxApi}.
     */
    private final Object mDropboxApiLock = new Object();

    /**
     * The Dropbox API. Access is synchronized on the {@link #mDropboxApiLock}.
     */
    private DbxClientV2 mDropboxApi = null;

    /**
     * Flag to track if a link request is started.
     */
    private boolean mIsLinkRequested = false;

    //
    // Private methods.
    //

    /**
     * Finishes a link request. Does nothing if {@link #mIsLinkRequested} is false prior to this call.
     *
     * @return true if linking is successful; false otherwise.
     */
    private boolean finishLinkRequest() {
        boolean isSuccessful = false;

        if (mIsLinkRequested) {
            synchronized (mDropboxApiLock) {
                String accessToken = Auth.getOAuth2Token();
                if (!TextUtils.isEmpty(accessToken)) {
                    // Set access token on the client.
                    mDropboxApi = new DbxClientV2(DROPBOX_CLIENT_CONFIG, accessToken);
                    isSuccessful = true;
                }
            }

            // Reset flag.
            mIsLinkRequested = false;
        }
        return isSuccessful;
    }

    /**
     * Links an account in a background thread. If unsuccessful, the link error is handled on a ui thread and a
     * {@link Toast} will be displayed.
     */
    private void link() {
        synchronized (mDropboxApiLock) {
            if (mDropboxApi != null) {
                final DbxClientV2 dropboxApi = mDropboxApi;

                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        String accountName = null;
                        String shareUrl = null;
                        String accessToken = null;

                        // Request params.
                        synchronized (mDropboxApiLock) {
                            // Create directory for storing photos.
                            if (createPhotoFolder(dropboxApi)) {
                                // Get account params.
                                accountName = requestAccountName(dropboxApi);
                                shareUrl = requestShareUrl(dropboxApi);
                                accessToken = Auth.getOAuth2Token();
                            }
                        }

                        // Validate account settings and store.
                        Handler uiHandler = new Handler(Looper.getMainLooper());
                        if (accountName != null && accountName.length() > 0 && shareUrl != null
                                && shareUrl.length() > 0 && accessToken != null) {
                            storeAccountParams(accountName, shareUrl, accessToken);

                            // Emit link state change event on ui thread.
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    notifyLinkStateChanged(new LinkEvent(true));
                                }
                            });
                        } else {
                            // Handle error on ui thread.
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    handleLinkError();
                                }
                            });
                        }
                    }
                });
            }
        }
    }

    /**
     * Handles an error case during the linking process.
     */
    private void handleLinkError() {
        // Show toast to indicate error during linking.
        showLinkError();

        // Unlink account to ensure proper reset.
        unlink();
    }

    /**
     * Displays the link error message.
     */
    private void showLinkError() {
        Toast.makeText(mContext, mContext.getString(R.string.wings_dropbox__error_link), Toast.LENGTH_SHORT).show();
    }

    /**
     * Creates a directory for photos if one does not already exist. If the folder already exists, this call will
     * do nothing.
     *
     * @param dropboxApi the {@link DbxClientV2}.
     * @return true if the directory is created or it already exists; false otherwise.
     */
    private boolean createPhotoFolder(DbxClientV2 dropboxApi) {
        boolean folderCreated = false;
        if (dropboxApi != null) {
            try {
                dropboxApi.files().createFolderV2("/" + mContext.getString(R.string.wings_dropbox__photo_folder));
                folderCreated = true;
            } catch (CreateFolderErrorException e) {
                // Consider the folder created if the folder already exists.
                folderCreated = e.errorValue.isPath() && e.errorValue.getPathValue().isConflict();
            } catch (DbxException e) {
                // Do nothing.
            }
        }
        return folderCreated;
    }

    /**
     * Requests the linked account name.
     *
     * @param dropboxApi the {@link DbxClientV2}.
     * @return the account name; or null if not linked.
     */
    private String requestAccountName(DbxClientV2 dropboxApi) {
        String accountName = null;
        if (dropboxApi != null) {
            try {
                accountName = dropboxApi.users().getCurrentAccount().getName().getDisplayName();
            } catch (DbxException e) {
                // Do nothing.
            }
        }
        return accountName;
    }

    /**
     * Requests the share url of the linked folder.
     *
     * @param dropboxApi the {@link DbxClientV2}.
     * @return the url; or null if not linked.
     */
    private String requestShareUrl(DbxClientV2 dropboxApi) {
        String shareUrl = null;
        if (dropboxApi != null) {
            try {
                final String path = "/" + mContext.getString(R.string.wings_dropbox__photo_folder);
                List<SharedLinkMetadata> sharedLinks = dropboxApi.sharing()
                        .listSharedLinksBuilder()
                        .withPath(path)
                        .start()
                        .getLinks();
                if (sharedLinks.size() > 0) {
                    shareUrl = sharedLinks.get(0).getUrl();
                } else {
                    shareUrl = dropboxApi.sharing().createSharedLinkWithSettings(path).getUrl();
                }
            } catch (DbxException e) {
                // Do nothing.
            }
        }
        return shareUrl;
    }

    /**
     * Stores the account params in persisted storage.
     *
     * @param accountName the user name associated with the account.
     * @param shareUrl    the share url associated with the account.
     * @param accessToken the access token.
     */
    private void storeAccountParams(String accountName, String shareUrl, String accessToken) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.putString(mContext.getString(R.string.wings_dropbox__account_name_key), accountName);
        editor.putString(mContext.getString(R.string.wings_dropbox__share_url_key), shareUrl);
        editor.putString(mContext.getString(R.string.wings_dropbox__access_token_key), accessToken);

        // Set preference to linked.
        editor.putBoolean(mContext.getString(R.string.wings_dropbox__link_key), true);
        editor.apply();
    }

    /**
     * Removes the account params from persisted storage.
     */
    private void removeAccountParams() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.remove(mContext.getString(R.string.wings_dropbox__account_name_key));
        editor.remove(mContext.getString(R.string.wings_dropbox__share_url_key));
        editor.remove(mContext.getString(R.string.wings_dropbox__access_token_key));

        // Set preference to unlinked.
        editor.putBoolean(mContext.getString(R.string.wings_dropbox__link_key), false);
        editor.apply();
    }

    /**
     * Gets the stored access token associated with the linked account.
     *
     * @return the access token; or null if unlinked.
     */
    private String getLinkedAccessToken() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getString(mContext.getString(R.string.wings_dropbox__access_token_key), null);
    }

    /**
     * Gets the share url associated with the linked account.
     *
     * @return the url; or null if unlinked.
     */
    private String getLinkedShareUrl() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getString(mContext.getString(R.string.wings_dropbox__share_url_key), null);
    }

    //
    // Public methods.
    //

    @Override
    public int getEndpointId() {
        return ENDPOINT_ID;
    }

    @Override
    public void startLinkRequest(Activity activity, Fragment fragment) {
        mIsLinkRequested = true;

        synchronized (mDropboxApiLock) {
            Auth.startOAuth2Authentication(activity, mContext.getString(R.string.wings_dropbox__app_key));
        }
    }

    @Override
    public void unlink() {
        // Unlink in persisted storage.
        removeAccountParams();

        // Unlink any current session.
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                synchronized (mDropboxApiLock) {
                    if (mDropboxApi != null) {
                        try {
                            mDropboxApi.auth().tokenRevoke();
                            mDropboxApi = null;
                        } catch (DbxException e) {
                            // Do nothing.
                        }
                    }
                }
            }
        });

        // Emit link state change event.
        notifyLinkStateChanged(new LinkEvent(false));

        // Remove existing share requests in a background thread.
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                mDatabase.deleteShareRequests(new Destination(DestinationId.APP_FOLDER, ENDPOINT_ID));
            }
        });
    }

    @Override
    public boolean isLinked() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.wings_dropbox__link_key), false);
    }

    @Override
    public void onResumeImpl() {
        if (mIsLinkRequested) {
            // Check if link request was successful.
            if (finishLinkRequest()) {
                link();
            } else {
                handleLinkError();
            }
        }
    }

    @Override
    public void onActivityResultImpl(Activity activity, Fragment fragment, int requestCode, int resultCode, Intent data) {
        // Do nothing.
    }

    @Override
    public LinkInfo getLinkInfo() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String accountName = preferences.getString(mContext.getString(R.string.wings_dropbox__account_name_key), null);
        String shareUrl = getLinkedShareUrl();
        if (accountName != null && accountName.length() > 0 && shareUrl != null && shareUrl.length() > 0) {
            String destinationDescription = mContext.getString(R.string.wings_dropbox__destination_description, accountName, shareUrl);
            return new LinkInfo(accountName, DestinationId.APP_FOLDER, destinationDescription);
        }
        return null;
    }

    @Override
    public Set<ShareNotification> processShareRequests() {
        Set<ShareNotification> notifications = new HashSet<ShareNotification>();

        // Get access token associated with the linked account.
        String accessToken = getLinkedAccessToken();
        String shareUrl = getLinkedShareUrl();
        if (accessToken != null && shareUrl != null) {
            // Get share requests for Dropbox.
            Destination destination = new Destination(DestinationId.APP_FOLDER, ENDPOINT_ID);
            List<ShareRequest> shareRequests = mDatabase.checkoutShareRequests(destination);
            int shared = 0;

            if (!shareRequests.isEmpty()) {
                // Start new session with the persisted access token.
                DbxClientV2 dropboxApi = new DbxClientV2(DROPBOX_CLIENT_CONFIG, accessToken);

                // Process share requests.
                for (ShareRequest shareRequest : shareRequests) {
                    File file = new File(shareRequest.getFilePath());
                    FileInputStream inputStream = null;
                    try {
                        inputStream = new FileInputStream(file);

                        // Upload file.
                        dropboxApi.files()
                                .uploadBuilder("/" + mContext.getString(R.string.wings_dropbox__photo_folder) + "/" + file.getName())
                                .uploadAndFinish(inputStream);

                        // Mark as successfully processed.
                        mDatabase.markSuccessful(shareRequest.getId());

                        shared++;
                    } catch (InvalidAccessTokenException e) {
                        mDatabase.markFailed(shareRequest.getId());

                        // Update account linking state to unlinked.
                        unlink();
                    } catch (Exception e) {
                        mDatabase.markFailed(shareRequest.getId());
                    } finally {
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                // Do nothing.
                            }
                        }
                    }
                }
            }

            // Construct and add notification representing share results.
            if (shared > 0) {
                notifications.add(new DropboxShareNotification(mContext, destination.getHash(), shareUrl, shared, shareUrl));
            }
        }

        return notifications;
    }

    @Override
    @Produce
    public DropboxEndpoint.LinkEvent produceLinkEvent() {
        return new LinkEvent(isLinked());
    }

    //
    // Public interfaces and classes.
    //

    /**
     * The list of destination ids.
     */
    public interface DestinationId extends WingsEndpoint.DestinationId {

        /**
         * The Dropbox app folder.
         */
        int APP_FOLDER = 0;
    }

    /**
     * The link event implementation associated with this endpoint.
     */
    public static class LinkEvent extends WingsEndpoint.LinkEvent {

        /**
         * Private constructor.
         *
         * @param isLinked true if current link state for this endpoint is linked; false otherwise.
         */
        private LinkEvent(boolean isLinked) {
            super(DropboxEndpoint.class, isLinked);
        }
    }
}