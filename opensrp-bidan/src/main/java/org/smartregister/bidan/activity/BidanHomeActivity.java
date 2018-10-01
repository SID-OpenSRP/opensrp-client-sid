package org.smartregister.bidan.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.smartregister.Context;
import org.smartregister.bidan.R;
import org.smartregister.bidan.application.BidanApplication;
import org.smartregister.bidan.controller.NavigationControllerINA;
import org.smartregister.bidan.receiver.SyncStatusBroadcastReceiver;
import org.smartregister.bidan.repository.IndonesiaECRepository;
import org.smartregister.bidan.service.FormSubmissionSyncService;
import org.smartregister.bidan.sync.ECSyncUpdater;
import org.smartregister.bidan.sync.UpdateActionsTask;
import org.smartregister.bidan.utils.AllConstantsINA;
import org.smartregister.bidan.utils.Support;
import org.smartregister.bidan.utils.Tools;
import org.smartregister.cursoradapter.SmartRegisterQueryBuilder;
import org.smartregister.domain.FetchStatus;
import org.smartregister.enketo.view.fragment.DisplayFormFragment;
import org.smartregister.event.Listener;
import org.smartregister.service.PendingFormSubmissionService;
import org.smartregister.sync.SyncAfterFetchListener;
import org.smartregister.sync.SyncProgressIndicator;
import org.smartregister.view.activity.SecuredActivity;
import org.smartregister.view.contract.HomeContext;
import org.smartregister.view.controller.NativeAfterANMDetailsFetchListener;
import org.smartregister.view.controller.NativeUpdateANMDetailsTask;

import java.text.SimpleDateFormat;
import java.util.Date;

import static android.widget.Toast.LENGTH_SHORT;
import static java.lang.String.valueOf;
import static org.smartregister.event.Event.ACTION_HANDLED;
import static org.smartregister.event.Event.FORM_SUBMITTED;
import static org.smartregister.event.Event.SYNC_COMPLETED;
import static org.smartregister.event.Event.SYNC_STARTED;

//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.Map;

public class BidanHomeActivity extends SecuredActivity implements SyncStatusBroadcastReceiver.SyncStatusListener,LocationListener {
    private static final String TAG = BidanHomeActivity.class.getName();
    public static int kicount;
    //    SimpleDateFormat timer = new SimpleDateFormat("hh:mm:ss");
    private MenuItem updateMenuItem;
    private MenuItem lastSyncMenuItem;
    private MenuItem remainingFormsToSyncMenuItem;
    private PendingFormSubmissionService pendingFormSubmissionService;
    private IndonesiaECRepository indonesiaECRepository;
    private SyncStatusBroadcastReceiver syncStatusBroadcastReceiver;
    private TextView ecRegisterClientCountView;
    private TextView kartuIbuANCRegisterClientCountView;
    private TextView kartuIbuPNCRegisterClientCountView;
    private TextView anakRegisterClientCountView;
    private TextView kohortKbCountView;
    private Listener<String> onFormSubmittedListener = new Listener<String>() {
        @Override
        public void onEvent(String instanceId) {
            updateRegisterCounts();
        }
    };
    private Listener<String> updateANMDetailsListener = new Listener<String>() {
        @Override
        public void onEvent(String data) {
            updateRegisterCounts();
        }
    };
    private View.OnClickListener onRegisterStartListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_kartu_ibu_register:
                    navigationController.startECSmartRegistry();
                    break;

                case R.id.btn_kohort_kb_register:
                    navigationController.startFPSmartRegistry();
                    break;

                case R.id.btn_kartu_ibu_anc_register:
                    navigationController.startANCSmartRegistry();
                    break;

                case R.id.btn_anak_register:
                    navigationController.startChildSmartRegistry();
                    break;

                case R.id.btn_kartu_ibu_pnc_register:
                    navigationController.startPNCSmartRegistry();
                    break;

                default:
                    break;
            }
//            FlurryAgent.logEvent("home_dashboard",Home, true);
        }
    };
    private View.OnClickListener onButtonsClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_reporting:
                    navigationController.startReports();
                    break;
                default:
                    break;

