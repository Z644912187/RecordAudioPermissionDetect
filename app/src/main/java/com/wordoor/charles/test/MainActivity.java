package com.wordoor.charles.test;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity implements RecordAudioPermissionDetect.onPermitRecordListener {

    private static final String TAG = "MainActivity";
    private RecordAudioPermissionDetect mRecordAudioPermissionDetect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRecordAudioPermissionDetect = new RecordAudioPermissionDetect(this);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mRecordAudioPermissionDetect.startCheckRecordPermission();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        mRecordAudioPermissionDetect.startCheckRecordPermission();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRecordAudioPermissionDetect.stopCheck();
    }

    @Override
    public void isPermit(boolean flag) {
        if (!flag) {
            mRecordAudioPermissionDetect.showMissingPermissionDialog(this);
        }
    }
}
