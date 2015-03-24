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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.widget.Toast;

import com.facebook.FacebookException;
import com.facebook.FacebookRequestError;
import com.facebook.FacebookRequestError.Category;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Request.Callback;
import com.facebook.Request.GraphUserCallback;
import com.facebook.RequestBatch;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.Session.NewPermissionsRequest;
import com.facebook.Session.OpenRequest;
import com.facebook.SessionDefaultAudience;
import com.facebook.SessionLoginBehavior;
import com.facebook.SessionState;
import com.facebook.model.GraphObject;
import com.groundupworks.wings.WingsEndpoint;
import com.groundupworks.wings.core.Destination;
import com.groundupworks.wings.core.ShareRequest;
import com.squareup.otto.Produce;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * The Wings endpoint for Facebook.
 *
 * @author Benedict Lau
 */
public class FacebookEndpoint extends WingsEndpoint {

    /**
     * Facebook endpoint id.
     */
    private static final int ENDPOINT_ID = 0;

    /**
     * Timeout value for http requests.
     */
    private static final int HTTP_REQUEST_TIMEOUT = 120000;

    /**
     * Facebook app package name.
     */
    private static final String FACEBOOK_APP_PACKAGE = "com.facebook.katana";

    //
    // Facebook permissions.
    //

    /**
     * Permission to public profile.
     */
    private static final String PERMISSION_PUBLIC_PROFILE = "public_profile";

    /**
     * Permission to user photos.
     */
    private static final String PERMISSION_USER_PHOTOS = "user_photos";

    /**
     * Permission to manage Pages.
     */
    private static final String PERMISSION_MANAGE_PAGES = "manage_pages";

    /**
     * Permission to publish content.
     */
    private static final String PERMISSION_PUBLISH_ACTIONS = "publish_actions";

    /**
     * Request code to use for {@link Activity#startActivityForResult(android.content.Intent, int)}
     * with {@link com.groundupworks.wings.facebook.FacebookLoginActivity}.
     */
    private static final int LOGIN_REQUEST_CODE = ENDPOINT_ID << 4;

    /**
     * Request code to use for {@link Activity#startActivityForResult(android.content.Intent, int)}
     * with {@link com.groundupworks.wings.facebook.FacebookSettingsActivity}.
     */
    private static final int SETTINGS_REQUEST_CODE = ENDPOINT_ID << 4 + 1;

    //
    // Link request state machine.
    //

    private static final int STATE_NONE = -1;

    private static final int STATE_LOGIN_REQUEST = 0;

    private static final int STATE_OPEN_SESSION_REQUEST = 1;

    private static final int STATE_PUBLISH_PERMISSIONS_REQUEST = 2;

    private static final int STATE_SETTINGS_REQUEST = 3;

    //
    // Account listing params.
    //

    /**
     * The graph path to list admin accounts for Pages.
     */
    private static final String ACCOUNTS_LISTING_GRAPH_PATH = "me/accounts";

    /**
     * The key for params to request.
     */
    private static final String ACCOUNTS_LISTING_FEILDS_KEY = "fields";

    /**
     * The value for params to request.
     */
    private static final String ACCOUNTS_LISTING_FIELDS_VALUE = "id,name,access_token,perms";

    //
    // Account listing results.
    //

    static final String ACCOUNTS_LISTING_RESULT_DATA_KEY = "data";

    static final String ACCOUNTS_LISTING_FIELD_ID = "id";

    static final String ACCOUNTS_LISTING_FIELD_NAME = "name";

    static final String ACCOUNTS_LISTING_FIELD_ACCESS_TOKEN = "access_token";

    static final String ACCOUNTS_LISTING_FIELD_PERMS = "perms";

    /**
     * The content creation account permission.
     */
    static final String ACCOUNT_PERM_CREATE_CONTENT = "CREATE_CONTENT";

    /**
     * The {@link String} to append to Page id to create graph path.
     */
    static final String PAGE_ID_TO_GRAPH_PATH = "/photos";

    //
    // Albums listing params.
    //

