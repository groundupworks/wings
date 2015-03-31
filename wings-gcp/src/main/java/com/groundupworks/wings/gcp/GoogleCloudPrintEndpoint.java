/*
 * Copyright (C) 2014 David Marques
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
package com.groundupworks.wings.gcp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.widget.Toast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.groundupworks.android.print.GoogleCloudPrint;
import com.groundupworks.wings.WingsEndpoint;
import com.groundupworks.wings.core.Destination;
import com.groundupworks.wings.core.ShareRequest;
import com.jayway.jsonpath.JsonPath;
import com.squareup.otto.Produce;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import retrofit.client.Response;
import retrofit.mime.TypedFile;

import static com.groundupworks.wings.gcp.GoogleCloudPrintSettingsActivity.EXTRA_ACCOUNT;
import static com.groundupworks.wings.gcp.GoogleCloudPrintSettingsActivity.EXTRA_COPIES;
import static com.groundupworks.wings.gcp.GoogleCloudPrintSettingsActivity.EXTRA_MEDIA;
import static com.groundupworks.wings.gcp.GoogleCloudPrintSettingsActivity.EXTRA_PRINTER;
import static com.groundupworks.wings.gcp.GoogleCloudPrintSettingsActivity.EXTRA_PRINTER_NAME;
import static com.groundupworks.wings.gcp.GoogleCloudPrintSettingsActivity.EXTRA_TOKEN;

/**
 * The Wings endpoint for Google Cloud Print.
 *
 * @author David Marques
 */
public class GoogleCloudPrintEndpoint extends WingsEndpoint {

    private static final String TICKET_WITH_MEDIA = "{\n" +
            "  \"version\": \"1.0\",\n" +
            "  \"print\": {\n" +
            "    \"vendor_ticket_item\": [],\n" +
            "    \"color\": {\n" +
            "      \"type\": \"STANDARD_COLOR\"\n" +
            "    },\n" +
            "    \"copies\": {\"copies\": %d}," +
            "    \"media_size\": {\n" +
            "      \"width_microns\": 1,\n" +
            "      \"height_microns\": 1,\n" +
            "      \"is_continuous_feed\": false,\n" +
            "      \"vendor_id\" : \"%s\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

    private static final String TICKET = "{\n" +
            "  \"version\": \"1.0\",\n" +
            "  \"print\": {\n" +
            "    \"vendor_ticket_item\": [],\n" +
            "    \"copies\": {\"copies\": %d}," +
            "    \"color\": {\n" +
            "      \"type\": \"STANDARD_COLOR\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

    /**
     * Google Cloud Print endpoint id.
     */
    private static final int ENDPOINT_ID = 2;

    private static final int REQUEST_CODE = ENDPOINT_ID;

    private static final String MIME_TYPE = "image/jpeg";

    private final GoogleCloudPrint mGoogleCloudPrint = new GoogleCloudPrint();

    @Override
    public int getEndpointId() {
        return ENDPOINT_ID;
    }

    @Override
    public void startLinkRequest(final Activity activity, final Fragment fragment) {
        if (fragment != null) {
            fragment.startActivityForResult(new Intent(activity, GoogleCloudPrintSettingsActivity.class), REQUEST_CODE);
        } else {
            activity.startActivityForResult(new Intent(activity, GoogleCloudPrintSettingsActivity.class), REQUEST_CODE);
        }
    }

    @Override
    public void unlink() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.putBoolean(mContext.getString(R.string.wings_gcp__link_key), false);
        editor.remove(mContext.getString(R.string.wings_gcp__account_name_key));
        editor.remove(mContext.getString(R.string.wings_gcp__printer_identifier_key));
        editor.remove(mContext.getString(R.string.wings_gcp__media));
        editor.remove(mContext.getString(R.string.wings_gcp__token));
        editor.remove(mContext.getString(R.string.wings_gcp__copies));
        editor.apply();

        // Emit link state change event.
        notifyLinkStateChanged(new LinkEvent(false));

