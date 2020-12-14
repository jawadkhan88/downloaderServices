package com.example.downloaderservices;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private Button b_dwl;
    private EditText et_url;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        et_url = findViewById(R.id.et_url);
        b_dwl = findViewById(R.id.b_dwl);

        //download button click listener
        b_dwl.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                //checking permissions and requesting if required
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    startDownload();
                }
            }
        });
    }

    public void startDownload() {
        URL url_dwl = null;

        Intent intent = new Intent(this, DownloaderService.class);
        intent.putExtra("URL", et_url.getText().toString());

        startService(intent);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    startDownload();
                } else {
                    // permission denied, boo!
                    Toast.makeText(this, "Please grant permissions", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }

    }
}