    /**
     * The graph path to list photo albums.
     */
    private static final String ALBUMS_LISTING_GRAPH_PATH = "me/albums";

    /**
     * The key for max number of albums to request.
     */
    private static final String ALBUMS_LISTING_LIMIT_KEY = "limit";

    /**
     * The value for max number of albums to request.
     */
    private static final String ALBUMS_LISTING_LIMIT_VALUE = "200";

    /**
     * The key for params to request.
     */
    private static final String ALBUMS_LISTING_FEILDS_KEY = "fields";

    /**
     * The value for params to request.
     */
    private static final String ALBUMS_LISTING_FIELDS_VALUE = "id,name,type,privacy,can_upload";

    //
    // Album listing results.
    //

    static final String ALBUMS_LISTING_RESULT_DATA_KEY = "data";

    static final String ALBUMS_LISTING_FIELD_ID = "id";

    static final String ALBUMS_LISTING_FIELD_NAME = "name";

    static final String ALBUMS_LISTING_FIELD_TYPE = "type";

    static final String ALBUMS_LISTING_FIELD_PRIVACY = "privacy";

    static final String ALBUMS_LISTING_FIELD_CAN_UPLOAD = "can_upload";

    /**
     * The {@link String} to append to album id to create graph path.
     */
    static final String ALBUM_ID_TO_GRAPH_PATH = "/photos";

    //
    // Default album params.
    //

    /**
     * The graph path of the app album.
     */
    static final String APP_ALBUM_GRAPH_PATH = "me/photos";

    /**
     * The privacy level of the Page album.
     */
    static final String PAGE_PRIVACY = "page";

    /**
     * The configurable privacy level of the app album.
     */
    static final String APP_ALBUM_PRIVACY = "select privacy level";

    /**
     * The type of the default album to share to.
     */
    static final String DEFAULT_ALBUM_TYPE = "app";

    //
    // Privacy levels for albums with 'custom' privacy level.
    //

    /**
     * Shared photos are visible to 'Only Me'.
     */
    static final String PHOTO_PRIVACY_SELF = "{'value':'SELF'}";

    /**
     * Shared photos are visible to 'Friends'.
     */
    static final String PHOTO_PRIVACY_FRIENDS = "{'value':'ALL_FRIENDS'}";

    /**
     * Shared photos are visible to 'Friends of Friends'.
     */
    static final String PHOTO_PRIVACY_FRIENDS_OF_FRIENDS = "{'value':'FRIENDS_OF_FRIENDS'}";

    /**
     * Shared photos are visible to 'Public'.
     */
    static final String PHOTO_PRIVACY_EVERYONE = "{'value':'EVERYONE'}";

    //
    // Share params.
    //

    private static final String SHARE_KEY_PICTURE = "picture";

    private static final String SHARE_KEY_PAGE_ACCESS_TOKEN = "access_token";

    private static final String SHARE_KEY_PHOTO_PRIVACY = "privacy";

    private static final String SHARE_KEY_PHOTO_ID = "id";

    private static final String SHARE_NOTIFICATION_INTENT_BASE_URI = "fb://photo/";

    /**
     * Flag to track if a link request is started.
     */
    private volatile int mLinkRequestState = STATE_NONE;

    //
    // Private methods.
    //

