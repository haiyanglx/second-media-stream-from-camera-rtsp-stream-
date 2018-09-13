package com.meige.policerecord.videorecord;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import com.meige.autosdk.logservice.LogServiceManger;
import com.meige.policerecord.PoliceVideoService;
import com.meige.policerecord.R;
import com.meige.policerecord.UtilTool;
import com.meige.policerecord.audiorecord.AudioMode;
import com.meige.policerecord.exif.Exif;
import com.meige.policerecord.exif.ExifInterface;
import com.meige.policerecord.hardware.HardWareCamera;
import com.meige.policerecord.setting.CameraSetting;
import com.meige.policerecord.setting.ComboPreferences;
import com.meige.policerecord.setting.PreferenceWarper;
import com.meige.policerecord.storage.SDCard;
import com.meige.policerecord.storage.Storage;
import com.meige.policerecord.ui.InLineSettingItem;
import com.meige.policerecord.ui.PoliceModeUI;
import com.meige.policerecord.ui.SurfaceHolderCallBack;
import com.meige.policerecord.ui.TimerWarapper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.meige.policerecord.os.CameraStatusManager.KEY_VIDEO_STATUS;
import static com.meige.policerecord.os.CameraStatusManager.VALUE_VIDEO_CLOSE;

public class PoliceMode implements SurfaceHolderCallBack.UICameraSurfaceOK, HardWareCamera.HardWareCameraDone
    ,VideoRecoderAppWarapper.RecordCallBack,TimerWarapper.TimeOut{
	private static final String TAG = "PoliceMode";
	private static final int RECORD_TYPE_APP = 0;
	private static final int RECORD_TYPE_FRAME = 1;

	public static final int CAMERA_MODE_PHOTO = 1;
	public static final int CAMERA_MODE_VIDEO = 0;
	public static final int CAMERA_MODE_AUDIO = 2;
	private int mCurrentMode = CAMERA_MODE_VIDEO;
	public static final String KEY_RECENT_VIDEO = "key_recent_video";
	public static final String KEY_START_RECENT_VIDEO = "key_start_recent_video";
	public static final String KEY_RECENT_IMAGE = "key_recent_image";
	public static final String KEY_RECENT_AUDIO  = "key_recent_audio";
	public static final String RECENT_FILE = "recent_file";
	public static final String RECENT_FILE_PATH = "recent_file_path";
    public static final int RECENT_VIDEO = 100;
    public static final int RECENT_IMAGE = 101;
    public static final int RECENT_AUDIO = 102;
	private int mOldMode = mCurrentMode;
	private static final String STRING_OFF = "off";
	private static final String STRING_DATE = "date";
	private static int mJpegMarkerLeft = 20;
	private static int mJpegMarkerTop = 20;
	private static int mJpegMarkerSize = 20;
	private Context mContext;
	private PoliceModeUI mPoliceModeUI;
	private HardWareCamera mHardWareCamera;
	private Boolean mIsFirstPreview = true;
	private Boolean mTakePictureByHard = true;
	private ComboPreferences mComboPreferences;
	private CarShutterCallBack mCarShutterCallBack;
	private CarJpegCallBack mCarJpegCallBack;
	private VideoRecorderManger mVideoRecorderManger;
	private CameraPreviewCallBack mCameraPreviewCallBack;
	private byte[] mVideoCallbackBuffer = new byte[PoliceMode.PreivewWidth
			* PoliceMode.PreivewHight * 3 / 2];
	private int mVideoRecordingType = RECORD_TYPE_FRAME;
	private PreferenceWarper mPreferenceWarper;
	private TimerWarapper mTimerWarapper;
	private Handler mHandler;
    private LogServiceManger mLogServiceManger;
	private int mCameraID = 0;
	public static  int PreivewHight = 720;
	public static  int PreivewWidth = 1280;
	public static final int PictureHight = 1080;
	public static final int PictureWidth = 1920;

    private boolean mVideoRecordCan = true;
    private boolean mImageCan = true;
	private static final int TIME_OUT = 1000;
	private static final int LATER_RECORD_FALSE = 1001;
	private static final int LATER_RECORD_TRUE = 1002;
	private static final int LATER_START_RECORD = 1003;
	private static final int CLOSE_RED_LIGHT_DEDAY = 1004;
	private static final int TAKE_PICTURE_DEDAY = 1005;
	private static final int SECOND_STREAM_START = 1006;
	private static final int SECOND_STREAM_STOP = 1007;
	private static PoliceMode mPoliceMode;
	private View mRootView;
	private long firstTime;
	private int mOrientation = 0;
	private boolean needOrientation = false;
	private boolean isCameraOpened;
	public static final String ACTION_CLOSE_LIGHT = "android.media.action.CLOSE_LIGHT";
	private boolean mPreRecord = false;
	private boolean mPreRecordMiddle = false;
	private boolean mLaterRecord = false;
	private SoundPool mSoundPool;
	private ILightInten mLightInten;
	private float mCurrentInfraredValue;
	// For calculate the best fps range for still image capture.
	private final static int MAX_PREVIEW_FPS_TIMES_1000 = 400000;
	private final static int PREFERRED_PREVIEW_FPS_TIMES_1000 = 30000;

	private int mstartPreviewCheckData = 0 ; 
	private int pre_mstartPreviewCheckData = 0  ; 

	private boolean mSecondStreamStart = false;


	private PoliceMode(Context service) {
		mContext = service;
	}

	public static PoliceMode getPoliceMode(Context context){
		if(mPoliceMode == null){
			mPoliceMode = new PoliceMode(context);
		}
		return mPoliceMode;
	}

	public void initMode(){
		init();
	}

	public void setRootView(View rootView){
		mRootView = rootView;
	}

	public View getRootView(){
		return mRootView;
	}
	
	public void initView() {
		UtilTool.LOGD(TAG, "initView");
		mPoliceModeUI = new PoliceModeUI(this);
		mPoliceModeUI.setUICameraSurfaceOK(this);
	}
	
	public void removeView(){
		UtilTool.LOGD(TAG, "removeView");
		if (mPoliceModeUI != null) {
			mPoliceModeUI.setUICameraSurfaceOK(null);
			mPoliceModeUI.onDestroy();
			mPoliceModeUI = null;
		}
	}

	public void refreshLanguage(){
		if (mPoliceModeUI != null) {
			mPoliceModeUI.refreshLanguage();
		}
	}
	
	
	private void init() {
		UtilTool.LOGD(TAG, "PoliceMode init() start");
		initSoundPool();
		mLogServiceManger = LogServiceManger.getInstance();
		mComboPreferences = new ComboPreferences(mContext);
		mPreferenceWarper = PreferenceWarper.getInstace(mContext);
		SDCard.initialize(mContext);
		mComboPreferences.setLocalId(mContext, mCameraID);
		mPoliceModeUI = new PoliceModeUI(this);
		mHardWareCamera = new HardWareCamera();
		mHardWareCamera.setHardWareCameraDone(this);
		mHardWareCamera.openCamera(mCameraID);
		mPoliceModeUI.setUICameraSurfaceOK(this);
		mVideoRecorderManger = new VideoRecorderManger(this);
		mCameraPreviewCallBack = new CameraPreviewCallBack();
		Storage.setSaveSDCard(true);
		Long sizeSave = Storage.getAvailableSpace();
		UtilTool.LOGD(TAG, "sizeSave " + sizeSave);
		initSDReciver();
		registerKeyReserve();
		mTimerWarapper = new TimerWarapper(this);
		mHandler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what){
					case TIME_OUT :
						stopVideoRecordingByFrame();
						break;
					case LATER_RECORD_FALSE:
						mHandler.removeMessages(LATER_RECORD_FALSE);
						stopVideoRecordingByFrame();
						break;
					case LATER_RECORD_TRUE:
						mHandler.removeMessages(LATER_RECORD_TRUE);
						stopVideoRecordingByFrame();
						break;
					case LATER_START_RECORD:
						mHandler.removeMessages(LATER_START_RECORD);
						startVideoRecordingByFrame();
						break;
					case CLOSE_RED_LIGHT_DEDAY:
						mHandler.removeMessages(CLOSE_RED_LIGHT_DEDAY);
						mLightInten.closeRedLight();
//						chooseColorEffect(true);
						break;
					case TAKE_PICTURE_DEDAY:
						mHandler.removeMessages(TAKE_PICTURE_DEDAY);
						takePictureByHard();
						break;
					case SECOND_STREAM_START:
						mHandler.removeMessages(SECOND_STREAM_START);
						VideoSecondStreamWarapper.getInstance().onStart();
						break;
					case SECOND_STREAM_STOP:
						mHandler.removeMessages(SECOND_STREAM_STOP);
						VideoSecondStreamWarapper.getInstance().onStop();
						break;
				}
			}
		};
		
		UtilTool.LOGD(TAG, "PoliceMode 123 init() getAES = "+mPoliceMode.getPreferenceWarper().getSwitchsAES());
		
		mPoliceMode.getPreferenceWarper().iniDefaultSetting();
		
		firstTime = System.currentTimeMillis();
	}

	private void initSoundPool() {
		mSoundPool = new SoundPool(10, AudioManager.STREAM_SYSTEM, 5);
		mSoundPool.load("/system/media/audio/ui/VideoRecord.ogg" ,1);
		mSoundPool.load("/system/media/audio/ui/PressKey.ogg" ,1);
		mSoundPool.load("/system/media/audio/ui/VideoStop.ogg" ,1);
		mSoundPool.load("/system/media/audio/ui/key.ogg" ,1);
	}

	public void soundPlay(int soundID){
		mSoundPool.play(soundID,1, 1, 0, 0, 1);
	}

	private final BroadcastReceiver mSDReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			UtilTool.LOGD(TAG, "BroadcastReceiver mSDReceiver and action is " + action);
			if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {//add
				Storage.setSaveSDCard(true);
			}
			if (Intent.ACTION_MEDIA_EJECT.equals(action)) {//delete
				Storage.setSaveSDCard(false);
			}
		}
	};

	private void initSDReciver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_EJECT);// delete
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);// add
		filter.addDataScheme("file");
		mContext.registerReceiver(mSDReceiver, filter);
		UtilTool.LOGD(TAG, "initReciver end");
	}

	private void unInitReciver(BroadcastReceiver receiver) {
		if (receiver == null) {
			return;
		}
		mContext.unregisterReceiver(receiver);
	}

	public static int[] getPhotoPreviewFpsRange(List<int[]> frameRates) {
		if (frameRates.size() == 0) {
			UtilTool.LOGD(TAG, "No suppoted frame rates returned!");
			return null;
		}

		// Find the lowest min rate in supported ranges who can cover 30fps.
		int lowestMinRate = MAX_PREVIEW_FPS_TIMES_1000;
		for (int[] rate : frameRates) {
			int minFps = rate[Parameters.PREVIEW_FPS_MIN_INDEX];
			int maxFps = rate[Parameters.PREVIEW_FPS_MAX_INDEX];
			if (maxFps >= PREFERRED_PREVIEW_FPS_TIMES_1000 &&
					minFps <= PREFERRED_PREVIEW_FPS_TIMES_1000 &&
					minFps < lowestMinRate) {
				lowestMinRate = minFps;
			}
		}

		// Find all the modes with the lowest min rate found above, the pick the
		// one with highest max rate.
		int resultIndex = -1;
		int highestMaxRate = 0;
		for (int i = 0; i < frameRates.size(); i++) {
			int[] rate = frameRates.get(i);
			int minFps = rate[Parameters.PREVIEW_FPS_MIN_INDEX];
			int maxFps = rate[Parameters.PREVIEW_FPS_MAX_INDEX];
			if (minFps == lowestMinRate && highestMaxRate < maxFps) {
				highestMaxRate = maxFps;
				resultIndex = i;
			}
		}

		if (resultIndex >= 0) {
			return frameRates.get(resultIndex);
		}
		UtilTool.LOGD(TAG, "Can't find an appropiate frame rate range!");
		return null;
	}

    public int[] getMaxPreviewFpsRange(Parameters params) {
        List<int[]> frameRates = params.getSupportedPreviewFpsRange();
        if (frameRates != null && frameRates.size() > 0) {
            // The list is sorted. Return the last element.
            return frameRates.get(frameRates.size() - 1);
        }
        return new int[0];
    }

	public void initParameters() {
		UtilTool.LOGD(TAG, "initParameters() ....");
		if (mHardWareCamera.getCamera() == null) {
			return;
		}
		Parameters parameters = mHardWareCamera.getParameters();
		//parameters.set("orientation", "portrait");
        int[] fpsRange = getPhotoPreviewFpsRange(parameters.getSupportedPreviewFpsRange());
        if (fpsRange.length > 0) {
            parameters.setPreviewFpsRange(
                    fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
                    fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
            UtilTool.LOGD(TAG,"setPreviewFpsRange->"+ fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX] +"--"+
                    fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
        }
		parameters.set("iso-speed", "auto");
		parameters.setWhiteBalance("auto");
		//parameters.set("tsmakeup", "Off");
//		parameters.set("recording-hint", "true");
        parameters.set("dis", "disable");
        /*if (parameters.getMaxNumMeteringAreas() > 0){ // check that metering areas are supported
           List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
           Rect areaRect1 = new Rect(-200, -200, 200, 200);    // specify an area in center of image
           meteringAreas.add(new Camera.Area(areaRect1, 250)); // set weight to 40%
           Rect areaRect2 = new Rect(600, -1000, 1000, -600);  // specify an area in upper right of image
           meteringAreas.add(new Camera.Area(areaRect2, 250)); // set weight to 15%
           Rect areaRect3 = new Rect(600, 600, 1000, 1000);  // specify an area in upper right of image
           meteringAreas.add(new Camera.Area(areaRect3, 250)); // set weight to 15%
           Rect areaRect4 = new Rect(-1000, 600, -600, 1000);  // specify an area in upper right of image
           meteringAreas.add(new Camera.Area(areaRect4, 250)); // set weight to 15%
           Rect areaRect5 = new Rect(-1000, -1000, -600, -600);  // specify an area in upper right of image
           meteringAreas.add(new Camera.Area(areaRect5, 250)); // set weight to 15%
           parameters.setMeteringAreas(meteringAreas);
        }*/
        
		
		parameters.set("denoise", "denoise-on");
		parameters.set("video-size", "1920x1080");
		parameters.set("preview-format", "yuv420sp");
		parameters.setFocusMode(Parameters.FOCUS_MODE_FIXED);
		parameters.setPreviewSize(PreivewWidth, PreivewHight);
		parameters.setPictureSize(PictureWidth, PictureHight);
//		parameters.set("video-cds-mode","off");
//		parameters.set(CameraSetting.PAR_QC_VIDEO_TNR_MODE, "on");
//        parameters.setVideoHDRMode("off");
//        parameters.setVideoRotation("0");
//        parameters.setFaceDetectionMode("off");
		parameters.set("recording-hint", "false");
		parameters.setTouchAfAec("touch-on");
		parameters.set("cds-mode","on");
		parameters.set("tnr-mode", "off");
		parameters.set("hdr-need-1x","true");
		parameters.set("zsl", "on");
		//parameters.setRotation(90);
		//parameters.set(CameraSetting.PAR_ZSL, "on");
        try {
            mHardWareCamera.setParameters(parameters);
        }catch (Exception e){
            UtilTool.LOGD(TAG, "camera setParameters--" + e);
        }
		//mHardWareCamera.setDisplayOrientation(90);
		if(needOrientation) {
			parameters.setRotation(mOrientation);
			mHardWareCamera.setDisplayOrientation(mOrientation);
		}
        //UtilTool.LOGD(TAG, "mVideoRecorderManger 222222 video-flip = "+parameters.get("video-flip"));
		mHardWareCamera.enableShutterSound(true);
		mHardWareCamera.setPreviewDisplay(mPoliceModeUI.getCameraSurfaceView().getThisHolder());
		//updateParameters();
		initBySystemSetting();
	}

    private void initBySystemSetting(){
        UtilTool.LOGD(TAG, "initBySystemSetting() ....");
        if (mHardWareCamera.getCamera() == null) {
            return;
        }
        Parameters parameters = mHardWareCamera.getParameters();
        if(mPreferenceWarper != null){
            String videoSize = mPreferenceWarper.getVideoSize();
            parameters.set(CameraSetting.PAR_VIDEO_SIZE, videoSize);
			int fps = mPreferenceWarper.getFPS();
			if (fps == 30){
				parameters.set(CameraSetting.PAR_VIDEO_HSR, "off");
			}else {
				parameters.set(CameraSetting.PAR_VIDEO_HSR, fps);
			}
			String photoQuality = mPreferenceWarper.getPhotoQuality();
            parameters.set(CameraSetting.PAR_PICTURE_QUALITY, photoQuality);
            String pictureSize = mPreferenceWarper.getPictureSize();
            parameters.set(CameraSetting.PAR_PICTURE_SIZE, pictureSize);
            String waterMark = mPreferenceWarper.getWaterMark();
            parameters.set(CameraSetting.PAR_WATERMARK, waterMark);
			if (CameraSetting.PAR_WATERMARK_DEVICE.equals(waterMark)){
				parameters.set(CameraSetting.PAR_WATERMARK_DEVICE, mPreferenceWarper.getSerialNo());
			}
			if (CameraSetting.PAR_WATERMARK_USER.equals(waterMark)) {
				parameters.set(CameraSetting.PAR_WATERMARK_DEVICE, mPreferenceWarper.getUserNumber());
			}
            String antiBanding = mPreferenceWarper.getAntiBanding();
            parameters.set(CameraSetting.PAR_ANTI_BANDING,antiBanding);
        }
        mHardWareCamera.setParameters(parameters);
    }

    private void systemSettingBeforeTakePic(){
		UtilTool.LOGD(TAG, "systemSettingBeforeRecord() ....");
		if (mHardWareCamera.getCamera() == null) {
			return;
		}
		Parameters parameters = mHardWareCamera.getParameters();
		int[] fpsRange = getPhotoPreviewFpsRange(parameters.getSupportedPreviewFpsRange());
		if (fpsRange.length > 0) {
			parameters.setPreviewFpsRange(
					fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
					fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
			UtilTool.LOGD(TAG,"setPreviewFpsRange->"+ fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX] +"--"+
					fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
		}
		parameters.set("recording-hint", "false");
		if(mPreferenceWarper != null) {
			String photoQuality = mPreferenceWarper.getPhotoQuality();
			parameters.set(CameraSetting.PAR_PICTURE_QUALITY, photoQuality);
			String pictureSize = mPreferenceWarper.getPictureSize();
			parameters.set(CameraSetting.PAR_PICTURE_SIZE, pictureSize);
			String waterMark = mPreferenceWarper.getWaterMark();
			parameters.set(CameraSetting.PAR_WATERMARK, waterMark);
			if (CameraSetting.PAR_WATERMARK_DEVICE.equals(waterMark)){
				parameters.set(CameraSetting.PAR_WATERMARK_DEVICE, mPreferenceWarper.getSerialNo());
			}
			if (CameraSetting.PAR_WATERMARK_USER.equals(waterMark)) {
				parameters.set(CameraSetting.PAR_WATERMARK_DEVICE, mPreferenceWarper.getUserNumber());
			}
			String antiBanding = mPreferenceWarper.getAntiBanding();
			parameters.set(CameraSetting.PAR_ANTI_BANDING,antiBanding);
		}
		parameters.set("tsmakeup", "Off");
		parameters.setFocusMode(Parameters.FOCUS_MODE_FIXED);
		parameters.set("touch-af-aec","touch-on");
		parameters.set("cds-mode","on");
		parameters.set("tnr-mode", "off");
		parameters.set("true-portrait","true-portrait-off");
		parameters.set("hdr-need-1x","true");
		parameters.set("zsl", "on");
		parameters.setCameraMode(1);
		parameters.set(CameraSetting.PAR_VIDEO_HSR, "off");
		parameters.set("video-cds-mode","on");
		parameters.set(CameraSetting.PAR_QC_VIDEO_TNR_MODE, "off");
		mHardWareCamera.setParameters(parameters);
	}

	public void systemSettingBeforeRecord(){
		UtilTool.LOGD(TAG, "systemSettingBeforeRecord() ....");
		if (mHardWareCamera.getCamera() == null) {
			return;
		}
		Parameters parameters = mHardWareCamera.getParameters();
		int[] fpsRange = getMaxPreviewFpsRange(parameters);
		if (fpsRange.length > 0) {
			parameters.setPreviewFpsRange(
					fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
					fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
			UtilTool.LOGD(TAG,"setPreviewFpsRange->"+ fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX] +"--"+
					fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
		}
		if(mPreferenceWarper != null){
			String videoSize = mPreferenceWarper.getVideoSize();
			parameters.set(CameraSetting.PAR_VIDEO_SIZE, videoSize);
			int fps = mPreferenceWarper.getFPS();
			if (fps == 30){
				parameters.set(CameraSetting.PAR_VIDEO_HSR, "off");
			}else {
				parameters.set(CameraSetting.PAR_VIDEO_HSR, fps);
			}
			String photoQuality = mPreferenceWarper.getPhotoQuality();
			parameters.set(CameraSetting.PAR_PICTURE_QUALITY, photoQuality);
			parameters.set(CameraSetting.PAR_PICTURE_SIZE, videoSize);
			String waterMark = mPreferenceWarper.getWaterMark();
			parameters.set(CameraSetting.PAR_WATERMARK, waterMark);
			if (CameraSetting.PAR_WATERMARK_DEVICE.equals(waterMark)){
				parameters.set(CameraSetting.PAR_WATERMARK_DEVICE, mPreferenceWarper.getSerialNo());
			}
			if (CameraSetting.PAR_WATERMARK_USER.equals(waterMark)) {
				parameters.set(CameraSetting.PAR_WATERMARK_DEVICE, mPreferenceWarper.getUserNumber());
			}
			if (fps == 30){
				String antiBanding = mPreferenceWarper.getAntiBanding();
				parameters.set("dis", "enable");
				parameters.set(CameraSetting.PAR_ANTI_BANDING,antiBanding);
			}else {
				String antiBanding = mPreferenceWarper.getAntiBanding();
				parameters.set("dis", "disable");
				parameters.set(CameraSetting.PAR_ANTI_BANDING,antiBanding);
//				parameters.set(CameraSetting.PAR_ANTI_BANDING,"50hz");
//				mPreferenceWarper.setAntiBanding("50hz");
			}
		}
		parameters.setWhiteBalance("auto");
		parameters.set("recording-hint", "true");
		parameters.set("preview-format", "yuv420sp");
		parameters.set("video-cds-mode","off");
		parameters.set(CameraSetting.PAR_QC_VIDEO_TNR_MODE, "on");
		parameters.setVideoHDRMode("off");
		parameters.setVideoRotation("0");
		parameters.setFaceDetectionMode("off");
		parameters.set("hdr-need-1x","true");
		parameters.set("zsl", "off");
		parameters.setCameraMode(0);
		mHardWareCamera.setParameters(parameters);
	}

	public PreferenceWarper getPreferenceWarper(){
		return mPreferenceWarper;
	}

	private void update2SystemSetting(){
		if(mPreferenceWarper != null){
            String preTime = mComboPreferences.getString(CameraSetting.KEY_VIDEO_PRERECORD,
				   Id2String(R.string.pref_camera_video_prerecord_default));
			mPreferenceWarper.setPreRecordTime(preTime);
			String laterTime = mComboPreferences.getString(CameraSetting.KEY_VIDEO_LATER,
					Id2String(R.string.pref_camera_video_tape_recording_default));
			mPreferenceWarper.setLaterRecordTime(laterTime);
		}
	}

	public int getCurrentMode(){
		return mCurrentMode;
	}

	public boolean IsVideoRecording(){
		if(mVideoRecordingType == RECORD_TYPE_FRAME){
			if(mVideoRecorderManger == null){
				UtilTool.LOGD(TAG,"mVideoRecorderManger is null");
				return false;
			}
			return mVideoRecorderManger.isVideoRecording();
		}
        return false;
	}

	public String Id2String(int id) {
		return mContext.getString(id);
	}

	public ComboPreferences getComboPreferences(){
		return  mComboPreferences;
	}

	public Camera getCamera(){
		if(mHardWareCamera != null){
			return mHardWareCamera.getCamera();
		}
		return null;
	}

	public HardWareCamera getHardWareCamera(){
		return mHardWareCamera;
	}

	public void startPreview() {
		UtilTool.LOGD(TAG, "startPreview() ....");
		if (mHardWareCamera.getCamera() == null) {
			return;
		}
		mHardWareCamera.startPreview();

//		mHardWareCamera.addCallbackBuffer(mVideoCallbackBuffer);
//		mHardWareCamera.setPreviewCallbackWithBuffer(mCameraPreviewCallBack);
//		mHandler.postDelayed(scanPreviewDataThread,3000);

		if(mSecondStreamStart) {
			mHardWareCamera.addCallbackBuffer(mVideoCallbackBuffer);
			mHardWareCamera.setPreviewCallbackWithBuffer(mCameraPreviewCallBack);
			startSecondStrem();
		}
		
	}
	
	public void stopPreview() {
		UtilTool.LOGD(TAG, "stopPreview() ....");
		if (mHardWareCamera.getCamera() == null) {
			return;
		}
		mHardWareCamera.stopPreview();

		if(mSecondStreamStart) {
			stopSecondStrem();
		}
		
	}

	public Context getContext() {
		return mContext;
	}

	public PoliceModeUI getPoliceModeUI() {
		return mPoliceModeUI;
	}

	public void releaceCamera() {
		if (mHardWareCamera != null) {
			//mHardWareCamera.stopPreview();
			stopPreview();
			mHardWareCamera.releaseCamera();
			mHardWareCamera.setPreviewCallBack(null);
			mHardWareCamera.setPreviewCallbackWithBuffer(null);
			mHardWareCamera.setOneShotPreviewCallback(null);
			mHardWareCamera = null;
			isCameraOpened = false;
		}
	}

	public Boolean takePicture() {
		if (mPoliceMode.getCurrentMode() != PoliceMode.CAMERA_MODE_PHOTO && !mPoliceMode.IsVideoRecording()){
			modePhotoChange();
		}
        if(!mImageCan){
            return false;
        }
        mImageCan = false;
		UtilTool.LOGD(TAG, "lightInten =" + mLightInten.getCurrentValue());
		if (IsVideoRecording()){
			if (mPoliceMode.isPreRecording() && !mPoliceMode.isPreRecordMiddle()){
				if (mLightInten.getCurrentValue() <= 10){
//					chooseColorEffect(false);
					mLightInten.openRedLight();
//					takePictureByHard();
					mHandler.sendEmptyMessageDelayed(TAKE_PICTURE_DEDAY,200);
					mHandler.sendEmptyMessageDelayed(CLOSE_RED_LIGHT_DEDAY,1000);
				}else {
					takePictureByHard();
				}
			}else {
				takePictureByHard();
			}
		}else {
			if (mLightInten.getCurrentValue() <= 10){
//				chooseColorEffect(false);
				mLightInten.openRedLight();
//				takePictureByHard();
				mHandler.sendEmptyMessageDelayed(TAKE_PICTURE_DEDAY,200);
				mHandler.sendEmptyMessageDelayed(CLOSE_RED_LIGHT_DEDAY,1000);
			}else {
				takePictureByHard();
			}
		}
//		if (mTakePictureByHard) {
//			return takePictureByHard();
//		} else {
//			return takeAPicture();
//		}
		return true;
	}

	public int getCameraID(){
		return mCameraID;
	}

	private Boolean takeAPicture() {
		return false;
	}

	private Boolean takePictureByHard() {
		UtilTool.LOGD(TAG,"takePictureByHard mHardWareCamera = " + mHardWareCamera + ";mIsFirstPreview = " + mIsFirstPreview);
		if (mHardWareCamera == null) {
            mImageCan = true;
			return false;
		}
		if (!IsVideoRecording()){
			systemSettingBeforeTakePic();
		}
		if (mCarShutterCallBack == null) {
			mCarShutterCallBack = new CarShutterCallBack();
		}
		if (mCarJpegCallBack == null) {
			mCarJpegCallBack = new CarJpegCallBack();
		}
		mHardWareCamera.takePicture(mCarShutterCallBack, null, null, mCarJpegCallBack);
		if (mPoliceModeUI.getLightStatus()){
			openCamereLight();
		}
		Intent intent = new Intent();
		intent.setAction(ACTION_CLOSE_LIGHT);
		mContext.sendBroadcast(intent);
		return true;
	}

	public void onDestroy() {
		unInitReciver(mSDReceiver);
		mHardWareCamera.setHardWareCameraDone(null);
		mPoliceModeUI.setUICameraSurfaceOK(null);
		VideoSecondStreamWarapper.getInstance().onDestory();
		releaceCamera();
		mLogServiceManger = null;
		mComboPreferences = null;
		mPreferenceWarper = null;
		mVideoRecorderManger = null;
		mCameraPreviewCallBack = null;
		if (mPoliceModeUI != null) {
			mPoliceModeUI.onDestroy();
			mPoliceModeUI = null;
		}
		unRegisterKeyReserve();
		mIsFirstPreview = true;
		mTimerWarapper = new TimerWarapper(this);
        mImageCan = true;
        mVideoRecordCan = true;
	}

	public void startVideoCallBack(boolean start) {
		UtilTool.LOGD(TAG,"startVideoCallBack");
		if (!start){
			systemSettingBeforeTakePic();
		}
        mVideoRecordCan = true;
		if(mPoliceModeUI != null){
			mPoliceModeUI.videoRecorded(start);
//			if (!mPreRecordMiddle && mPreRecord){
//				mPoliceModeUI.setRecordImg(start);
//				mPoliceModeUI.galleryUIChange();
//			}else {
//				mPoliceModeUI.videoRecorded(start);
//			}
		}
	}

	@Override
	public void recordStateChange(boolean state) {
		startVideoCallBack(state);
	}

	public int getDuration() {//S
//		String durationStr =  mComboPreferences.getString(
//				CameraSetting.KEY_VIDEO_DURATION,
//				Id2String(R.string.pref_camera_video_duration_default));
		String durationStr =  mPreferenceWarper.getdurationTime();
		int durations = Integer.parseInt(durationStr) * 60;
		return durations + getLaterRecordTime();
	}

	public int getLaterRecordTime(){//S
//		String time =  mComboPreferences.getString(
//				CameraSetting.KEY_VIDEO_LATER,
//				Id2String(R.string.pref_camera_video_tape_recording_default));
		String time = mPreferenceWarper.getLaterRecordTime();
		int t = 0;
		if(!STRING_OFF.equals(time)){
			t = Integer.parseInt(time);
		}
		UtilTool.LOGD(TAG,"getLaterRecordTime t = " +t);
		return t;
	}

	public int getSysLaterRecordTime(){//S
		String value ="";
		if(mPreferenceWarper != null){
			value = mPreferenceWarper.getLaterRecordTime();
		}
		int t = 0;
		if(!STRING_OFF.equals(value)){
			t = Integer.parseInt(value);
		}
		UtilTool.LOGD(TAG,"getSysLaterRecordTime t = " +t);
		return t;
	}

	public int getPreRecordTime(){//S
		String time = mPreferenceWarper.getPreRecordTime();
		int t = 0;
		if(!STRING_OFF.equals(time)){
			t = Integer.parseInt(time);
		}
		UtilTool.LOGD(TAG,"getPreRecordTime t = " +t);
		return t;
	}

	@Override
	public void timeOut() {
		UtilTool.LOGD(TAG,"timeOut ---- ");
		mHandler.sendEmptyMessage(TIME_OUT);
		mTimerWarapper.stopTimePool();
	}

	class CarShutterCallBack implements Camera.ShutterCallback {

		@Override
		public void onShutter() {
			UtilTool.LOGD(TAG, "CarShutterCallBack ....");
		}
	}

	private byte[] mJpegData;
	private Camera mCameraCall;

	class CarJpegCallBack implements Camera.PictureCallback {

		@Override
		public void onPictureTaken(byte[] jpegData2, Camera camera2) {
			UtilTool.LOGD(TAG, "CarJpegCallBack onPictureTaken ...." + jpegData2.length);
			mJpegData  = jpegData2;
			mCameraCall = camera2;
			new Thread(new Runnable() {
				@Override
				public void run() {
					Bitmap bitmap = UtilTool.Bytes2Bimap(mJpegData);
					ExifInterface exif = Exif.getExif(mJpegData);
					/*String waterMark = mPreferenceWarper.getWaterMark();
					if(!STRING_OFF.equals(waterMark)){
						Parameters parameters = mCameraCall.getParameters();
						Size s = parameters.getPictureSize();
						int width = s.width;
						if(bitmap != null){
							String title = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
							Bitmap bitmap2 = null;
							if(STRING_DATE.equals(waterMark)){
								bitmap2 = UtilTool.drawTextToRightTop(mPoliceService,bitmap,title,width/20,
										Color.WHITE,mJpegMarkerLeft,mJpegMarkerTop);
							}else{
								bitmap2 = UtilTool.drawTextToRightTop(mPoliceService,bitmap,title,waterMark,width/20,
										Color.WHITE,mJpegMarkerLeft,mJpegMarkerTop);
							}
							if(bitmap2 != null){
								mJpegData = UtilTool.Bitmap2Bytes(bitmap2);
							}
						}
					}*/
					JpegInfo ji = new JpegInfo(mJpegData, System.currentTimeMillis(), null);
					startPreview();
					storageImage(ji, mCameraCall ,exif);
					mImageCan = true;
				}
			}).start();
		}

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		UtilTool.LOGD(TAG, "surfaceCreated");
		initParameters();
		startPreview();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		UtilTool.LOGD(TAG, "surfaceChanged,width" + width + ",height=" + height);
		try {
            if (mVideoRecorderManger != null && !mVideoRecorderManger.isVideoRecording()){
//				initBySystemSetting();
            }
        }catch (Exception e){
            UtilTool.LOGE(TAG, "updateBySystemSetting crash",e);
        }

//		if (mIsFirstPreview) {
//			try {
//				if (mIsFirstPreview && mVideoRecorderManger != null && !mVideoRecorderManger.isVideoRecording()) {
//					initParameters();
//					startPreview();
//					mIsFirstPreview = false;
//				}
//			} catch (Exception e) {
//				UtilTool.LOGE(TAG, "initParameters crash",e);
//			}
//		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		UtilTool.LOGD(TAG, "surfaceDestroyed");
	}

	@Override
	public void cameraHasOpened() {
		UtilTool.LOGD(TAG, "cameraHasOpened");
		isCameraOpened = true;
	}

	public boolean getCameraStatus(){
		return isCameraOpened;
	}

	public void alarmKeyDown(){
		if(IsVideoRecording()){
			markerButtonClick();
		}else{
			if(AudioMode.getAudioMode(mContext).isAudioRecording()) {
				boolean result = AudioMode.getAudioMode(mContext).stopAudioRecord();
			}
			videoRecordByFrame();
			markerButtonClick();
		}
	}

	public void modeChange(){
		if (mCurrentMode != CAMERA_MODE_VIDEO ){
			mCurrentMode = CAMERA_MODE_VIDEO;
			mPoliceModeUI.modeChange();
		}
	}

	public void modePhotoChange(){
		if (mCurrentMode != CAMERA_MODE_PHOTO ){
			mCurrentMode = CAMERA_MODE_PHOTO;
			mPoliceModeUI.modeChange();
		}
	}

	public void modeAudioChange(){
		if (mCurrentMode != CAMERA_MODE_AUDIO ){
			mCurrentMode = CAMERA_MODE_AUDIO;
			mPoliceModeUI.modeChange();
		}
	}

	public boolean startVideoRecord(){
		SystemProperties.set("persist.sys.record.split.fd", "0");
		if(mPreRecord) {
			UtilTool.LOGD(TAG,"startVideoRecord -- startVideoRecord ");
			SystemProperties.set("persist.sys.precord.start", "1");
			mPreRecordMiddle = true;
			soundPlay(1);
			startVideoCallBack(true);
			return true;
		}else {
			if(IsVideoRecording()) {
				return true;
			}else {
				if(mPoliceModeUI == null) {
					UtilTool.LOGD(TAG,"mPoliceModeUI == null ");
					return false;
				}
				if(mVideoRecorderManger == null || mHardWareCamera == null || mHardWareCamera.getCamera() == null){
					UtilTool.LOGD(TAG,"mMediaRecorder is not init");
		            mPoliceModeUI.videoRecorded(false);
					return false;
				}
				UtilTool.LOGD(TAG,"startVideoRecord ");
				startVideoRecordingByFrame();
			}
			return true;
		}
	}

	public boolean startLaterRecord(){
		UtilTool.LOGD(TAG,"startLaterRecord ");
		mHandler.sendEmptyMessageDelayed(LATER_START_RECORD,500);
//		startVideoRecordingByFrame();
		return true;
	}
	
	public boolean startSecondStrem() {
		UtilTool.LOGD(TAG,"startSecondStrem ");
		mHandler.sendEmptyMessageDelayed(SECOND_STREAM_START,3000);
		return true;
	}
	
	public boolean stopSecondStrem() {
		UtilTool.LOGD(TAG,"stopSecondStrem ");
		mHandler.sendEmptyMessageDelayed(SECOND_STREAM_STOP,500);
		return true;
	}

	public boolean stopVideoRecord(){ 
		if(IsVideoRecording()) {
			UtilTool.LOGD(TAG,"stopVideoRecord ");
			UtilTool.LOGD(TAG,"getPostRecordSwitch " + getPostRecordSwitch());
			if (getPostRecordSwitch()){
				if (mLaterRecord){
					stopVideoRecordingByFrame();
					mHandler.removeMessages(LATER_RECORD_FALSE);
				}else {
					mLaterRecord = true;
					soundPlay(3);
					mPoliceModeUI.setRecordImg(false);
					mHandler.sendEmptyMessageDelayed(LATER_RECORD_FALSE,getLaterRecordTime() * 1000);
				}
			}else {
				stopVideoRecordingByFrame();
			}
		}
		return true;
	}

	public void videoRecordButtonClick() {
		long now = System.currentTimeMillis();
		UtilTool.LOGD(TAG,"videoRecordButtonClick click="+(now-firstTime));
		
		if(now-firstTime > 100) {
			firstTime = now;
			if(IsVideoRecording()) {
				if(mPreRecordMiddle) {
					if (mLaterRecord){
						stopVideoRecord();
						startLaterRecord();
					}else {
						stopVideoRecord();
					}
				}else {
					if(mPreRecord) {
						startVideoRecord();
					}else {
						if (mLaterRecord){
							stopVideoRecord();
							startLaterRecord();
						}else {
							stopVideoRecord();
						}
					}
				}
			}
			else {
				if(AudioMode.getAudioMode(mContext).isAudioRecording()) {
					boolean result = AudioMode.getAudioMode(mContext).stopAudioRecord();
				}
				startVideoRecord();
			}
		}
		
//		if(mVideoRecorderManger == null || mHardWareCamera == null || mHardWareCamera.getCamera() == null || mIsFirstPreview){
//            mPoliceModeUI.videoRecorded(false);
//			return;
//		}
//		if(mVideoRecordingType == RECORD_TYPE_FRAME){
//			Intent audiointent = new Intent();
//			audiointent.setAction("athis.com.android.audiorecord.status");
//			mContext.sendBroadcast(audiointent);
//			videoRecordByFrame();
//		}else if(mVideoRecordingType == RECORD_TYPE_APP){
//			videoRecordByAPP();
//		}
	}

	public void galleryButtonClick() {
		if (!IsVideoRecording()){
			goToGallery();
		}
	}

	public void recentButtonClick(){
		if (!IsVideoRecording()) {
			if (mCurrentMode == CAMERA_MODE_PHOTO) {
                String imagePath = Settings.System.getString(mContext.getContentResolver(), KEY_RECENT_IMAGE);
                UtilTool.LOGD(TAG,"recentButtonClick - imagePath" + imagePath);
                if (imagePath != null){
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.meige.imageshow", "com.meige.imageshow.ui.ImagePlayerActivity");
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    intent.putExtra(RECENT_FILE,RECENT_IMAGE);
                    intent.putExtra(RECENT_FILE_PATH,imagePath);
                    mContext.startActivity(intent);
                }
			}else if (mCurrentMode == CAMERA_MODE_AUDIO) {
                String audioPath = Settings.System.getString(mContext.getContentResolver(), KEY_RECENT_AUDIO);
                UtilTool.LOGD(TAG,"recentButtonClick - audioPath" + audioPath);
                if (audioPath != null){
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.meige.imageshow", "com.meige.imageshow.ui.AudioPlayerActivity");
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    intent.putExtra(RECENT_FILE,RECENT_AUDIO);
                    intent.putExtra(RECENT_FILE_PATH,audioPath);
                    mContext.startActivity(intent);
                }
			}else {
                String videoPath = Settings.System.getString(mContext.getContentResolver(), KEY_RECENT_VIDEO);
                UtilTool.LOGD(TAG,"recentButtonClick - videoPath" + videoPath);
                if (videoPath != null){
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.meige.imageshow", "com.meige.imageshow.ui.VideoPlayerActivity");
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    intent.putExtra(RECENT_FILE,RECENT_VIDEO);
                    intent.putExtra(RECENT_FILE_PATH,videoPath);
                    mContext.startActivity(intent);
                }
			}
		}
	}

	private void goToGallery(){
		//go to Gallery
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClassName("com.meige.imageshow", "com.meige.imageshow.LoginActivity");
		intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		mContext.startActivity(intent);
	}

	public void markerButtonClick (){
		UtilTool.LOGD(TAG,"markerButtonClick");
		if(!IsVideoRecording()){
			return;
		}
		if(mVideoRecordingType == RECORD_TYPE_FRAME){
			if(getMarkerState()){
				mVideoRecorderManger.setMarkerState(false);
			}else{
				mVideoRecorderManger.setMarkerState(true);
			}
			mPoliceModeUI.markerStateChangeCallBack();
		}
	}

	public boolean getMarkerState(){
		UtilTool.LOGD(TAG,"getMarkerState");
		if(!IsVideoRecording()){
			return false;
		}
		if(mVideoRecordingType == RECORD_TYPE_FRAME){
			return mVideoRecorderManger.getMarkerState();
		}
		return false;
	}

	private void videoRecordByFrame(){
		if(!mVideoRecordCan){
			return;
		}
		mVideoRecordCan = false;
		if(mVideoRecorderManger.isVideoRecording()){
			if(getLaterRecordTime() != 0){
				mTimerWarapper.setTimeDution(getLaterRecordTime());
				mTimerWarapper.startTimePool();
				return ;
			}
			stopVideoRecordingByFrame();
		}else{
			startVideoRecordingByFrame();
		}
	}

	

	public boolean startVideoRecordingByFrame(){
		if(mVideoRecorderManger == null || mHardWareCamera == null || mHardWareCamera.getCamera() == null){
			return false;
		}
		return mVideoRecorderManger.startVideoRecorder();
	}

	public boolean stopVideoRecordingByFrame(){
		if(mVideoRecorderManger == null || mHardWareCamera == null || mHardWareCamera.getCamera() == null){
			setSpCameraStatus();
			return false;
		}
		if(mVideoRecorderManger.stopVideoRecorder()){
			return true;
		}
		return false;
	}

	public void setSpCameraStatus(){
		Settings.System.putInt(mContext.getContentResolver(),KEY_VIDEO_STATUS,VALUE_VIDEO_CLOSE);
	}

	public Boolean onBackPressed() {
		if (mPoliceModeUI != null) {
			if (IsVideoRecording()) {
				return true;
			}
		}
		return false;
	}

	public void onSettingChanged(InLineSettingItem item) {
		//updateParameters();
		initBySystemSetting();
		update2SystemSetting();
	}

	private void storageImage(JpegInfo ji, Camera camera) {
		Parameters parameters = camera.getParameters();
		ExifInterface exif = Exif.getExif(ji.jpegData);
		storageImage(ji,camera,exif);
	}

	private void storageImage(JpegInfo ji, Camera camera ,ExifInterface exif) {
		UtilTool.LOGD(TAG,"storageImage");
		Parameters parameters = camera.getParameters();
		
		int orientation = mOrientation;//Exif.getOrientation(exif);
		
		UtilTool.LOGD(TAG,"getOrientation ="+orientation);
		
		String title = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(new Date());
		if("on".equals(mPoliceMode.getPreferenceWarper().getSwitchsAES())){
            title = title + "_Enc";
        }
		String mimeType = "jpeg";
		String jpegPath = Storage.generateFilepath(title, mimeType);
		Size s = parameters.getPictureSize();
		int width, height;
//		if ((parameters.getInt("rotation") + orientation) % 180 == 0) {
//			width = s.width;
//			height = s.height;
//		} else {
//			width = s.height;
//			height = s.width;
//		}
		
		if(mOrientation == 0) {
			width = s.width;
			height = s.height;
		}
		else {
			width = s.height;
			height = s.width;
		}
		
		
		//Storage.writeFile(jpegPath, ji.jpegData, exif, mimeType);
		Storage.writeFile(jpegPath, ji.jpegData);
        Settings.System.putString(mContext.getContentResolver(),KEY_RECENT_IMAGE,jpegPath);
		UtilTool.LOGD(TAG, "mImagePath =  " + jpegPath);
		try {
			mLogServiceManger.writeCapture("Capture",jpegPath,title);
		}catch (Exception e){
			UtilTool.LOGD(TAG, "mLogServiceManger has not start " + e);
		}
		int size = 0;
		File f = new File(jpegPath);
		if (f.exists() && f.isFile()) {
			size = (int) f.length();
		}
		Uri uri = Storage.addImage(mContext.getContentResolver(), title, ji.captureStartTime, ji.location,
				orientation, size, jpegPath, width, height, mimeType);
	}

	public synchronized void deleteFirstFile(){
		new Thread(new Runnable() {
			@Override
			public void run() {
				List<File> flieList = loadFile(Storage.getCameraRootPath());
				if(flieList != null && !flieList.isEmpty()){
					ContentResolver contentResolver = mContext.getContentResolver();
					Storage.deleteImage(contentResolver,Storage.getFileUri(mContext,flieList.get(0)));
				}
			}
		}).start();
	}

	private synchronized List<File> loadFile(final String path) {
		UtilTool.LOGD(TAG, "loadFile has start ");
		List<File> flieList = new ArrayList<File>();
		File fileAll = new File(path + "/");
		if (!fileAll.exists()) {
			fileAll.mkdirs();
		}
		File[] files = fileAll.listFiles();
		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			if (file.isFile() && isImageOrVideoOrAudio(file)) {
				flieList.add(file);
			}
		}
		UtilTool.LOGD(TAG, "loadFile has end mThumbItem.size = " + flieList.size());
		flieList = UtilTool.arrayScrol2Hight(flieList);
		return flieList;
	}

	private Boolean isImageOrVideoOrAudio(File file) {
		if(file == null){
			return false;
		}else{
			if(Storage.isAudio(file) || Storage.isImage(file) || Storage.isVideo(file)){
				return true;
			}
		}
		return false;
	}

	private final class JpegInfo {
		byte[] jpegData;
		long captureStartTime;
		Location location;

		public JpegInfo(byte[] data, long time, Location l) {
			jpegData = data;
			captureStartTime = time;
			location = l;
		}
	}

	private KeyPressReserve mKeyReceiver = null;
	private final String SYSTEM_DIALOG_REASON_KEY = "reason";
	private final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
	private final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";

	class KeyPressReserve extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			UtilTool.LOGD(TAG, "KeyPressReserve has do action =" + action);
			if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {

			}
		}
	}

	public void registerKeyReserve() {
		UtilTool.LOGD(TAG, "registerHomeKeyReserve() has do");
		mKeyReceiver = new KeyPressReserve();
		final IntentFilter homeFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
		homeFilter.addAction("android.media.VOLUME_CHANGED_ACTION");
		mContext.registerReceiver(mKeyReceiver, homeFilter);
	}

	public void unRegisterKeyReserve() {
		UtilTool.LOGD(TAG, "unRegisterHomeKeyReserve() has do");
		if (mKeyReceiver != null) {
			mContext.unregisterReceiver(mKeyReceiver);
		}
	}

	public static int bytesToInt(byte[] src, int offset) {  
	    int value;    
	    value = (int) ((src[offset] & 0xFF)   
	            | ((src[offset+1] & 0xFF)<<8)   
	            | ((src[offset+2] & 0xFF)<<16)   
	            | ((src[offset+3] & 0xFF)<<24));  
	    return value;  
	}  
	