        // Remove existing share requests in a background thread.
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                mDatabase.deleteShareRequests(new Destination(DestinationId.PRINT_QUEUE, ENDPOINT_ID));
            }
        });
    }

    @Override
    public boolean isLinked() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.wings_gcp__link_key), false);
    }

    @Override
    public void onResumeImpl() {
        // Do nothing.
    }

    @Override
    public void onActivityResultImpl(final Activity activity, final Fragment fragment,
                                     final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String accountName = data.getStringExtra(EXTRA_ACCOUNT);
                String printerIdentifier = data.getStringExtra(EXTRA_PRINTER);
                String printerName = data.getStringExtra(EXTRA_PRINTER_NAME);
                String media = data.getStringExtra(EXTRA_MEDIA);
                String token = data.getStringExtra(EXTRA_TOKEN);
                String copies = data.getStringExtra(EXTRA_COPIES);

                if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(printerIdentifier) && !TextUtils.isEmpty(token)) {
                    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
                    editor.putBoolean(mContext.getString(R.string.wings_gcp__link_key), true);
                    editor.putString(mContext.getString(R.string.wings_gcp__account_name_key), accountName);
                    editor.putString(mContext.getString(R.string.wings_gcp__printer_identifier_key), printerIdentifier);
                    editor.putString(mContext.getString(R.string.wings_gcp__printer_name_key), printerName);
                    editor.putString(mContext.getString(R.string.wings_gcp__media), media);
                    editor.putString(mContext.getString(R.string.wings_gcp__token), token);
                    editor.putString(mContext.getString(R.string.wings_gcp__copies), copies);
                    editor.apply();

                    // Emit link state change event.
                    notifyLinkStateChanged(new LinkEvent(true));
                } else {
                    Toast.makeText(mContext, mContext.getString(R.string.wings_gcp__error_link), Toast.LENGTH_SHORT).show();
                    unlink();
                }
            } else {
                Toast.makeText(mContext, mContext.getString(R.string.wings_gcp__error_link), Toast.LENGTH_SHORT).show();
                unlink();
            }
        }
    }

    @Override
    public LinkInfo getLinkInfo() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String accountName = preferences.getString(mContext.getString(R.string.wings_gcp__account_name_key), null);
        String printerIdentifier = preferences.getString(mContext.getString(R.string.wings_gcp__printer_name_key), null);
        if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(printerIdentifier)) {
            String destinationDescription = mContext.getString(R.string.wings_gcp__destination_description, accountName, printerIdentifier);
            return new LinkInfo(accountName, DestinationId.PRINT_QUEUE, destinationDescription);
        }
        return null;
    }

    @Override
    public Set<ShareNotification> processShareRequests() {
        final Set<ShareNotification> notifications = new HashSet<ShareNotification>();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        int shareCount = 0;

        final boolean isLinked = preferences.getBoolean(mContext.getString(R.string.wings_gcp__link_key), false);
        final String accountName = preferences.getString(mContext.getString(R.string.wings_gcp__account_name_key), null);
        final String printerIdentifier = preferences.getString(mContext.getString(R.string.wings_gcp__printer_identifier_key), null);
        final String media = preferences.getString(mContext.getString(R.string.wings_gcp__media), null);
        final String token = preferences.getString(mContext.getString(R.string.wings_gcp__token), null);
        final String copies = preferences.getString(mContext.getString(R.string.wings_gcp__copies), null);
        if (isLinked && !TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(printerIdentifier) &&
                !TextUtils.isEmpty(token)) {
            final String ticket = !TextUtils.isEmpty(media) ? String.format(TICKET_WITH_MEDIA, copies, media) : String.format(TICKET, copies);
            final Destination destination = new Destination(DestinationId.PRINT_QUEUE, ENDPOINT_ID);
            List<ShareRequest> shareRequests = mDatabase.checkoutShareRequests(destination);
            for (ShareRequest shareRequest : shareRequests) {
                File file = new File(shareRequest.getFilePath());
                if (file.exists()) {
                    try {
                        Response response = mGoogleCloudPrint.submitPrintJob(token, printerIdentifier,
                                file.getName(), ticket, new TypedFile(MIME_TYPE, file), MIME_TYPE).toBlocking().first();
                        final HashMap<String, String> parameters = new HashMap<>();
                        if (response.getStatus() == HttpURLConnection.HTTP_OK) {
                            try {
                                final GcpResponse gcpResponse = JsonPath.parse(response.getBody().in())
                                        .read("$", GcpResponse.class);
                                parameters.put("message", gcpResponse.message);
                                if (gcpResponse.hasSucceeded) {
                                    mDatabase.markSuccessful(shareRequest.getId());
                                    shareCount++;
                                    sLogger.log("gcp_queue_success", parameters);
                                } else {
                                    mDatabase.markFailed(shareRequest.getId());
                                    sLogger.log("gcp_queue_failed", parameters);
                                }
                            } catch (IOException e) {
                                mDatabase.markFailed(shareRequest.getId());
                                parameters.put("error", e.getMessage());
                                sLogger.log("gcp_queue_failed", parameters);
                            }
                        } else {
                            mDatabase.markFailed(shareRequest.getId());
                            parameters.put("code", String.valueOf(response.getStatus()));
                            sLogger.log("gcp_queue_failed", parameters);
                        }
                    } catch (NoSuchElementException e) {
                        mDatabase.markFailed(shareRequest.getId());
                    }
                } else {
                    mDatabase.markFailed(shareRequest.getId());
                }
            }

            final HashMap<String, String> parameters = new HashMap<>();
            parameters.put("count", String.valueOf(shareCount));
            sLogger.log("gcp_shared", parameters);

            // Create and add notification.
            if (shareCount > 0) {
                final int count = shareCount;
                ShareNotification notification = new ShareNotification() {

                    @Override
                    public int getId() {
                        return destination.getHash();
                    }

                    @Override
                    public String getTitle() {
                        return mContext.getString(R.string.wings_gcp__notification_shared_title);
                    }

                    @Override
                    public String getMessage() {
                        String msg;
                        if (count == 1) {
                            msg = mContext.getString(R.string.wings_gcp__notification_shared_msg_single, printerIdentifier);
                        } else {
                            msg = mContext.getString(R.string.wings_gcp__notification_shared_msg_multi, count, printerIdentifier);
                        }
                        return msg;
                    }

                    @Override
                    public String getTicker() {
                        return mContext.getString(R.string.wings_gcp__notification_shared_ticker);
                    }

                    @Override
                    public Intent getIntent() {
                        return new Intent();
                    }
                };

                notifications.add(notification);
            }
        }

        return notifications;
    }

    @Override
    @Produce
    public GoogleCloudPrintEndpoint.LinkEvent produceLinkEvent() {
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
         * The GCP print queue.
         */
        public static final int PRINT_QUEUE = 0;
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
            super(GoogleCloudPrintEndpoint.class, isLinked);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GcpResponse {

        @JsonProperty("success")
        boolean hasSucceeded;

        @JsonProperty("message")
        String message;

    }
}