    /**
     * Opens a new session with read permissions.
     *
     * @param activity the {@link Activity}.
     * @param fragment the {@link Fragment}. May be null.
     */
    private void startOpenSessionRequest(final Activity activity, final Fragment fragment) {
        // State transition.
        mLinkRequestState = STATE_OPEN_SESSION_REQUEST;

        // Construct status callback.
        Session.StatusCallback statusCallback = new Session.StatusCallback() {
            @Override
            public void call(Session session, SessionState state, Exception exception) {
                if (mLinkRequestState == STATE_OPEN_SESSION_REQUEST && state.isOpened()) {
                    // Request publish permissions.
                    if (!startPublishPermissionsRequest(activity, fragment)) {
                        handleLinkError();
                    }
                }
            }
        };

        // Construct new session.
        Session session = new Session(activity);
        Session.setActiveSession(session);

        // Construct read permissions to request for.
        List<String> readPermissions = new LinkedList<String>();
        readPermissions.add(PERMISSION_PUBLIC_PROFILE);
        readPermissions.add(PERMISSION_USER_PHOTOS);

        // Construct open request.
        OpenRequest openRequest;
        if (fragment == null) {
            openRequest = new OpenRequest(activity);
        } else {
            openRequest = new OpenRequest(fragment);
        }

        // Allow SSO login only because the web login does not allow PERMISSION_USER_PHOTOS to be bundled with the
        // first openForRead() call.
        openRequest.setLoginBehavior(SessionLoginBehavior.SSO_ONLY);
        openRequest.setPermissions(readPermissions);
        openRequest.setDefaultAudience(SessionDefaultAudience.EVERYONE);
        openRequest.setCallback(statusCallback);

        // Execute open request.
        session.openForRead(openRequest);
    }

    /**
     * Finishes a {@link com.groundupworks.wings.facebook.FacebookEndpoint#startOpenSessionRequest(android.app.Activity, android.support.v4.app.Fragment)}.
     *
     * @param activity    the {@link Activity}.
     * @param requestCode the integer request code originally supplied to startActivityForResult(), allowing you to identify who
     *                    this result came from.
     * @param resultCode  the integer result code returned by the child activity through its setResult().
     * @param data        an Intent, which can return result data to the caller (various data can be attached to Intent
     *                    "extras").
     * @return true if open session request is successful; false otherwise.
     */
    private boolean finishOpenSessionRequest(final Activity activity, int requestCode, int resultCode, Intent data) {
        boolean isSuccessful = false;

        Session session = Session.getActiveSession();

        // isOpened() must be called after onActivityResult().
        if (session != null && session.onActivityResult(activity, requestCode, resultCode, data) && session.isOpened()
                && session.getPermissions().contains(PERMISSION_USER_PHOTOS)) {
            isSuccessful = true;
        }

        return isSuccessful;
    }

    /**
     * Requests for permissions to publish publicly. Requires an opened active {@link Session}.
     *
     * @param activity the {@link Activity}.
     * @param fragment the {@link Fragment}. May be null.
     * @return true if the request is made; false if no opened {@link Session} is active.
     */
    private boolean startPublishPermissionsRequest(Activity activity, Fragment fragment) {
        boolean isSuccessful = false;

        // State transition.
        mLinkRequestState = STATE_PUBLISH_PERMISSIONS_REQUEST;

        Session session = Session.getActiveSession();
        if (session != null && session.isOpened()) {
            // Construct publish permissions.
            List<String> publishPermissions = new LinkedList<String>();
            publishPermissions.add(PERMISSION_PUBLISH_ACTIONS);
            publishPermissions.add(PERMISSION_MANAGE_PAGES);

            // Construct permissions request to publish publicly.
            NewPermissionsRequest permissionsRequest;
            if (fragment == null) {
                permissionsRequest = new NewPermissionsRequest(activity, publishPermissions);
            } else {
                if (fragment.getActivity() == null) {
                    return false;
                }
                permissionsRequest = new NewPermissionsRequest(fragment, publishPermissions);
            }
            permissionsRequest.setDefaultAudience(SessionDefaultAudience.EVERYONE);

            // Execute publish permissions request.
            session.requestNewPublishPermissions(permissionsRequest);
            isSuccessful = true;
        }
        return isSuccessful;
    }

