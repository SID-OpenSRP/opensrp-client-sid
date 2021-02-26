package org.smartregister.bidan.activity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;



import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.Context;
import org.smartregister.bidan.BuildConfig;
import org.smartregister.bidan.R;
import org.smartregister.bidan.application.BidanApplication;
import org.smartregister.bidan.utils.AllConstantsINA;
import org.smartregister.domain.LoginResponse;
import org.smartregister.domain.jsonmapping.LoginResponseData;
import org.smartregister.domain.jsonmapping.util.TeamLocation;
import org.smartregister.event.Listener;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.DrishtiSyncScheduler;
import org.smartregister.util.AssetHandler;
import org.smartregister.util.Utils;
import org.smartregister.view.BackgroundAction;
import org.smartregister.view.LockingBackgroundTask;
import org.smartregister.view.ProgressIndicator;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS;
import static org.smartregister.domain.LoginResponse.NO_INTERNET_CONNECTIVITY;
import static org.smartregister.domain.LoginResponse.SUCCESS;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_TEAM_DETAILS;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_TEAM_LOCATION;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_TEAM_LOCATION_UUID;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_TEAM_NAME;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_TEAM_UUID;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_USER_DETAILS;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_USER_LOCATION;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_USER_PREFERREDNAME;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITHOUT_USER_USERNAME;
import static org.smartregister.domain.LoginResponse.SUCCESS_WITH_EMPTY_RESPONSE;
import static org.smartregister.domain.LoginResponse.UNAUTHORIZED;
import static org.smartregister.domain.LoginResponse.UNKNOWN_RESPONSE;
import static org.smartregister.util.Log.logError;
import static org.smartregister.util.Log.logInfo;
import static org.smartregister.util.Log.logVerbose;

public class LoginActivity extends AppCompatActivity {
    public static final String ENGLISH_LOCALE = "en";
    public static final String BAHASA_LOCALE = "in";
    public static final String ENGLISH_LANGUAGE = "English";
    public static final String BAHASA_LANGUAGE = "Bahasa";
    private static final String TAG = LoginActivity.class.getName();
    private Context context = BidanApplication.getInstance().context();
    private EditText userNameEditText;
    private EditText passwordEditText;
    private ProgressDialog progressDialog;
    public static final String PREF_TEAM_LOCATIONS = "PREF_TEAM_LOCATIONS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logVerbose("Initializing ...");
        try {
            AllSharedPreferences allSharedPreferences = new AllSharedPreferences(getDefaultSharedPreferences(this));
            String preferredLocale = allSharedPreferences.fetchLanguagePreference();
            Resources res = Context.getInstance().applicationContext().getResources();
            // Change locale settings in the app.
            DisplayMetrics dm = res.getDisplayMetrics();
            android.content.res.Configuration conf = res.getConfiguration();
            conf.locale = new Locale(preferredLocale);
            res.updateConfiguration(conf, dm);
        } catch (Exception e) {
            e.printStackTrace();
        }
        setContentView(org.smartregister.R.layout.login);
        ImageView loginglogo = findViewById(R.id.login_logo);
        loginglogo.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.login_logo_bidan, null));
        ResourcesCompat.getDrawable(getResources(), R.drawable.login_logo_bidan, null);

        context = Context.getInstance().updateApplicationContext(this.getApplicationContext());
//        context = BidanApplication.getInstance().context();;
        positionViews();
        initializeLoginFields();
        initializeBuildDetails();
        setDoneActionHandlerOnPasswordField();
        initializeProgressDialog();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setIcon(ResourcesCompat.getDrawable(getResources(), R.mipmap.logo, null));
            getSupportActionBar().setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(), R.color.action_bar_background, null));
        }
        setLanguage();

