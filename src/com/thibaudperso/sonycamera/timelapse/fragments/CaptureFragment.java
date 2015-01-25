package com.thibaudperso.sonycamera.timelapse.fragments;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.thibaudperso.sonycamera.R;
import com.thibaudperso.sonycamera.sdk.CameraIO;
import com.thibaudperso.sonycamera.sdk.TakePictureListener;
import com.thibaudperso.sonycamera.timelapse.MyCountDownTicks;
import com.thibaudperso.sonycamera.timelapse.StepFragment;
import com.thibaudperso.sonycamera.timelapse.TimelapseApplication;
import com.thibaudperso.sonycamera.timelapse.Utils;

public class CaptureFragment extends StepFragment {

	private final static String TIME_FORMAT = "HH:mm";
		
	private CaptureFragmentListener mListener;

	private CameraIO mCameraIO;

	private View rootView;
	
	private View normalModeLine1View;
	private View normalModeLine2View;
	private View normalModeLine3View;
	private View unlimitedModeLine1View;
	private ImageView lastFramePreviewImageView;

	private TextView batteryValue;
	private TextView framesCountDownValue;
	private TextView framesCountValue;
	private ProgressBar progressBar;
	private TextView progressValue;
	private ProgressBar nextProgressBar;
	private TextView nextProgressValue;
	
	private MyCountDownTicks mCountDownPictures;
	private MyCountDownTicks mInitialCountDown;
	private CountDownTimer mNextCountDown;
	
	private boolean showLastFramePreview;
	private int timeLapseDuration;
	private int initialDelay;
	private int intervalTime;
	private int framesCount;
	private boolean isUnlimitedMode;


	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		mCameraIO = ((TimelapseApplication) getActivity().getApplication()).getCameraIO();

		rootView = inflater.inflate(R.layout.fragment_capture, container, false);

		normalModeLine1View = (View) rootView.findViewById(R.id.normalModeLine1);
		normalModeLine2View = (View) rootView.findViewById(R.id.normalModeLine2);
		normalModeLine3View = (View) rootView.findViewById(R.id.normalModeLine3);
		unlimitedModeLine1View = (View) rootView.findViewById(R.id.unlimitedModeLine1);
		lastFramePreviewImageView = (ImageView) rootView.findViewById(R.id.lastFramePreview);
		nextProgressBar = (ProgressBar) rootView.findViewById(R.id.nextPictureProgressBar);
		nextProgressValue = (TextView) rootView.findViewById(R.id.nextValueTextView);
		