    /**
     * Finishes a {@link com.groundupworks.wings.facebook.FacebookEndpoint#startPublishPermissionsRequest(android.app.Activity, android.support.v4.app.Fragment)}.
     *
     * @param activity    the {@link Activity}.
     * @param requestCode the integer request code originally supplied to startActivityForResult(), allowing you to identify who
     *                    this result came from.
     * @param resultCode  the integer result code returned by the child activity through its setResult().
     * @param data        an Intent, which can return result data to the caller (various data can be attached to Intent
     *                    "extras").
     * @return true if publish permissions request is successful; false otherwise.
     */
    private boolean finishPublishPermissionsRequest(Activity activity, int requestCode, int resultCode, Intent data) {
        boolean isSuccessful = false;

        Session session = Session.getActiveSession();

        // isOpened() must be called after onActivityResult().
        if (session != null && session.onActivityResult(activity, requestCode, resultCode, data) && session.isOpened()
                && session.getPermissions().contains(PERMISSION_PUBLISH_ACTIONS)) {
            isSuccessful = true;
        }

        return isSuccessful;
    }

    /**
     * Requests for permissions to publish publicly. Requires an opened active {@link Session}.
     *
     * @param activity the {@link Activity}.
     * @param fragment the {@link Fragment}. May be null.
     * @return true if the request is made; false if no opened {@link Session} is active.
     */
    private boolean startSettingsRequest(Activity activity, Fragment fragment) {
        boolean isSuccessful = false;

        // State transition.
        mLinkRequestState = STATE_SETTINGS_REQUEST;

        Session session = Session.getActiveSession();
        if (session != null && session.isOpened()) {
            // Start activity for result.
            Intent intent = new Intent(activity, FacebookSettingsActivity.class);
            if (fragment == null) {
                activity.startActivityForResult(intent, SETTINGS_REQUEST_CODE);
            } else {
                fragment.startActivityForResult(intent, SETTINGS_REQUEST_CODE);
            }

            isSuccessful = true;
        }
        return isSuccessful;
    }

