package abj.locset.legacy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.PermissionChecker;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

public class LocsetActivity extends FragmentActivity {

    private static final int REQUEST_CODE_LOCATION_PERMISSION = 1;
    private static final int REQUEST_CODE_LOCATION_SETTING = 2;
    private static final int REQUEST_CODE_GOOGLE_PLAY_SERVICES_ERROR = 3;

    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    static final String INTENT_EXTRA_KEY_PRIORITY = "intent.extra.key.priority";

    @Locset.SettingPriority
    private int mPriority = Locset.SettingPriority.HIGH_ACCURACY;

    private boolean mIsShowGooglePlayServiceErrorDialog = false;

    @Nullable
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPriority = getIntent().getIntExtra(INTENT_EXTRA_KEY_PRIORITY, Locset.SettingPriority.HIGH_ACCURACY);

        if (isPermissionGranted()) {
            requestLocationSetting();
            return;
        }
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_LOCATION_PERMISSION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CODE_LOCATION_SETTING: {
                if (resultCode == Activity.RESULT_OK) {
                    finishForResult(Locset.ResultCode.SUCCESS);
                } else {
                    finishForResult(Locset.ResultCode.LOCATION_SETTING_FAILURE);
                }
                break;
            }
            case REQUEST_CODE_GOOGLE_PLAY_SERVICES_ERROR: {
                finishForResult(Locset.ResultCode.GOOGLE_PLAY_SERVICES_UNAVAILABLE);
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (permissions.length != grantResults.length) {
            finishForResult(Locset.ResultCode.PERMISSION_FAILURE);
            return;
        }
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                    finishForResult(Locset.ResultCode.PERMISSION_FAILURE);
                } else {
                    finishForResult(Locset.ResultCode.PERMISSION_FAILURE_DO_NOT_ASK_AGAIN);
                }
                return;
            }
        }
        requestLocationSetting();
    }

    private void finishForResult(@Locset.ResultCode int resultCode) {
        if (isFinishing()) {
            return;
        }
        setResult(resultCode);
        finish();
    }

    private boolean isPermissionGranted() {
        for (String permission : PERMISSIONS) {
            if (PermissionChecker.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestLocationSetting() {
        if (!checkGooglePlayServicesAvailable()) {
            return;
        }
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        if (connectionResult.isSuccess()) {
                            return;
                        }
                        finishForResult(Locset.ResultCode.GOOGLE_API_FAILURE);
                    }
                })
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        requestLocationSettingGoogleApi();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        finishForResult(Locset.ResultCode.GOOGLE_API_FAILURE);
                    }
                }).build();
        mGoogleApiClient.connect();

    }

    /**
     * SettingsApi was deprecated from play-services-11.0
     */
    private void requestLocationSettingGoogleApi() {
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
            finishForResult(Locset.ResultCode.GOOGLE_API_FAILURE);
            return;
        }

        final LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .setAlwaysShow(true)
                .addLocationRequest(LocationRequest.create().setPriority(mPriority))
                .build();

        final PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(
                mGoogleApiClient,
                request);

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        finishForResult(Locset.ResultCode.SUCCESS);
                        return;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(
                                    LocsetActivity.this,
                                    // An arbitrary constant to disambiguate activity results.
                                    REQUEST_CODE_LOCATION_SETTING);
                            return;
                        } catch (IntentSender.SendIntentException e) {
                            // failure
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        break;
                }
                finishForResult(Locset.ResultCode.LOCATION_SETTING_FAILURE);
            }
        });
    }

    private boolean checkGooglePlayServicesAvailable() {
        final GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        final int result = googleApi.isGooglePlayServicesAvailable(getApplicationContext());
        if (result == ConnectionResult.SUCCESS) {
            return true;
        }
        if (googleApi.isUserResolvableError(result)) {
            if (googleApi.showErrorDialogFragment(this, result, REQUEST_CODE_GOOGLE_PLAY_SERVICES_ERROR)) {
                mIsShowGooglePlayServiceErrorDialog = true;
                getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentDestroyed(FragmentManager fm, Fragment f) {
                        if (mIsShowGooglePlayServiceErrorDialog && f instanceof DialogFragment) {
                            finishForResult(Locset.ResultCode.GOOGLE_PLAY_SERVICES_UNAVAILABLE);
                        }
                    }
                }, false);
                return false;
            }
        }
        finishForResult(Locset.ResultCode.GOOGLE_PLAY_SERVICES_UNAVAILABLE);
        return false;
    }

}