		return rootView;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		setStepCompleted(true);
	}
	
	@Override
	public void onEnterFragment() {
		super.onEnterFragment();
		
		getActivity().registerReceiver(this.myBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

		lastFramePreviewImageView.setImageBitmap(null);

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

		initialDelay = preferences.getInt(TimelapseSettingsFragment.PREFERENCES_INITIAL_DELAY, TimelapseSettingsFragment.DEFAULT_INITIAL_DELAY);
		intervalTime = preferences.getInt(TimelapseSettingsFragment.PREFERENCES_INTERVAL_TIME, TimelapseSettingsFragment.DEFAULT_INTERVAL_TIME);
		framesCount = preferences.getInt(TimelapseSettingsFragment.PREFERENCES_FRAMES_COUNT, TimelapseSettingsFragment.DEFAULT_FRAMES_COUNT);
		showLastFramePreview = preferences.getBoolean(TimelapseSettingsFragment.PREFERENCES_LAST_IMAGE_REVIEW, TimelapseSettingsFragment.DEFAULT_LAST_IMAGE_REVIEW);
		
		timeLapseDuration = intervalTime * (framesCount - 1);
		isUnlimitedMode = framesCount == -1;

		/*
		 * Set activity fields
		 */
		Calendar beginEndCalendar = Calendar.getInstance();
		beginEndCalendar.add(Calendar.SECOND, initialDelay);
		String beginTime = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(beginEndCalendar.getTime());

		if(!isUnlimitedMode) {
			switchUIToNormalMode();

			((TextView) rootView.findViewById(R.id.beginValue)).setText(beginTime);

			((TextView) rootView.findViewById(R.id.durationValue)).setText(String.valueOf(timeLapseDuration)+"s");

			beginEndCalendar.add(Calendar.SECOND, timeLapseDuration);
			String endTime = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(beginEndCalendar.getTime());
			((TextView) rootView.findViewById(R.id.endValue)).setText(endTime);

			progressBar = (ProgressBar) rootView.findViewById(R.id.progressBar);
			progressBar.setProgress(0);
			progressBar.setMax(framesCount);

			progressValue = (TextView) rootView.findViewById(R.id.progressValue);
			progressValue.setText(getString(R.string.capture_progress_default));

			framesCountDownValue = (TextView) rootView.findViewById(R.id.framesCountDownValue);
			framesCountDownValue.setText(String.valueOf(framesCount));

			batteryValue = ((TextView) rootView.findViewById(R.id.batteryValue));

		} else {
			switchUIToUnlimitedMode();

			((TextView) rootView.findViewById(R.id.beginUnlimitedModeValue)).setText(beginTime);

			framesCountValue = ((TextView) rootView.findViewById(R.id.framesCountUnlimitedModeValue));
			batteryValue = ((TextView) rootView.findViewById(R.id.batteryUnlimitedModeValue));
		}

		final Integer updateEveryMillisec = 100;
		nextProgressBar.setMax(intervalTime*1000/updateEveryMillisec);
		nextProgressValue.setText(getString(R.string.capture_next_picture_default));
		mNextCountDown = new CountDownTimer(intervalTime*1000, updateEveryMillisec) {
			
			@Override
			public void onTick(long millisUntilFinished) {
				int progressUnits = (int) ((intervalTime*1000-millisUntilFinished)/updateEveryMillisec);
				nextProgressBar.setProgress(progressUnits);
				nextProgressValue.setText(new DecimalFormat("#").format(Math.ceil(millisUntilFinished/1000.0)) + "s" );
			}
			
			@Override
			public void onFinish() {
				//set all values to max
				onTick(0);
			}
		};

		final TextView timelapseCountdownBeforeStart = (TextView) rootView.findViewById(R.id.timelapseCountdownBeforeStartText);


		/*
		 * Show start in message 
		 */
		if(initialDelay != 0) {
			timelapseCountdownBeforeStart.setVisibility(View.VISIBLE);
			Animation hideAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.message_from_bottom_show);
			timelapseCountdownBeforeStart.setAnimation(hideAnimation);
		}

		mInitialCountDown = new MyCountDownTicks(initialDelay, 1000) {

			public void onTick(int remainingTicks) {
				timelapseCountdownBeforeStart.setText(String.format(getString(R.string.capture_countdown_before_start_message), 
						remainingTicks));
			}

			public void onFinish() {

				/*
				 * Remove start in message
				 */
				if(initialDelay != 0) {
					timelapseCountdownBeforeStart.setVisibility(View.GONE);
					Animation hideAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.message_from_bottom_hide);
					timelapseCountdownBeforeStart.setAnimation(hideAnimation);
				}

				// Start timelapse
				startTimeLapse();
			}

		}.start();
		
	}
	
	@Override
	public void onExitFragment() {
		super.onExitFragment();
		
		if(mInitialCountDown != null) {
			mInitialCountDown.cancel();
		}
		
		if(mCountDownPictures != null) {
			mCountDownPictures.cancel();
		}
		
		if(mNextCountDown != null){
			mNextCountDown.cancel();
		}

		getActivity().unregisterReceiver(myBatteryReceiver);
	}
	
	
	private void startTimeLapse() {

		mCountDownPictures = new MyCountDownTicks(framesCount, intervalTime*1000, true) {

			public void onTick(int remainingFrames) {
				//reset progress bar
				nextProgressBar.setProgress(0);

				if(!isUnlimitedMode) {
					/*
					 * Update activity fields for normal mode
					 */
					framesCountDownValue.setText(String.valueOf(remainingFrames));

					int progress = framesCount - remainingFrames;
					float progressPercent = (float) progress / framesCount * 100;
					progressBar.setProgress(progress);
					progressValue.setText(new DecimalFormat("#.##").format(progressPercent) + "%" );
				
				} else {
					/*
					 * Update activity fields for unlimited mode
					 */
					framesCountValue.setText(String.valueOf(remainingFrames));	
				}
				
				takePicture();
				
				//start progress bar for next picture
				mNextCountDown.start();
			}

			public void onFinish() {
				
				if(mListener != null) {
					mListener.onCaptureFinished();
				}

			}
		}.start();

	}
	
	private void takePicture() {
		/*
		 * Take a picture and notify the counter when it is done
		 * this is necessary in order to avoid a further takePicture() while the camera
		 * is still working on the last one
		 */
		mCameraIO.takePicture(new TakePictureListener() {

			@Override
			public void onResult(String url) {
				
				if(showLastFramePreview) {
					Bitmap preview = Utils.downloadBitmap(url);				
					setPreviewImage(preview);	
				}	
				
				//a picture was taken, notify the countdown class
				mCountDownPictures.tickProcessed();			
			}

			@Override
			public void onError(CameraIO.ResponseCode responseCode, String responseMsg) {
				// Had an error, let's see which
				
				Log.i("timelapseapp","error in takepicture "+responseCode+", "+responseMsg); //TMP
				switch(responseCode){
					case LONG_SHOOTING:
						//shooting not yet finished
						//await picture and call this listener when finished
						//(or when again an error occurs)
						mCameraIO.awaitTakePicture(this);
						break;
					case NOT_AVAILABLE_NOW:
						//will have to try later for picture shooting
						//TODO
					case NONE:
					case OK:
						//mark as processed for the cases where we don't react to the error
						mCountDownPictures.tickProcessed();
				}
			}
		});
	}
	

	private void setPreviewImage(final Bitmap preview) {

		getActivity().runOnUiThread(new Runnable() {

			@Override
			public void run() {
				lastFramePreviewImageView.setImageBitmap(preview);						
			}
		});

	}
	
	/**
	 * Handler for battery state
	 */
	private BroadcastReceiver myBatteryReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			int bLevel = arg1.getIntExtra("level", 0);

			batteryValue.setText(String.valueOf(bLevel)+"%");
		} 
	};

	
	private void switchUIToUnlimitedMode() {
		normalModeLine1View.setVisibility(View.GONE);						
		normalModeLine2View.setVisibility(View.GONE);						
		normalModeLine3View.setVisibility(View.GONE);						
		unlimitedModeLine1View.setVisibility(View.VISIBLE);						
	}

	private void switchUIToNormalMode() {
		normalModeLine1View.setVisibility(View.VISIBLE);						
		normalModeLine2View.setVisibility(View.VISIBLE);						
		normalModeLine3View.setVisibility(View.VISIBLE);						
		unlimitedModeLine1View.setVisibility(View.GONE);						
	}
	
	public void setListener(CaptureFragmentListener listener) {
		this.mListener = listener;
	}
	
	@Override
	public Spanned getInformation() {
		return Html.fromHtml(getString(R.string.connection_information_message));
	}
}