    /**
     * Finishes a {@link com.groundupworks.wings.facebook.FacebookEndpoint#startSettingsRequest(android.app.Activity, android.support.v4.app.Fragment)}.
     *
     * @param requestCode the integer request code originally supplied to startActivityForResult(), allowing you to identify who
     *                    this result came from.
     * @param resultCode  the integer result code returned by the child activity through its setResult().
     * @param data        an Intent, which can return result data to the caller (various data can be attached to Intent
     *                    "extras").
     * @return the settings; or null if failed.
     */
    private FacebookSettings finishSettingsRequest(int requestCode, int resultCode, Intent data) {
        FacebookSettings settings = null;

        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            // Construct settings from the extras bundle.
            settings = FacebookSettings.newInstance(data.getExtras());
        }
        return settings;
    }

    /**
     * Checks if the Facebook native app is installed on the device.
     *
     * @return true if installed; false otherwise.
     */
    private boolean isFacebookAppInstalled() {
        boolean isInstalled = false;
        try {
            // An exception will be thrown if the package is not found.
            mContext.getPackageManager().getApplicationInfo(FACEBOOK_APP_PACKAGE, 0);
            isInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            // Do nothing.
        }
        return isInstalled;
    }

    /**
     * Links an account.
     *
     * @param settings the {@link com.groundupworks.wings.facebook.FacebookSettings}.
     * @return true if successful; false otherwise.
     */
    private boolean link(FacebookSettings settings) {
        boolean isSuccessful = false;

        // Validate account params and store.
        if (settings != null) {
            storeSettings(settings);

            // Emit link state change event.
            notifyLinkStateChanged(new LinkEvent(true));

            isSuccessful = true;
        }
        return isSuccessful;
    }

    /**
     * Handles an error case during the linking process.
     */
    private void handleLinkError() {
        // Reset link request state.
        mLinkRequestState = STATE_NONE;

        // Show toast to indicate error during linking.
        if (isFacebookAppInstalled()) {
            showLinkError();
        } else {
            showFacebookAppError();
        }

        // Unlink account to ensure proper reset.
        unlink();
    }

    /**
     * Displays the link error message.
     */
    private void showLinkError() {
        Toast.makeText(mContext, mContext.getString(R.string.wings_facebook__error_link), Toast.LENGTH_SHORT).show();
    }

    /**
     * Displays the Facebook app error message.
     */
    private void showFacebookAppError() {
        Toast.makeText(mContext, mContext.getString(R.string.wings_facebook__error_facebook_app), Toast.LENGTH_SHORT).show();
    }

    /**
     * Stores the link settings in persisted storage.
     *
     * @param settings the {@link com.groundupworks.wings.facebook.FacebookSettings}.
     */
    private void storeSettings(FacebookSettings settings) {
        int destinationId = settings.getDestinationId();
        String accountName = settings.getAccountName();
        String albumName = settings.getAlbumName();
        String albumGraphPath = settings.getAlbumGraphPath();
        String pageAccessToken = settings.optPageAccessToken();
        String photoPrivacy = settings.optPhotoPrivacy();

        Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.putInt(mContext.getString(R.string.wings_facebook__destination_id_key), destinationId);
        editor.putString(mContext.getString(R.string.wings_facebook__account_name_key), accountName);
        editor.putString(mContext.getString(R.string.wings_facebook__album_name_key), albumName);
        editor.putString(mContext.getString(R.string.wings_facebook__album_graph_path_key), albumGraphPath);
        if (!TextUtils.isEmpty(pageAccessToken)) {
            editor.putString(mContext.getString(R.string.wings_facebook__page_access_token_key), pageAccessToken);
        }
        if (!TextUtils.isEmpty(photoPrivacy)) {
            editor.putString(mContext.getString(R.string.wings_facebook__photo_privacy_key), photoPrivacy);
        }

        // Set preference to linked.
        editor.putBoolean(mContext.getString(R.string.wings_facebook__link_key), true);
        editor.apply();
    }

    /**
     * Fetches the link settings from persisted storage.
     *
     * @return the {@link com.groundupworks.wings.facebook.FacebookSettings}; or null if unlinked.
     */
    private FacebookSettings fetchSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (!preferences.getBoolean(mContext.getString(R.string.wings_facebook__link_key), false)) {
            return null;
        }

        int destinationId = preferences.getInt(mContext.getString(R.string.wings_facebook__destination_id_key), DestinationId.UNLINKED);
        String accountName = preferences.getString(mContext.getString(R.string.wings_facebook__account_name_key), null);
        String albumName = preferences.getString(mContext.getString(R.string.wings_facebook__album_name_key), null);
        String albumGraphPath = preferences.getString(mContext.getString(R.string.wings_facebook__album_graph_path_key), null);
        String pageAccessToken = preferences.getString(mContext.getString(R.string.wings_facebook__page_access_token_key), null);
        String photoPrivacy = preferences.getString(mContext.getString(R.string.wings_facebook__photo_privacy_key), null);

        return FacebookSettings.newInstance(destinationId, accountName, albumName, albumGraphPath, pageAccessToken, photoPrivacy);
    }

    /**
     * Removes the link settings from persisted storage.
     */
    private void removeSettings() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.remove(mContext.getString(R.string.wings_facebook__destination_id_key));
        editor.remove(mContext.getString(R.string.wings_facebook__account_name_key));
        editor.remove(mContext.getString(R.string.wings_facebook__album_name_key));
        editor.remove(mContext.getString(R.string.wings_facebook__album_graph_path_key));
        editor.remove(mContext.getString(R.string.wings_facebook__page_access_token_key));
        editor.remove(mContext.getString(R.string.wings_facebook__photo_privacy_key));

        // Set preference to unlinked.
        editor.putBoolean(mContext.getString(R.string.wings_facebook__link_key), false);
        editor.apply();
    }

    /**
     * Parses the photo id from a {@link GraphObject}.
     *
     * @param graphObject the {@link GraphObject} to parse.
     * @return the photo id; or null if not found.
     */
    private String parsePhotoId(GraphObject graphObject) {
        String photoId = null;

        if (graphObject != null) {
            JSONObject jsonObject = graphObject.getInnerJSONObject();
            if (jsonObject != null) {
                photoId = jsonObject.optString(SHARE_KEY_PHOTO_ID, null);
            }
        }
        return photoId;
    }

    //
    // Package private methods.
    //

    /**
     * Asynchronously requests the user name associated with the linked account. Requires an opened active
     * {@link Session}.
     *
     * @param graphUserCallback a {@link GraphUserCallback} when the request completes.
     * @return true if the request is made; false if no opened {@link Session} is active.
     */
    boolean requestAccountName(GraphUserCallback graphUserCallback) {
        boolean isSuccessful = false;

        Session session = Session.getActiveSession();
        if (session != null && session.isOpened()) {
            Request.newMeRequest(session, graphUserCallback).executeAsync();
            isSuccessful = true;
        }
        return isSuccessful;
    }

    /**
     * Asynchronously requests the Page accounts associated with the linked account. Requires an opened active {@link Session}.
     *
     * @param callback a {@link Callback} when the request completes.
     * @return true if the request is made; false if no opened {@link Session} is active.
     */
    boolean requestAccounts(Callback callback) {
        boolean isSuccessful = false;

        Session session = Session.getActiveSession();
        if (session != null && session.isOpened()) {
            // Construct fields to request.
            Bundle params = new Bundle();
            params.putString(ACCOUNTS_LISTING_FEILDS_KEY, ACCOUNTS_LISTING_FIELDS_VALUE);

            // Construct and execute albums listing request.
            Request request = new Request(session, ACCOUNTS_LISTING_GRAPH_PATH, params, HttpMethod.GET, callback);
            request.executeAsync();

            isSuccessful = true;
        }
        return isSuccessful;
    }

    /**
     * Asynchronously requests the albums associated with the linked account. Requires an opened active {@link Session}.
     *
     * @param callback a {@link Callback} when the request completes.
     * @return true if the request is made; false if no opened {@link Session} is active.
     */
    boolean requestAlbums(Callback callback) {
        boolean isSuccessful = false;

        Session session = Session.getActiveSession();
        if (session != null && session.isOpened()) {
            // Construct fields to request.
            Bundle params = new Bundle();
            params.putString(ALBUMS_LISTING_LIMIT_KEY, ALBUMS_LISTING_LIMIT_VALUE);
            params.putString(ALBUMS_LISTING_FEILDS_KEY, ALBUMS_LISTING_FIELDS_VALUE);

            // Construct and execute albums listing request.
            Request request = new Request(session, ALBUMS_LISTING_GRAPH_PATH, params, HttpMethod.GET, callback);
            request.executeAsync();

            isSuccessful = true;
        }
        return isSuccessful;
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
        mLinkRequestState = STATE_LOGIN_REQUEST;

        Intent intent = new Intent(activity, FacebookLoginActivity.class);
        if (fragment == null) {
            activity.startActivityForResult(intent, LOGIN_REQUEST_CODE);
        } else {
            fragment.startActivityForResult(intent, LOGIN_REQUEST_CODE);
        }
    }

    @Override
    public void unlink() {
        // Unlink in persisted storage.
        removeSettings();

        // Unlink any current session.
        Session session = Session.openActiveSessionFromCache(mContext);
        if (session != null && !session.isClosed()) {
            session.closeAndClearTokenInformation();
        }

        // Emit link state change event.
        notifyLinkStateChanged(new LinkEvent(false));

        // Remove existing share requests in a background thread.
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                mDatabase.deleteShareRequests(new Destination(DestinationId.PROFILE, ENDPOINT_ID));
                mDatabase.deleteShareRequests(new Destination(DestinationId.PAGE, ENDPOINT_ID));
            }
        });
    }

    @Override
    public boolean isLinked() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.wings_facebook__link_key), false);
    }

    @Override
    public void onResumeImpl() {
        // Do nothing.
    }

    @Override
    public void onActivityResultImpl(Activity activity, Fragment fragment, int requestCode, int resultCode, Intent data) {
        // State machine to handle the linking process.
        switch (mLinkRequestState) {
            case STATE_LOGIN_REQUEST: {
                if (requestCode == LOGIN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
                    startOpenSessionRequest(activity, fragment);
                } else {
                    handleLinkError();
                    final HashMap<String, String> parameters = new HashMap<>();
                    parameters.put("state", "STATE_LOGIN_REQUEST");
                    parameters.put("transition_failed", "requestCode=" + requestCode + " resultCode=" + resultCode);
                    sLogger.log("facebook_link_error", parameters);
                }
                break;
            }
            case STATE_OPEN_SESSION_REQUEST: {
                // Only handle the error case. If successful, the publish permissions request will be started by a
                // session callback.
                if (!finishOpenSessionRequest(activity, requestCode, resultCode, data)) {
                    handleLinkError();
                    final HashMap<String, String> parameters = new HashMap<>();
                    parameters.put("state", "STATE_OPEN_SESSION_REQUEST");
                    parameters.put("transition_failed", "finishOpenSessionRequest()");
                    sLogger.log("facebook_link_error", parameters);
                }
                break;
            }
            case STATE_PUBLISH_PERMISSIONS_REQUEST: {
                if (finishPublishPermissionsRequest(activity, requestCode, resultCode, data)) {
                    // Start request for settings.
                    if (!startSettingsRequest(activity, fragment)) {
                        handleLinkError();
                        final HashMap<String, String> parameters = new HashMap<>();
                        parameters.put("state", "STATE_PUBLISH_PERMISSIONS_REQUEST");
                        parameters.put("transition_failed", "startSettingsRequest()");
                        sLogger.log("facebook_link_error", parameters);
                    }
                } else {
                    handleLinkError();
                    final HashMap<String, String> parameters = new HashMap<>();
                    parameters.put("state", "STATE_PUBLISH_PERMISSIONS_REQUEST");
                    parameters.put("transition_failed", "finishOpenSessionRequest()");
                    sLogger.log("facebook_link_error", parameters);
                }
                break;
            }
            case STATE_SETTINGS_REQUEST: {
                // Link account.
                FacebookSettings settings = finishSettingsRequest(requestCode, resultCode, data);
                if (link(settings)) {
                    // End link request, but persist link tokens.
                    Session session = Session.getActiveSession();
                    if (session != null && !session.isClosed()) {
                        session.close();
                    }
                    mLinkRequestState = STATE_NONE;
                } else {
                    handleLinkError();
                    final HashMap<String, String> parameters = new HashMap<>();
                    parameters.put("state", "STATE_SETTINGS_REQUEST");
                    parameters.put("transition_failed", "link()");
                    sLogger.log("facebook_link_error", parameters);
                }
                break;
            }
            default: {
            }
        }
    }

    @Override
    public LinkInfo getLinkInfo() {
        FacebookSettings settings = fetchSettings();
        if (settings != null) {
            int destinationId = settings.getDestinationId();
            String accountName = settings.getAccountName();

            int resId = R.string.wings_facebook__destination_profile_description;
            if (DestinationId.PAGE == destinationId) {
                resId = R.string.wings_facebook__destination_page_description;
            }
            String destinationDescription = mContext.getString(resId, accountName, settings.getAlbumName());

            return new LinkInfo(accountName, destinationId, destinationDescription);
        }
        return null;
    }

    @Override
    public Set<ShareNotification> processShareRequests() {
        Set<ShareNotification> notifications = new HashSet<ShareNotification>();

        // Get params associated with the linked account.
        FacebookSettings settings = fetchSettings();
        if (settings != null) {
            // Get share requests for Facebook.
            int destinationId = settings.getDestinationId();
            Destination destination = new Destination(destinationId, ENDPOINT_ID);
            List<ShareRequest> shareRequests = mDatabase.checkoutShareRequests(destination);
            int shared = 0;
            String intentUri = null;

            if (!shareRequests.isEmpty()) {
                // Try open session with cached access token.
                Session session = Session.openActiveSessionFromCache(mContext);
                if (session != null && session.isOpened()) {
                    // Process share requests.
                    for (ShareRequest shareRequest : shareRequests) {
                        File file = new File(shareRequest.getFilePath());
                        ParcelFileDescriptor fileDescriptor = null;
                        try {
                            // Construct graph params.
                            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                            Bundle params = new Bundle();
                            params.putParcelable(SHARE_KEY_PICTURE, fileDescriptor);

                            String pageAccessToken = settings.optPageAccessToken();
                            if (DestinationId.PAGE == destinationId && !TextUtils.isEmpty(pageAccessToken)) {
                                params.putString(SHARE_KEY_PAGE_ACCESS_TOKEN, pageAccessToken);
                            }

                            String photoPrivacy = settings.optPhotoPrivacy();
                            if (!TextUtils.isEmpty(photoPrivacy)) {
                                params.putString(SHARE_KEY_PHOTO_PRIVACY, photoPrivacy);
                            }

                            // Execute upload request synchronously. Need to use RequestBatch to set connection timeout.
                            Request request = new Request(session, settings.getAlbumGraphPath(), params, HttpMethod.POST, null);
                            RequestBatch requestBatch = new RequestBatch(request);
                            requestBatch.setTimeout(HTTP_REQUEST_TIMEOUT);
                            List<Response> responses = requestBatch.executeAndWait();
                            if (responses != null && !responses.isEmpty()) {
                                // Process response.
                                Response response = responses.get(0);
                                if (response != null) {
                                    FacebookRequestError error = response.getError();
                                    if (error == null) {
                                        // Mark as successfully processed.
                                        mDatabase.markSuccessful(shareRequest.getId());

                                        // Parse photo id to construct notification intent uri.
                                        if (intentUri == null) {
                                            String photoId = parsePhotoId(response.getGraphObject());
                                            if (photoId != null && photoId.length() > 0) {
                                                intentUri = SHARE_NOTIFICATION_INTENT_BASE_URI + photoId;
                                            }
                                        }

                                        shared++;
                                    } else {
                                        mDatabase.markFailed(shareRequest.getId());

                                        Category category = error.getCategory();
                                        if (Category.AUTHENTICATION_RETRY.equals(category)
                                                || Category.PERMISSION.equals(category)) {
                                            // Update account linking state to unlinked.
                                            unlink();
                                        }
                                    }
                                } else {
                                    mDatabase.markFailed(shareRequest.getId());
                                }
                            } else {
                                mDatabase.markFailed(shareRequest.getId());
                            }
                        } catch (FacebookException e) {
                            mDatabase.markFailed(shareRequest.getId());
                        } catch (IllegalArgumentException e) {
                            mDatabase.markFailed(shareRequest.getId());
                        } catch (FileNotFoundException e) {
                            mDatabase.markFailed(shareRequest.getId());
                        } catch (Exception e) {
                            // Safety.
                            mDatabase.markFailed(shareRequest.getId());
                        } finally {
                            if (fileDescriptor != null) {
                                try {
                                    fileDescriptor.close();
                                } catch (IOException e) {
                                    // Do nothing.
                                }
                            }
                        }
                    }
                } else {
                    // Mark all share requests as failed to process since we failed to open an active session.
                    for (ShareRequest shareRequest : shareRequests) {
                        mDatabase.markFailed(shareRequest.getId());
                    }
                }
            }

            // Construct and add notification representing share results.
            if (shared > 0) {
                notifications.add(new FacebookShareNotification(mContext, destination.getHash(), settings.getAlbumName(), shared, intentUri));
            }
        }

        return notifications;
    }

    @Override
    @Produce
    public LinkEvent produceLinkEvent() {
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
         * The personal Facebook profile.
         */
        public static final int PROFILE = 0;

        /**
         * A Facebook Page.
         */
        public static final int PAGE = 1;
    }

    /**
     * The link event implementation associated with this endpoint.
     */
    public class LinkEvent extends WingsEndpoint.LinkEvent {

        /**
         * Private constructor.
         *
         * @param isLinked true if current link state for this endpoint is linked; false otherwise.
         */
        private LinkEvent(boolean isLinked) {
            super(FacebookEndpoint.class, isLinked);
        }
    }
}
