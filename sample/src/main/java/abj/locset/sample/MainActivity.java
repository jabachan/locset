package abj.locset.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import abj.locset.Locset;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.main_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Locset.request(MainActivity.this, Locset.SettingPriority.HIGH_ACCURACY, REQUEST_CODE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE) {
            Log.d("LocsetSample", "locset result: " + resultCode);
            switch (resultCode) {
                case Locset.ResultCode.SUCCESS:
                    // setting ok
                    Toast.makeText(this, "locset success", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    // setting ng
                    Toast.makeText(this, "locset failure", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
}
