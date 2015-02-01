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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.dpsmarques.android.account.activity.AccountSelectionActivityHelper;
import com.dpsmarques.android.auth.GoogleOauthTokenObservable;
import com.dpsmarques.android.auth.activity.OperatorGoogleAuthenticationActivityController;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flurry.android.FlurryAgent;
import com.github.dpsm.android.print.GoogleCloudPrint;
import com.github.dpsm.android.print.jackson.JacksonPrinterSearchResultOperator;
import com.github.dpsm.android.print.jackson.model.JacksonPrinterSearchResult;
import com.github.dpsm.android.print.model.Printer;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.internal.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.internal.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import retrofit.client.Response;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * {@link android.app.Activity} to select a Google Cloud printer.
 *
 * @author David Marques
 */
public class GoogleCloudPrintSettingsActivity extends Activity implements
        AccountSelectionActivityHelper.AccountSelectionListener,
        OperatorGoogleAuthenticationActivityController.GoogleAuthenticationListener {

    private static final String GOOGLE_PRINT_SCOPE = "oauth2:https://www.googleapis.com/auth/cloudprint";

    private static final String[] ACCOUNT_TYPE = new String[]{"com.google"};

    private static final JsonPath OPTIONS_JSON_PATH;

    static {
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return new JacksonMappingProvider();
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });

        OPTIONS_JSON_PATH = JsonPath.compile("$.printers[*].capabilities.printer.media_size.option[*]");
    }

    static final String EXTRA_ACCOUNT = "account";
    static final String EXTRA_PRINTER = "printer";
    static final String EXTRA_PRINTER_NAME = "printer_name";
    static final String EXTRA_TOKEN = "token";
    static final String EXTRA_MEDIA = "media";

    private static final int REQUEST_CODE_BASE = 1000;

    private final Action1<Throwable> mShowPrinterNotFoundAction = new Action1<Throwable>() {
        @Override
        public void call(final Throwable throwable) {
            Toast.makeText(getApplicationContext(), R.string.wings_gcp__settings__error_no_printer, Toast.LENGTH_SHORT).show();
        }
    };

    private GoogleCloudPrint mGoogleCloudPrint;
    private AccountSelectionActivityHelper mAccountSelectionHelper;
    private OperatorGoogleAuthenticationActivityController mAuthenticationHelper;

    private Observable<String> mOauthObservable;
    private String mAccountSelected;
    private String mAuthenticationToken;

    private Button mSelectPrinterButton;
    private Spinner mPrinterSpinner;
    private Spinner mMediaSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gcp_activity_settings);

        mAuthenticationHelper = new OperatorGoogleAuthenticationActivityController(this, REQUEST_CODE_BASE + 100);
        mAccountSelectionHelper = new AccountSelectionActivityHelper(this, REQUEST_CODE_BASE);
        mGoogleCloudPrint = new GoogleCloudPrint();

        mSelectPrinterButton = (Button) findViewById(R.id.gcp_activity_settings_button_link);
        mPrinterSpinner = (Spinner) findViewById(R.id.gcp_activity_settings_spinner_printers);
        mPrinterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                onPrinterSelected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mMediaSpinner = (Spinner) findViewById(R.id.gcp_activity_settings_spinner_media_sizes);

        mSelectPrinterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                final Printer selectedPrinter = (Printer) mPrinterSpinner.getSelectedItem();
                final MediaSize selectedMedia = (MediaSize) mMediaSpinner.getSelectedItem();
                if (selectedPrinter != null) {
                    final String id = selectedPrinter.getId();
                    final String account = mAccountSelected;
                    final String token = mAuthenticationToken;
                    if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(account) && !TextUtils.isEmpty(token)) {
                        final Intent intent = new Intent();
                        intent.putExtra(EXTRA_PRINTER, id);
                        intent.putExtra(EXTRA_PRINTER_NAME, selectedPrinter.getName());
                        intent.putExtra(EXTRA_ACCOUNT, account);
                        intent.putExtra(EXTRA_TOKEN, token);
                        if (selectedMedia != null) {
                            intent.putExtra(EXTRA_MEDIA, selectedMedia.id);
                        }

                        setResult(Activity.RESULT_OK, intent);
                        finish();
                    }
                }
            }
        });
    }

    private void onPrinterSelected(final int position) {
        mMediaSpinner.setVisibility(View.GONE);
        final Printer printer = (Printer) mPrinterSpinner.getAdapter().getItem(position);
        mOauthObservable.subscribe(new Action1<String>() {
            @Override
            public void call(final String token) {
                getPrinterDetails(token, printer.getId()).subscribe(new Action1<Response>() {
                    @Override
                    public void call(Response response) {
                        onPrinterDetails(response);
                    }
                }, mShowPrinterNotFoundAction);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAccountSelectionHelper.selectUserAccount(ACCOUNT_TYPE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mAccountSelectionHelper.handleActivityResult(requestCode, resultCode, data)) {
            return; // Handled by helper.
        }
        if (mAuthenticationHelper.handleActivityResult(requestCode, resultCode, data)) {
            return; // Handled by helper.
        }
    }

    @Override
    public void onAccountSelected(final String accountName) {
        mAccountSelected = accountName;
        mOauthObservable = GoogleOauthTokenObservable
                .create(this, accountName, GOOGLE_PRINT_SCOPE)
                .authenticateUsing(this, REQUEST_CODE_BASE)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());

        findPrinters();
    }

    @Override
    public void onAccountSelectionCanceled() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onAuthenticationError(final Throwable throwable) {
        Toast.makeText(getApplicationContext(), R.string.wings_gcp__settings__error_authenticate, Toast.LENGTH_SHORT).show();

        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onAuthenticationSucceeded(final String token) {
        mAuthenticationToken = token;
    }

    @Override
    public void onRetryAuthentication() {
        Toast.makeText(getApplicationContext(), R.string.wings_gcp__settings__error_authenticate, Toast.LENGTH_SHORT).show();

        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    /**
     * Finds printers associated with the account.
     */
    private void findPrinters() {
        mOauthObservable.subscribe(new Action1<String>() {
            @Override
            public void call(final String token) {
                searchPrinters(token).subscribe(new Action1<JacksonPrinterSearchResult>() {
                    @Override
                    public void call(JacksonPrinterSearchResult result) {
                        onPrinterSearchResult(result);
                    }
                }, mShowPrinterNotFoundAction);
            }
        });
    }

    private Observable<JacksonPrinterSearchResult> searchPrinters(final String token) {
        return mGoogleCloudPrint.getPrinters(token)
                .lift(new JacksonPrinterSearchResultOperator())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Observable<Response> getPrinterDetails(final String token, final String printerId) {
        return mGoogleCloudPrint.getPrinter(token, printerId, true, null)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io());
    }

    private void onPrinterSearchResult(final JacksonPrinterSearchResult result) {
        final List<Printer> printers = result.getPrinters();

        final HashMap<String, String> parameters = new HashMap<>();
        parameters.put("sizes", String.valueOf(printers.size()));
        FlurryAgent.logEvent("gcp_search_printer_success", parameters);

        if (printers != null && printers.size() > 0) {
            mPrinterSpinner.setAdapter(new ArrayAdapter<Printer>(
                    GoogleCloudPrintSettingsActivity.this, R.layout.gcp_settings_spinner_item,
                    R.id.activity_main_spinner_item_text, printers
            ));
        } else {
            Toast.makeText(getApplicationContext(), R.string.wings_gcp__settings__error_no_printer, Toast.LENGTH_SHORT).show();
        }
    }

    private void onPrinterDetails(final Response response) {
        if (response.getStatus() == HttpURLConnection.HTTP_OK) {
            final List<MediaSize> sizes = parseMediaSizes(response);
            final HashMap<String, String> parameters = new HashMap<>();
            parameters.put("sizes", String.valueOf(sizes.size()));
            FlurryAgent.logEvent("gcp_details_success", parameters);

            final boolean hasMediaSize = !sizes.isEmpty();
            if (hasMediaSize) {
                mMediaSpinner.setAdapter(new ArrayAdapter<MediaSize>(
                        GoogleCloudPrintSettingsActivity.this, R.layout.gcp_settings_spinner_item,
                        R.id.activity_main_spinner_item_text, sizes));
            }
            mMediaSpinner.setVisibility(hasMediaSize ? View.VISIBLE : View.GONE);
        } else {
            final HashMap<String, String> parameters = new HashMap<>();
            parameters.put("code", String.valueOf(response.getStatus()));
            FlurryAgent.logEvent("gcp_details_failed", parameters);
        }
    }

    private List<MediaSize> parseMediaSizes(Response response) {
        final List<MediaSize> mediaSizes = new LinkedList<MediaSize>();
        try {
            mediaSizes.addAll(JsonPath.parse(response.getBody().in())
                    .read(OPTIONS_JSON_PATH, MediaSizeList.class));
        } catch (IOException e) {
            // Do nothing.
        }
        return mediaSizes;
    }

    static class MediaSizeList extends ArrayList<MediaSize> {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MediaSize {

        @JsonProperty("custom_display_name")
        String name;

        @JsonProperty("vendor_id")
        String id;

        @Override
        public String toString() {
            return name;
        }
    }
}