//        debugApp();

    }

    private void positionViews() {
        ImageView loginglogo = findViewById(R.id.login_logo);
        loginglogo.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.login_logo_bidan, null));
        ResourcesCompat.getDrawable(getResources(), R.drawable.login_logo_bidan, null);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.add("Settings");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().toString().equalsIgnoreCase("Settings")) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!getOpenSRPContext().IsUserLoggedOut()) {
            goToHome();
        }

        if (BuildConfig.DEBUG) {
            if (getOpenSRPContext().userService().hasARegisteredUser()) {
                userNameEditText.setText(context.allSharedPreferences().fetchRegisteredANM());
            }
        }
    }

    public static Context getOpenSRPContext() {
        return BidanApplication.getInstance().context();
    }

    public static String switchLanguagePreference() {
        AllSharedPreferences allSharedPreferences = new AllSharedPreferences(getDefaultSharedPreferences(Context.getInstance().applicationContext()));
        String preferredLocale = allSharedPreferences.fetchLanguagePreference();
        Resources res = Context.getInstance().applicationContext().getResources();
        // Change locale settings in the app.
        DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();

        if (ENGLISH_LOCALE.equals(preferredLocale)) {
            allSharedPreferences.saveLanguagePreference(BAHASA_LOCALE);
            conf.locale = new Locale(BAHASA_LOCALE);
            res.updateConfiguration(conf, dm);
            return BAHASA_LANGUAGE;

        } else {
            allSharedPreferences.saveLanguagePreference(ENGLISH_LOCALE);
            conf.locale = new Locale(ENGLISH_LOCALE);
            res.updateConfiguration(conf, dm);
            return ENGLISH_LANGUAGE;
        }
    }

    protected String getVersion() throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        return packageInfo.versionName;
    }

    private String getBuildDate() {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(BuildConfig.BUILD_TIMESTAMP));
    }

    public static void setLanguage() {

        AllSharedPreferences allSharedPreferences = new AllSharedPreferences(getDefaultSharedPreferences(Context.getInstance().applicationContext()));
        String preferredLocale = allSharedPreferences.fetchLanguagePreference();

        Resources res = Context.getInstance().applicationContext().getResources();
        // Change locale settings in the app.
        DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
//        conf.locale = new Locale(BAHASA_LOCALE);
        conf.locale = new Locale(preferredLocale);
        res.updateConfiguration(conf, dm);
        Log.e(TAG, "setLanguage: " + res.getConfiguration().locale.toString());

    }

    private void initializeLoginFields() {
        userNameEditText = findViewById(org.smartregister.R.id.login_userNameText);
        userNameEditText.setRawInputType(InputType.TYPE_CLASS_TEXT);
        passwordEditText = findViewById(org.smartregister.R.id.login_passwordText);
        passwordEditText.setRawInputType(InputType.TYPE_CLASS_TEXT);
    }

    private void initializeBuildDetails() {
        TextView buildDetailsTextView = findViewById(org.smartregister.R.id.login_build);
        try {
            buildDetailsTextView.setText("Version " + getVersion() + ", Built on: " + getBuildDate());
        } catch (Exception e) {
            logError("Error fetching build details: " + e);
        }
    }

    private void setDoneActionHandlerOnPasswordField() {
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    login(findViewById(org.smartregister.R.id.login_loginButton));
                }
                return false;
            }
        });
    }

    public void login(final View view) {
        login(view, !getOpenSRPContext().allSharedPreferences().fetchForceRemoteLogin());
    }

    private void login(final View view, boolean localLogin) {
        hideKeyboard();
        view.setClickable(false);

        final String userName = userNameEditText.getText().toString();
        final String password = passwordEditText.getText().toString();



        if (!TextUtils.isEmpty(userName) && !TextUtils.isEmpty(password)) {
            if (context.allSharedPreferences().fetchRegisteredANM().equalsIgnoreCase(userNameEditText.getText().toString())) {
                if (localLogin) {
                    localLogin(view, userName, password);
                } else {
                    remoteLogin(view, userName, password);
                }
            } else {
                clearExistingData();
                remoteLogin(view, userName, password);
            }
        } else {
            showErrorDialog(getResources().getString(R.string.unauthorized));
            view.setClickable(true);
        }
    }

    private void clearExistingData() {
        BidanApplication.getInstance().getRepository().deleteRepository();
        getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE).edit().clear().apply();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String currentUrlSetting = pref.getString("DRISHTI_BASE_URL", "-");
        pref.edit().clear().apply();
        pref.edit().putString("DRISHTI_BASE_URL", currentUrlSetting).apply();
        AllSharedPreferences allSharedPreferences = new AllSharedPreferences(pref);
        URL url = null;
        try {
            url = new URL(currentUrlSetting);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        String base = url.getProtocol() + "://" + url.getHost();
        int port = url.getPort();

        logInfo("Base URL: " + base);
        logInfo("Port: " + port);
        allSharedPreferences.saveHost(base);
        allSharedPreferences.savePort(port);
    }

    private KeyStore.PrivateKeyEntry getUserKeyPair(KeyStore keyStore, final String username) throws Exception {
        if (keyStore.containsAlias(username)) {
            return (KeyStore.PrivateKeyEntry) keyStore.getEntry(username, null);
        }

        return null;
    }

    private static final String CIPHER = "RSA/ECB/PKCS1Padding";
    private static final String CIPHER_PROVIDER = "AndroidOpenSSL";
    private static final String CIPHER_TEXT_CHARACTER_CODE = "UTF-8";
    private String decryptString(KeyStore.PrivateKeyEntry privateKeyEntry, String cipherText)
            throws Exception {

        Cipher output;
        if (Build.VERSION.SDK_INT >= 23) {
            output = Cipher.getInstance(CIPHER);
            output.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());
        } else {
            output = Cipher.getInstance(CIPHER, CIPHER_PROVIDER);
            RSAPrivateKey privateKey = (RSAPrivateKey) privateKeyEntry.getPrivateKey();
            output.init(Cipher.DECRYPT_MODE, privateKey);
        }

        CipherInputStream cipherInputStream = new CipherInputStream(
                new ByteArrayInputStream(Base64.decode(cipherText, Base64.DEFAULT)), output);

        ArrayList<Byte> values = new ArrayList<>();
        int nextByte;
        while ((nextByte = cipherInputStream.read()) != -1) {
            values.add((byte) nextByte);
        }

        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i);
        }

        return new String(bytes, 0, bytes.length, CIPHER_TEXT_CHARACTER_CODE);
    }

    public String getGroupId(AllSharedPreferences allSharedPreferences, String userName, KeyStore.PrivateKeyEntry privateKeyEntry) {
        if (privateKeyEntry != null) {
            String encryptedGroupId = allSharedPreferences.fetchEncryptedGroupId(userName);
            if (encryptedGroupId != null) {
                try {
                    return decryptString(privateKeyEntry, encryptedGroupId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private boolean isValidGroupId(String groupId) {
        return Context.getInstance().initRepository().canUseThisPassword(groupId);
    }

    private void localLogin(View view, String userName, String password) {
        AllSharedPreferences allSharedPreferences = getOpenSRPContext().allSharedPreferences();
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            KeyStore.PrivateKeyEntry privateKeyEntry = getUserKeyPair(keyStore, userName);


            String encryptedPassword = allSharedPreferences.
                    fetchEncryptedPassword(userName);
            String decryptedPassword = decryptString(privateKeyEntry, encryptedPassword);


            String groupId = getGroupId(allSharedPreferences, userName, privateKeyEntry);


            boolean isValidGroupId = isValidGroupId(groupId);

        } catch (Exception e) {
            e.printStackTrace();
        }

        boolean fetchForceRemoteLogin = allSharedPreferences.fetchForceRemoteLogin();


        if (getOpenSRPContext().userService().isUserInValidGroup(userName, password)) {
            localLoginWith(userName, password);

        } else {

//            showErrorDialog(getString(org.smartregister.R.string.login_failed_dialog_message));
//            view.setClickable(true);
            remoteLogin(view, userName, password);
        }
    }

    private void remoteLogin(final View view, final String userName, final String password) {
        tryRemoteLogin(userName, password, new Listener<LoginResponse>() {
            public void onEvent(LoginResponse loginResponse) {
                if (loginResponse == SUCCESS) {
                    remoteLoginWith(userName, password, loginResponse.payload());
                    getOpenSRPContext().allSharedPreferences().saveForceRemoteLogin(false);

                } else {
                    if (loginResponse == null) {
                        showErrorDialog("Login failed. Unknown reason. Try Again");
                    } else {
                        Log.e(TAG, "onEvent: "+ loginResponse.message() );
                        if (loginResponse == NO_INTERNET_CONNECTIVITY) {
                            showErrorDialog(getResources().getString(R.string.no_internet_connectivity));
                        } else if (loginResponse == UNKNOWN_RESPONSE) {
                            showErrorDialog(getResources().getString(R.string.unknown_response));
                        } else if (loginResponse == UNAUTHORIZED) {
                            showErrorDialog(getResources().getString(R.string.unauthorized));
                        }
//                        showErrorDialog(loginResponse.message());
                    }
                    view.setClickable(true);
                }
            }
        });
    }

    private void tryRemoteLogin(final String userName, final String password, final Listener<LoginResponse> afterLoginCheck) {
        LockingBackgroundTask task = new LockingBackgroundTask(new ProgressIndicator() {
            @Override
            public void setVisible() {
                progressDialog.show();
            }

            @Override
            public void setInvisible() {
                progressDialog.dismiss();
            }
        });

        task.doActionInBackground(new BackgroundAction<LoginResponse>() {
            public LoginResponse actionToDoInBackgroundThread() {
                return context.userService().isValidRemoteLogin(userName, password);
            }

            public void postExecuteInUIThread(LoginResponse result) {
                afterLoginCheck.onEvent(result);
            }
        });
    }

    private void remoteLoginWith(String userName, String password, LoginResponseData userInfo) {
        context.userService().remoteLogin(userName, password, userInfo);

        Iterator<TeamLocation> teamLocationIterator = userInfo.team.location.iterator();
        TeamLocation teamLocation = teamLocationIterator.next();

        Log.e(TAG, "remoteLoginWith: location "+ teamLocation.uuid );

        Utils.writePreference(BidanApplication.getInstance().getApplicationContext(), PREF_TEAM_LOCATIONS, teamLocation.uuid);

        setDefaultLocationId(userName, teamLocation.uuid);
        goToHome();
        DrishtiSyncScheduler.startOnlyIfConnectedToNetwork(getApplicationContext());
    }

    private void localLoginWith(String userName, String password) {
        context.userService().localLogin(userName, password);
        goToHome();
        DrishtiSyncScheduler.startOnlyIfConnectedToNetwork(getApplicationContext());
    }

    private void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null)
            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), HIDE_NOT_ALWAYS);
    }

    private void showErrorDialog(String message) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.login_failed_dialog_title))
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // do nothing
                    }
                })
                .create();
        dialog.show();
    }

    private void goToHome() {
        startActivity(new Intent(this, BidanHomeActivity.class));
//        startActivity(new Intent(this, FPSmartRegisterActivity.class));
        finish();
    }

    public void setDefaultLocationId(String userName, String locationId) {
        if (userName != null) {
            context.userService().getAllSharedPreferences().savePreference(userName + "-locationid", locationId);
        }
    }

    private void initializeProgressDialog() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(getString(org.smartregister.R.string.loggin_in_dialog_title));
        progressDialog.setMessage(getString(org.smartregister.R.string.loggin_in_dialog_message));
    }

}