//	Runnable scanPreviewDataThread = new Runnable() {
//		@Override
//		public void run() {
//			if ( mstartPreviewCheckData != pre_mstartPreviewCheckData ) {
////				UtilTool.LOGD(TAG, "CARL >>>>>> normal  %d  " + mstartPreviewCheckData);
//				pre_mstartPreviewCheckData = mstartPreviewCheckData;
//				mHandler.postDelayed(scanPreviewDataThread,3000);
//			}else {
//				mHardWareCamera.stopPreview();
//				releaceCamera();
//				mHardWareCamera = new HardWareCamera();
//				mHardWareCamera.openCamera(mCameraID);
//				initParameters();
//				startPreview();
//				pre_mstartPreviewCheckData = 0;
//				mstartPreviewCheckData = 0;
//			}
//		}	
//	};
	
	class CameraPreviewCallBack implements Camera.PreviewCallback{

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {

			if(mSecondStreamStart) {
				VideoSecondStreamWarapper.getInstance().onPreviewFrame(data,camera);
			}

		}
	}
	
	public void setOrientation(int degree) {
		if(mOrientation != degree) {
			needOrientation = true;
			mOrientation = degree;
			UtilTool.LOGD(TAG, "setOrientation"+mOrientation);
//			mHardWareCamera.stopPreview();
			stopPreview();
//			mHardWareCamera.setDisplayOrientation(mOrientation);
			if(IsVideoRecording()) {
				mVideoRecorderManger.pauseVideoRecord();
			}
			
			Parameters mparameters = mHardWareCamera.getParameters();
			if(mparameters != null) {
				//UtilTool.LOGD(TAG, "mVideoRecorderManger 3333  video-flip = "+mparameters.get("video-flip"));
				
				if(IsVideoRecording()) {
//					UtilTool.LOGD(TAG, "mVideoRecorderManger IsVideoRecording video-flip = "+mparameters.get("video-flip"));

					if (mOrientation == 180) {
                        if(mVideoRecorderManger.isFrist180){
                            mparameters.set("video-flip", "flip-h");
                        }else{
                            mparameters.set("video-flip", "flip-v");
                        }
					} else {
                        if(mVideoRecorderManger.isFrist180){
                            mparameters.set("video-flip", "flip-vh");
                        }else{
                            mparameters.set("video-flip", "off");
                        }
                        //mVideoRecorderManger.isFrist180 = false;
					}
					
					//mVideoRecorderManger.setOrientation(mOrientation);
					
					UtilTool.LOGD(TAG, "mVideoRecorderManger setOrientation"+mOrientation);
				}
				
				if (mOrientation == 180) {
					mparameters.set("preview-flip", "flip-v");
					mparameters.set("snapshot-picture-flip", "flip-v");
					
				} else {
					mparameters.set("preview-flip", "off");
					mparameters.set("snapshot-picture-flip", "off");
				}
				
				UtilTool.LOGD(TAG, "preview setOrientation"+mOrientation);
				
				mHardWareCamera.setParameters(mparameters);
                //UtilTool.LOGD(TAG, "mVideoRecorderManger video-flip = "+mparameters.get("video-flip"));
			}
			else{
				UtilTool.LOGD(TAG, "mparameters is null");
			}
			
			startPreview();
			
			if(IsVideoRecording()) {
				mVideoRecorderManger.resumeVideoRecord();
			}
		}
	}
	
	public int getOrientation() {
		return mOrientation;
	}
	
	public boolean startPreRecord() {
		UtilTool.LOGD(TAG, "startPreRecord "+mPreRecord);
		if(getPreRecordSwitch()) {
			if(!mPreRecord) {
				SystemProperties.set("persist.sys.precord.config", "1");
				mPreRecord = true;
				if(!IsVideoRecording()) {
					if(mVideoRecorderManger == null || mHardWareCamera == null || mHardWareCamera.getCamera() == null){
						UtilTool.LOGD(TAG,"startPreRecord mMediaRecorder is not init");
						mPreRecord = false;
						return false;
					}
					modeChange();
					UtilTool.LOGD(TAG,"startVideoRecord ");
					return startVideoRecordingByFrame();
				}
			}
		}
		return true;
	}

	public boolean isPreRecording() {
		return mPreRecord;
	}

	public void setPreRecord(boolean preRecord){
		mPreRecord = preRecord;
	}

	public boolean isPreRecordMiddle(){
		return mPreRecordMiddle;
	}

	public void setPreRecordMiddle(boolean preRecordMiddle){
		mPreRecordMiddle = preRecordMiddle;
	}

	public boolean isLaterRecord(){
		return mLaterRecord;
	}

	public void setLaterRecord(boolean laterRecord){
		mLaterRecord = laterRecord;
	}
	
	public boolean getPreRecordSwitch() {
		return mPreferenceWarper.getPreRecordSwitch();
	}

	public boolean getPostRecordSwitch() {
		return mPreferenceWarper.getPostRecordSwitch();
	}

	public void openCamereLight(){
		try {
			UtilTool.LOGD(TAG, "lightopen");
			Parameters mParameters = mHardWareCamera.getParameters();
			mParameters.setFlashMode(Parameters.FLASH_MODE_ON);
			mHardWareCamera.setParameters(mParameters);
		} catch (Exception e) {
			e.printStackTrace();
			UtilTool.LOGD(TAG, "lighton = " + e);
		}
	}

	public void closeCameraLight(){
		try {
			UtilTool.LOGD(TAG, "lightclose");
			Parameters mParameters = mHardWareCamera.getParameters();
			mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			mHardWareCamera.setParameters(mParameters);
		} catch (Exception e) {
			e.printStackTrace();
			UtilTool.LOGD(TAG, "lightclose = " + e);
		}
	}

	public void setOnLightInten(ILightInten lightInten){
		this.mLightInten = lightInten;
	}

	public void chooseColorEffect(boolean none){
//		try {
			if(IsVideoRecording()) {
				mVideoRecorderManger.pauseVideoRecord();
			}
			stopPreview();
			Parameters parameters = mHardWareCamera.getParameters();
			if (none){
				parameters.setColorEffect("none");
			}else {
				parameters.setColorEffect("mono");
			}
			mHardWareCamera.setParameters(parameters);
			startPreview();
			if(IsVideoRecording()) {
				mVideoRecorderManger.resumeVideoRecord();
			}
	}

	public void setCurrentInfraredValue(float currentInfraredValue){
		this.mCurrentInfraredValue = currentInfraredValue;
	}

	public float getCurrentInfraredValue(){
		return mCurrentInfraredValue;
	}

    public void writeRecordStart(String type,String path,String fileName){
	    mLogServiceManger.writeRecordStart(type,path,fileName);
    }

    public void writeImportantVideoFile(String type,String path,String fileName){
        mLogServiceManger.writeImportantVideoFile(type,path,fileName);
    }

    public void writeRecordStop(String type,String path,String fileName){
        mLogServiceManger.writeRecordStop(type,path,fileName);
    }
}
