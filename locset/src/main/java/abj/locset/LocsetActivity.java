package abj.locset;

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
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

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

        final LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .setAlwaysShow(true)
                .addLocationRequest(LocationRequest.create().setPriority(mPriority))
                .build();
        final Task<LocationSettingsResponse> result = LocationServices.getSettingsClient(this)
                .checkLocationSettings(request);

        result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
            @Override
            public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                try {
                    task.getResult(ApiException.class);
                    // All location settings are satisfied. The client can initialize location
                    // requests here.
                    finishForResult(Locset.ResultCode.SUCCESS);
                } catch (ApiException exception) {
                    switch (exception.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                            try {
                                // Cast to a resolvable exception.
                                final ResolvableApiException resolvable = (ResolvableApiException) exception;
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                resolvable.startResolutionForResult(LocsetActivity.this, REQUEST_CODE_LOCATION_SETTING);
                                return;
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            } catch (ClassCastException e) {
                                // Ignore, should be an impossible error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            break;
                    }
                    finishForResult(Locset.ResultCode.LOCATION_SETTING_FAILURE);
                }
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