//                case R.id.btn_videos:
//                    navigationController.startVideos();
//                    break;
            }
        }
    };

    private void flagActivator() {
        new Thread() {
            public void run() {
                try {
                    while (AllConstantsINA.TimeConstants.SLEEP_TIME > 0) {
                        sleep(1000);
                        if (AllConstantsINA.TimeConstants.IDLE)
                            AllConstantsINA.TimeConstants.SLEEP_TIME -= 1000;
                    }
                    Support.ONSYNC = false;
                } catch (InterruptedException ie) {
                    Log.e(TAG, "run: " + ie.getCause());
                }
            }
        }.start();
    }
    LocationManager locationManager;
    String provider;

    @Override
    protected void onCreation() {
        //home dashboard
        /*FlurryFacade.logEvent("home_dashboard");*/
//        String HomeStart = timer.format(new Date());
//        Map<String, String> Home = new HashMap<>();
//        Home.put("start", HomeStart);
//        FlurryAgent.logEvent("home_dashboard", Home, true);
        locationManager = (LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);

        provider = locationManager.getBestProvider(new Criteria(), false);
        if ( provider == null ) {
            provider = LocationManager.GPS_PROVIDER;
        }
        setContentView(R.layout.smart_registers_home_bidan);
        navigationController = new NavigationControllerINA(this, anmController, context());
        setupViews();
        initialize();
        DisplayFormFragment.formInputErrorMessage = getResources().getString(R.string.forminputerror);
        DisplayFormFragment.okMessage = getResources().getString(R.string.okforminputerror);
        //  context.formSubmissionRouter().getHandlerMap().put("census_enrollment_form", new ANChandler());
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            //your codes here

        }
        checkLocationPermission();
    }

    private void setupViews() {
        findViewById(R.id.btn_kartu_ibu_register).setOnClickListener(onRegisterStartListener);
        findViewById(R.id.btn_kartu_ibu_anc_register).setOnClickListener(onRegisterStartListener);
        findViewById(R.id.btn_kartu_ibu_pnc_register).setOnClickListener(onRegisterStartListener);
        findViewById(R.id.btn_anak_register).setOnClickListener(onRegisterStartListener);
        findViewById(R.id.btn_kohort_kb_register).setOnClickListener(onRegisterStartListener);

        findViewById(R.id.btn_reporting).setOnClickListener(onButtonsClickListener);
//        findViewById(R.id.btn_videos).setOnClickListener(onButtonsClickListener);

        ecRegisterClientCountView = (TextView) findViewById(R.id.txt_kartu_ibu_register_client_count);
        kartuIbuANCRegisterClientCountView = (TextView) findViewById(R.id.txt_kartu_ibu_anc_register_client_count);
        kartuIbuPNCRegisterClientCountView = (TextView) findViewById(R.id.txt_kartu_ibu_pnc_register_client_count);
        anakRegisterClientCountView = (TextView) findViewById(R.id.txt_anak_client_count);
        kohortKbCountView = (TextView) findViewById(R.id.txt_kohort_kb_register_count);
    }

    private void initialize() {
        pendingFormSubmissionService = context().pendingFormSubmissionService();
        indonesiaECRepository = BidanApplication.getInstance().indonesiaECRepository();

        FORM_SUBMITTED.addListener(onFormSubmittedListener);
        ACTION_HANDLED.addListener(updateANMDetailsListener);

        registerMyReceiver();

        //noinspection ConstantConditions
        getSupportActionBar().setTitle("");
//        getSupportActionBar().setIcon(getResources().getDrawable(org.smartregister.bidan.R.mipmap.logo));
        getSupportActionBar().setIcon(ResourcesCompat.getDrawable(getResources(), R.mipmap.logo, null));
        getSupportActionBar().setLogo(org.smartregister.bidan.R.mipmap.logo);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

//        LoginActivity.setLanguage();
//        getActionBar().setBackgroundDrawable(getResources().getDrawable(R.color.action_bar_background));
    }

    private void registerMyReceiver() {

        try
        {
            syncStatusBroadcastReceiver = new SyncStatusBroadcastReceiver(this);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(SyncStatusBroadcastReceiver.ACTION_SYNC_STATUS);
            registerReceiver(syncStatusBroadcastReceiver, intentFilter);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

    }

    @Override
    protected void onResumption() {
//        LoginActivity.setLanguage();

        updateRegisterCounts();
        updateSyncIndicator();
        updateLastSyncTime();
        updateRemainingFormsToSyncCount();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            locationManager.requestLocationUpdates(provider, 400, 1, this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            locationManager.removeUpdates(this);
        }
    }

    private void updateRegisterCounts() {
        NativeUpdateANMDetailsTask task = new NativeUpdateANMDetailsTask(Context.getInstance().anmController());
        task.fetch(new NativeAfterANMDetailsFetchListener() {
            @Override
            public void afterFetch(HomeContext anmDetails) {
                Log.d(TAG, "afterFetch: " + anmDetails);
                SmartRegisterQueryBuilder sqb = new SmartRegisterQueryBuilder();
                Cursor kiCountCursor = context().commonrepository("ec_kartu_ibu").rawCustomQueryForAdapter(sqb.queryForCountOnRegisters("ec_kartu_ibu_search", "ec_kartu_ibu_search.is_closed=0 AND namalengkap != '' AND namalengkap IS NOT NULL"));
                kiCountCursor.moveToFirst();
                kicount = kiCountCursor.getInt(0);
                kiCountCursor.close();

                Cursor kbCountCursor = context().commonrepository("ec_kartu_ibu").rawCustomQueryForAdapter(sqb.queryForCountOnRegisters("ec_kartu_ibu_search", "ec_kartu_ibu_search.is_closed=0 AND jenisKontrasepsi !='0' AND namalengkap != '' AND namalengkap IS NOT NULL"));
                kbCountCursor.moveToFirst();
                int kbcount = kbCountCursor.getInt(0);
                kbCountCursor.close();

                Cursor anccountcursor = context().commonrepository("ec_ibu").rawCustomQueryForAdapter(sqb.queryForCountOnRegisters("ec_ibu_search", "ec_ibu_search.is_closed=0 AND namalengkap !='' AND namalengkap IS NOT NULL"));
                anccountcursor.moveToFirst();
                int anccount = anccountcursor.getInt(0);
                anccountcursor.close();

                Cursor pnccountcursor = context().commonrepository("ec_pnc").rawCustomQueryForAdapter(sqb.queryForCountOnRegisters("ec_pnc_search", "ec_pnc_search.is_closed=0 AND (ec_pnc_search.keadaanIbu ='hidup' OR ec_pnc_search.keadaanIbu IS NULL) AND namalengkap !='' AND namalengkap IS NOT NULL")); // and ec_pnc_search.keadaanIbu LIKE '%hidup%'
                pnccountcursor.moveToFirst();
                int pnccount = pnccountcursor.getInt(0);
                pnccountcursor.close();

                Cursor childcountcursor = context().commonrepository("anak").rawCustomQueryForAdapter(sqb.queryForCountOnRegisters("ec_anak_search", "ec_anak_search.is_closed=0"));
                childcountcursor.moveToFirst();
                int childcount = childcountcursor.getInt(0);
                childcountcursor.close();

                ecRegisterClientCountView.setText(valueOf(kicount));
                kartuIbuANCRegisterClientCountView.setText(valueOf(anccount));
                kartuIbuPNCRegisterClientCountView.setText(valueOf(pnccount));
                anakRegisterClientCountView.setText(valueOf(childcount));
                kohortKbCountView.setText(valueOf(kbcount));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        attachLogoutMenuItem(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateMenuItem = menu.findItem(R.id.updateMenuItem);
        lastSyncMenuItem = menu.findItem(R.id.lastSyncDate);
        remainingFormsToSyncMenuItem = menu.findItem(R.id.remainingFormsToSyncMenuItem);

        updateSyncIndicator();
        updateLastSyncTime();
        updateRemainingFormsToSyncCount();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.updateMenuItem:
                updateDataFromServer();
                return true;
            case R.id.switchLanguageMenuItem:
                String newLanguagePreference = LoginActivity.switchLanguagePreference();
                LoginActivity.setLanguage();
                Toast.makeText(this, "Language preference set to " + newLanguagePreference + ". Please restart the application.", LENGTH_SHORT).show();
                this.recreate();
                return true;
            case R.id.help:
                String anmID;
                try {
                    anmID = new JSONObject(context().anmController().get()).get("anmName").toString();
                } catch (org.json.JSONException e) {
                    anmID = "undefined";
                }
                Toast.makeText(this, String.format("%s current user = %s", context().getStringResource(R.string.app_name), anmID), LENGTH_SHORT).show();

                Tools.getDbRecord(context());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*public void updateDataFromServer() {
        FlurryFacade.logEvent("clicked_update_from_server");
        UpdateActionsTask updateActionsTask = new UpdateActionsTask(
                this, context().actionService(), context().formSubmissionSyncService(),
                new SyncProgressIndicator(), context().allFormVersionSyncService());
        updateActionsTask.updateDataFromServer(new SyncAfterFetchListener());
        String locationJSON = context().anmLocationController().get();
        LocationTree locationTree = EntityUtils.fromJson(locationJSON, LocationTree.class);

//        Map<String,TreeNode<String, Location>> locationMap =
//                locationTree.getLocationsHierarchy();

//        if(LoginActivity.generator.uniqueIdController().needToRefillUniqueId(LoginActivity.generator.UNIQUE_ID_LIMIT))  // unique id part
//            LoginActivity.generator.requestUniqueId();                                                                  // unique id part
    }*/
    public void updateDataFromServer() {
        Log.e("Home", "updateDataFromServer: tombol update");
        UpdateActionsTask updateActionsTask = new UpdateActionsTask(this);
//        FlurryFacade.logEvent("click_update_from_server");
        updateActionsTask.updateFromServer();

//        if (LoginActivity.generator.uniqueIdController().needToRefillUniqueId(LoginActivity.generator.UNIQUE_ID_LIMIT))  // unique id part
//            LoginActivity.generator.requestUniqueId();                                                                  // unique id part

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        FORM_SUBMITTED.removeListener(onFormSubmittedListener);
        ACTION_HANDLED.removeListener(updateANMDetailsListener);
    }

    private void updateSyncIndicator() {
        if (updateMenuItem != null) {
            if (context().allSharedPreferences().fetchIsSyncInProgress()) {
                updateMenuItem.setActionView(R.layout.progress);
            } else
                updateMenuItem.setActionView(null);
        }
    }

    private void updateRemainingFormsToSyncCount() {
        if (remainingFormsToSyncMenuItem == null) {
            return;
        }

        long size = BidanApplication.getInstance().getECRepository().getUnSyncedEventsSize();
        if (size > 0) {
            remainingFormsToSyncMenuItem.setTitle(valueOf(size) + " " + getString(R.string.unsynced_forms_count_message));
            remainingFormsToSyncMenuItem.setVisible(true);
        } else {
            remainingFormsToSyncMenuItem.setVisible(false);
        }
    }

    private void updateLastSyncTime(){
        if (lastSyncMenuItem == null) {
            return;
        }

        long longLastSync = ECSyncUpdater.getInstance(getApplicationContext()).getLastCheckTimeStamp();
        String lastSyncDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(longLastSync));
        if (longLastSync==0) {
            lastSyncMenuItem.setTitle(getString(R.string.not_synced));
            lastSyncMenuItem.setVisible(true);
        } else {
            lastSyncMenuItem.setTitle(getString(R.string.sync_last_date)+" "+lastSyncDate);
            lastSyncMenuItem.setVisible(true);
        }
    }

    @Override
    public void onSyncStart() {
        Support.ONSYNC = true;
        AllConstantsINA.TimeConstants.IDLE = false;
        AllConstantsINA.TimeConstants.SLEEP_TIME = 5000;
        if (updateMenuItem != null) {
            updateMenuItem.setActionView(R.layout.progress);
        }
    }

    @Override
    public void onSyncInProgress(FetchStatus fetchStatus) {

    }

    @Override
    public void onSyncComplete(FetchStatus fetchStatus) {
        Toast.makeText(getApplicationContext(), fetchStatus.displayValue(), Toast.LENGTH_SHORT).show();
        updateLastSyncTime();
        updateRemainingFormsToSyncCount();
        if (updateMenuItem != null) {
            updateMenuItem.setActionView(null);
        }
        updateRegisterCounts();

        flagActivator();
    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Location Permission")
                        .setMessage("This app need to access location")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(BidanHomeActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        //Request location updates:
                        locationManager.requestLocationUpdates(provider, 400, 1, this);
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return;
            }

        }
    }

    @Override
    public void onLocationChanged(Location location) {
//        Double lat = location.getLatitude();
//        Double lng = location.getLongitude();
//
//        Log.i("Location info: Lat", lat.toString());
//        Log.i("Location info: Lng", lng.toString());
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}