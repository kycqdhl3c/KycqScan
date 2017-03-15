package com.kycq.scan;

import android.Manifest;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.kycq.library.scan.ScanView;
import com.kycq.scan.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {
	private ActivitySplashBinding dataBinding;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.dataBinding = DataBindingUtil.setContentView(this, R.layout.activity_splash);
		this.dataBinding.scanView.setOnScanListener(new ScanView.OnScanListener() {
			@Override
			public void scanResult(String result) {
				Toast.makeText(SplashActivity.this, result, Toast.LENGTH_SHORT).show();
			}
		});
		
		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		this.dataBinding.scanView.startScan();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		this.dataBinding.scanView.stopScan();
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			this.dataBinding.scanView.startScan();
			return;
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
