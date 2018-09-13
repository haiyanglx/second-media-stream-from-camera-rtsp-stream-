package com.meige.policerecord.videorecord;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.EncoderCapabilities;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import com.meige.policerecord.setting.PreferenceWarper;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.meige.policerecord.ApiHelper;
import com.meige.policerecord.videorecord.PoliceMode;
import com.meige.policerecord.R;
import com.meige.policerecord.UtilTool;
import com.meige.policerecord.setting.CameraSetting;
import com.meige.policerecord.setting.ComboPreferences;
import com.meige.policerecord.storage.SDCard;
import com.meige.policerecord.storage.Storage;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.LedManager;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.provider.Settings;

import static com.meige.policerecord.os.CameraStatusManager.CAMERA_STATUS;
import static com.meige.policerecord.os.CameraStatusManager.KEY_VIDEO_STATUS;
//import android.media.EncoderCapabilities.VideoEncoderCap;
import android.hardware.Camera.Parameters;
import android.widget.Toast;


public class VideoRecorderManger implements MediaRecorder.OnInfoListener ,MediaRecorder.OnErrorListener{

    private static final String TAG = "VideoRecorderManger";

    private PoliceMode mPoliceMode;
    private MediaRecorder mMediaRecorder;
    private CamcorderProfile mProfile;
    private ComboPreferences mPreferences;
    private ContentValues mCurrentVideoValues;
    private String mVideoFilename;
    private String mVideoTitle;
    private String mCurrentVideoFilename;
    private String mPreVideoFilename;
    private ContentResolver mContentResolver;
    private int mVideoEncoder;
    private int mAudioEncoder;
    private long mDuration = 0L;
    private boolean mIsVideoRecording = false;
    private boolean mIsVoiceRecording = false;
    private boolean mIsMarker = false;
    private LedManager mLedManager;
    public boolean isFrist180 = false;
    private long splitTime = 0L;
    private static final DefaultHashMap<String, Integer>
            VIDEO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();
    private static final DefaultHashMap<String, Integer>
            AUDIO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();
    static {

        VIDEO_ENCODER_TABLE.put("h263", MediaRecorder.VideoEncoder.H263);
        VIDEO_ENCODER_TABLE.put("h264", MediaRecorder.VideoEncoder.H264);
        int h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                "HEVC", null, MediaRecorder.VideoEncoder.DEFAULT);
        if (h265 == MediaRecorder.VideoEncoder.DEFAULT) {
            h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                    "H265", null, MediaRecorder.VideoEncoder.DEFAULT);
        }
        VIDEO_ENCODER_TABLE.put("h265", h265);
        VIDEO_ENCODER_TABLE.put("m4v", MediaRecorder.VideoEncoder.MPEG_4_SP);
        VIDEO_ENCODER_TABLE.putDefault(MediaRecorder.VideoEncoder.DEFAULT);

        AUDIO_ENCODER_TABLE.put("amrnb", MediaRecorder.AudioEncoder.AMR_NB);
        AUDIO_ENCODER_TABLE.put("amrwb", MediaRecorder.AudioEncoder.AMR_WB);
        AUDIO_ENCODER_TABLE.put("aac", MediaRecorder.AudioEncoder.AAC);
        AUDIO_ENCODER_TABLE.putDefault(MediaRecorder.AudioEncoder.DEFAULT);
    }

    private int videoWidth;
    private int videoHeight;
    private boolean isHSR;
    private int captureRate;


    public boolean getMarkerState(){
        return mIsMarker;
    }

    public void setMarkerState(boolean state){
        mIsMarker = state;
    }

    private final OnMediaSavedListener mOnVideoSavedListener =
            new OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {

                    }
                }
            };

    public VideoRecorderManger(PoliceMode mode){
        mPoliceMode = mode;
        mPreferences = mPoliceMode.getComboPreferences();
        mContentResolver = mPoliceMode.getContext().getContentResolver();
        //initProfile();
        //uploadPreferences();
        mLedManager =  (LedManager)mPoliceMode.getContext().getSystemService(Context.LED_SERVICE);
    }

    public boolean isVideoRecording(){
        return mIsVideoRecording;
    }

    public void initProfile(){
        UtilTool.LOGD(TAG,"initProfile");
//        String videoQuality = mPreferences.getString(CameraSetting.KEY_BACK_VIDEO_SIZE,
//                mPoliceMode.Id2String(R.string.setting_back_video_size_def));
        String videoQuality = PreferenceWarper.getInstace(mPoliceMode.getContext()).getVideoSize();
        int quality = CameraSetting.VIDEO_QUALITY_TABLE.get(videoQuality);
        int cameraID = mPoliceMode.getCameraID();
        mProfile = CamcorderProfile.get(cameraID, quality);
    }

    public void uploadPreferences(){
        UtilTool.LOGD(TAG,"uploadPreferences");
        String videoEncoder =mPoliceMode.getPreferenceWarper().getVideoEncoder();
        mVideoEncoder = VIDEO_ENCODER_TABLE.get(videoEncoder);
        mProfile.videoCodec = mVideoEncoder;
        String audioEncoder = "aac";
        mAudioEncoder = AUDIO_ENCODER_TABLE.get(audioEncoder);
        mProfile.audioCodec = mAudioEncoder;
        String durationStr =  mPoliceMode.getPreferenceWarper().getdurationTime();
        int duration = Integer.parseInt(durationStr);
        mProfile.duration = duration * 60000;//ms
//        mProfile.videoFrameRate = PreferenceWarper.FPS;
    }
    
    public void setOrientation(int Orientation) {
    	mMediaRecorder.setOrientationHint(Orientation);
    }
    
    public void pauseVideoRecord() {
    	if (mMediaRecorder != null) {
    		mMediaRecorder.pause();
    	}
    }
    
    
    public void resumeVideoRecord() {
    	if (mMediaRecorder != null) {
    		mMediaRecorder.resume();
    	}
    }

    private boolean isSessionSupportedByEncoder(int w, int h, int fps) {
        int expectedMBsPerSec = w * h * fps;

        List<EncoderCapabilities.VideoEncoderCap> videoEncoders = EncoderCapabilities.getVideoEncoders();
        for (EncoderCapabilities.VideoEncoderCap videoEncoder: videoEncoders) {
            if (videoEncoder.mCodec == mVideoEncoder) {
                int maxMBsPerSec = (videoEncoder.mMaxFrameWidth * videoEncoder.mMaxFrameHeight
                        * videoEncoder.mMaxFrameRate);
                if (expectedMBsPerSec > maxMBsPerSec) {
                    UtilTool.LOGD(TAG,"Selected codec " + mVideoEncoder
                            + " does not support width(" + w
                            + ") X height ("+ h
                            + "@ " + fps +" fps");
                    UtilTool.LOGD(TAG, "Max capabilities: " +
                            "MaxFrameWidth = " + videoEncoder.mMaxFrameWidth + " , " +
                            "MaxFrameHeight = " + videoEncoder.mMaxFrameHeight + " , " +
                            "MaxFrameRate = " + videoEncoder.mMaxFrameRate);
                    return false;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    public void initVideoMedia(){
        UtilTool.LOGD(TAG,"initVideoMedia mMediaRecorder " +mMediaRecorder);
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        } else {
        }

//        initProfile();
//        uploadPreferences();

        mMediaRecorder.reset();
        mPoliceMode.getCamera().unlock();
        
        mMediaRecorder.setCamera(mPoliceMode.getCamera());

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if (isHSR){
            mMediaRecorder.setOutputFormat(mProfile.fileFormat);
            mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
            mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate);
            mMediaRecorder.setVideoEncoder(mProfile.videoCodec);
            mMediaRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);
            mMediaRecorder.setAudioChannels(mProfile.audioChannels);
            mMediaRecorder.setAudioSamplingRate(mProfile.audioSampleRate);
            mMediaRecorder.setAudioEncoder(mProfile.audioCodec);
        }else {
            mMediaRecorder.setProfile(mProfile);
        }
        mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mMediaRecorder.setMaxDuration(mProfile.duration);
        if (captureRate > 0){
            int targetFrameRate = captureRate;
            mMediaRecorder.setVideoFrameRate(targetFrameRate);
//            int scaledBitrate = mProfile.videoBitRate * (targetFrameRate / mProfile.videoFrameRate);
//            UtilTool.LOGD(TAG,"scaledBitrate " + scaledBitrate);
//            mMediaRecorder.setVideoEncodingBitRate(scaledBitrate/4);
        }else {
//            UtilTool.LOGD(TAG,"videoBitRate " + mProfile.videoBitRate/4);
//            mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate/4);
        }
        if ("720x480".equals(mPoliceMode.getPreferenceWarper().getVideoSize())){
            mMediaRecorder.setVideoEncodingBitRate(Storage.LOW_VIDEO_BIT_RATE);
        }else {
            mMediaRecorder.setVideoEncodingBitRate(Storage.HIGH_VIDEO_BIT_RATE);
        }
        generateVideoFilename(mProfile.fileFormat);
        mMediaRecorder.setOutputFile(mVideoFilename);
//        long maxFileSize = Storage.getAvailableSpace() - 2 * Storage.LOW_STORAGE_THRESHOLD_BYTES;
//        if (Storage.isSaveSDCard() && maxFileSize > Storage.SDCARD_SIZE_LIMIT) {
//            maxFileSize = Storage.SDCARD_SIZE_LIMIT;
//        }
//        UtilTool.LOGD(TAG,"maxFileSize " + maxFileSize);
//        try {
//            mMediaRecorder.setMaxFileSize(maxFileSize);
//        } catch (RuntimeException exception) {
//
//        }
        if(mPoliceMode.getOrientation() == 180){
            isFrist180 = true;
        }else{
            isFrist180 = false;
        }
        UtilTool.LOGD(TAG,"isFrist180 == " + isFrist180);
        mMediaRecorder.setOrientationHint(mPoliceMode.getOrientation());
    }
    
    private void startRecordSplitFile() {
    	//SystemProperties.set("persist.sys.record.split.fd", "1");
    	
    	generateVideoFilename(mProfile.fileFormat);
    	SystemProperties.set("persist.sys.record.split.fd",mVideoFilename);
    	
//    	try {
//    		Thread.sleep(100);
//    		mMediaRecorder.setOutputFile(mVideoFilename);
//    		mMediaRecorder.prepare();
//    		UtilTool.LOGD(TAG,"create split file " + mVideoFilename);
//    	}
//    	catch (Exception exception) {
//    		UtilTool.LOGE(TAG,"create split file error: " + exception);
//    	}
    }

    private void generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        // Used when emailing.
        if("on".equals(mPoliceMode.getPreferenceWarper().getSwitchsAES())){
            title = title + "_Enc";
        }
        mVideoTitle = title;
        String filename = title + convertOutputFormatToFileExt(outputFileFormat);
        String mime = convertOutputFormatToMimeType(outputFileFormat);
        String path = null;
        if (Storage.isSaveSDCard() && SDCard.instance().isWriteable()) {
            path = SDCard.instance().getVideoDirectory() + '/' + filename;
        } else {
            path = Storage.DIRECTORY + '/' + filename;
        }
        File file = new File(Storage.getFileVideoParentPath());
        if(!file.exists()){
            file.mkdirs();
        }
        mCurrentVideoValues = new ContentValues(9);
        mCurrentVideoValues.put(Video.Media.TITLE, title);
        mCurrentVideoValues.put(Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(MediaStore.MediaColumns.DATE_MODIFIED, dateTaken / 1000);
        mCurrentVideoValues.put(Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(Video.Media.DATA, path);
        mCurrentVideoValues.put(Video.Media.RESOLUTION,
                Integer.toString(mProfile.videoFrameWidth) + "x" +
                        Integer.toString(mProfile.videoFrameHeight));
//        Location loc = mLocationManager.getCurrentLocation();
//        if (loc != null) {
//            mCurrentVideoValues.put(Video.Media.LATITUDE, loc.getLatitude());
//            mCurrentVideoValues.put(Video.Media.LONGITUDE, loc.getLongitude());
//        }
        mVideoFilename = path;
        UtilTool.LOGD(TAG, "New video filename: " + mVideoFilename);
    }

    private void saveVideo() {
        UtilTool.LOGD(TAG, "saveVideo");
        File origFile = new File(mCurrentVideoFilename);
        if (origFile.length() <= 0){
            origFile.delete();
            Settings.System.putString(mPoliceMode.getContext().getContentResolver(),PoliceMode.KEY_RECENT_VIDEO,mPreVideoFilename);
        }
        if (!origFile.exists() || origFile.length() <= 0) {
            UtilTool.LOGE(TAG, "Invalid file");
            mCurrentVideoValues = null;
            return;
        }

        mDuration = 0L;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(mCurrentVideoFilename);
            mDuration = Long.valueOf(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (IllegalArgumentException e) {
            UtilTool.LOGE(TAG, "cannot access the file:", e);
        }
        retriever.release();

        new Thread(new Runnable() {
            @Override
            public void run() {
            	if(mCurrentVideoValues != null) {
	                addVideo(mCurrentVideoFilename,
	                        mDuration, mCurrentVideoValues,
	                        mOnVideoSavedListener, mContentResolver);
            	}
                mCurrentVideoValues = null;
            }
        }).start();
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(mPoliceMode.Id2String(R.string.video_file_name_format));
        return dateFormat.format(date);
    }

    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }

    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".3gp";
    }

    public boolean startVideoRecorder(){
    	splitTime = System.currentTimeMillis();
        if(mIsVoiceRecording){
            mPoliceMode.startVideoCallBack(false);
            return false;
        }
        if(Storage.getAvailableSpace() < Storage.LOW_STORAGE_VIDEO_BYTES){
            mIsVideoRecording = false;
            mPoliceMode.startVideoCallBack(false);
            releaseMediaRecorder();
            UtilTool.tostShow(mPoliceMode.getContext(),R.string.memory_low_300m);
            return false;
        }
        mPoliceMode.systemSettingBeforeRecord();
        initProfile();
        uploadPreferences();
        videoWidth = mProfile.videoFrameWidth;
        videoHeight = mProfile.videoFrameHeight;
        int hsr = mPoliceMode.getPreferenceWarper().getFPS();
        isHSR = hsr == 60;
        UtilTool.LOGD(TAG,"hsr == " + hsr);
        captureRate = isHSR ? hsr : 0;
        if (isHSR){
            if (!isSessionSupportedByEncoder(videoWidth,videoHeight, captureRate)){
                Toast.makeText(mPoliceMode.getContext(),"Unsupported HSR and video size combinations",Toast.LENGTH_LONG).show();
                return false;
            }
        }
        initVideoMedia();
        if(mMediaRecorder == null){
            UtilTool.LOGE(TAG,"mMediaRecorder is null");
            mPoliceMode.startVideoCallBack(false);
            return false;
        }
        try{
            mMediaRecorder.prepare();
            mMediaRecorder.setOnInfoListener(this);
            mMediaRecorder.setOnErrorListener(this);
            mMediaRecorder.start();
            playSound();
            if(mPoliceMode.getOrientation() == 180){
                UtilTool.LOGE(TAG,"getOrientation :" + mPoliceMode.getOrientation());
                Parameters mparameters = mPoliceMode.getCamera().getParameters();
                if(!mparameters.get("video-flip").equals("flip-h")){
                    mparameters.set("video-flip", "flip-h");
                    mPoliceMode.getCamera().setParameters(mparameters);
                }
            }else{
                UtilTool.LOGE(TAG,"getOrientation 00000:" + mPoliceMode.getOrientation());
               Parameters mparameters = mPoliceMode.getCamera().getParameters();
                if(!mparameters.get("video-flip").equals("off")){
                    mparameters.set("video-flip", "off");
                    mPoliceMode.getCamera().setParameters(mparameters);
                } 
            }
            mIsVideoRecording = true;
            mPoliceMode.startVideoCallBack(true);
            mLedManager.setCustomFlashing(8,true);
            mPoliceMode.writeRecordStart("Record Start",mVideoFilename,mVideoTitle);
            Settings.System.putString(mPoliceMode.getContext().getContentResolver(),PoliceMode.KEY_START_RECENT_VIDEO,mVideoFilename);
        }catch(Exception e){
            releaseMediaRecorder();
            UtilTool.LOGE(TAG,"start video recorder failed :",e);
            mIsVideoRecording = false;
            mPoliceMode.startVideoCallBack(false);
            return false;
        }
        return true;
    }

    private void playSound() {
        if (!mPoliceMode.isPreRecordMiddle()){
            if (mPoliceMode.isPreRecording()){
                mPoliceMode.soundPlay(2);
            }else {
                mPoliceMode.soundPlay(1);
            }
        }
    }

    public boolean stopVideoRecorder(){
        UtilTool.LOGE(TAG,"stopRecorder video recorder");
        mLedManager.setCustomFlashing(8,false);
        if(mMediaRecorder == null || mIsVoiceRecording){
            mIsVideoRecording = false;
            mPoliceMode.startVideoCallBack(false);
            mPoliceMode.setSpCameraStatus();
            return false;
        }
        try{
            UtilTool.LOGE(TAG,"stopRecorder video 2222 recorder");
            //Parameters mparameters = mPoliceMode.getCamera().getParameters();
            /*if(!mparameters.get("video-flip").equals("off")){
                mparameters.set("video-flip", "off");
                mPoliceMode.getCamera().setParameters(mparameters);
            }*/
            stopPreRecord();
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.stop();
            stopRecordStatus();
            mIsVideoRecording = false;
            mPoliceMode.getCamera().lock();
            mPoliceMode.startVideoCallBack(false);
        }catch(Exception e){
            UtilTool.LOGE(TAG,"stopRecorder video recorder failed :",e);
            mIsVideoRecording = false;
            stopPreRecord();
            stopRecordStatus();
            releaseMediaRecorder();
            mPoliceMode.getCamera().lock();
            mPoliceMode.startVideoCallBack(false);
            mPoliceMode.setSpCameraStatus();
            return false;
        }
        releaseMediaRecorder();
        mCurrentVideoFilename = mVideoFilename;
        saveVideo();
        renameFile();
        mPoliceMode.writeRecordStart("Record Stop",mVideoFilename,mVideoTitle);
        Settings.System.putString(mPoliceMode.getContext().getContentResolver(),PoliceMode.KEY_RECENT_VIDEO,mVideoFilename);
        deleteFile();
        mPreVideoFilename = mVideoFilename;
        mPoliceMode.setSpCameraStatus();
        return true;
    }

    private void stopPreRecord() {
        if (mPoliceMode.isPreRecordMiddle()) {
            if (mPoliceMode.isPreRecording()) {
                UtilTool.LOGE(TAG,"stopPreRecord - persist");
                SystemProperties.set("persist.sys.precord.start", "0");
                SystemProperties.set("persist.sys.precord.config", "0");
            }
        }else {
            if (mPoliceMode.isPreRecording()) {
                SystemProperties.set("persist.sys.precord.start", "0");
                SystemProperties.set("persist.sys.precord.config", "0");
            }
        }
    }

    private void stopRecordStatus() {
        if (mPoliceMode.isPreRecordMiddle()) {
            if (mPoliceMode.isPreRecording()) {
//                SystemProperties.set("persist.sys.precord.start", "0");
//                SystemProperties.set("persist.sys.precord.config", "0");
                mPoliceMode.setPreRecord(false);
                mPoliceMode.setPreRecordMiddle(false);
                if (mPoliceMode.isLaterRecord()){
                    mPoliceMode.setLaterRecord(false);
                }else {
                    mPoliceMode.soundPlay(3);
                }
            }
        } else{
            if (mPoliceMode.isPreRecording()) {
                mPoliceMode.soundPlay(2);
//                SystemProperties.set("persist.sys.precord.start", "0");
//                SystemProperties.set("persist.sys.precord.config", "0");
                mPoliceMode.setPreRecord(false);
            } else {
                    if (mPoliceMode.isLaterRecord()){
                        mPoliceMode.setLaterRecord(false);
                    }else {
                        mPoliceMode.soundPlay(3);
                    }
                }
            }
    }

    private void deleteFile(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(needDelete()){
                    Storage.deleteFile();
                    Settings.System.putString(mPoliceMode.getContext().getContentResolver(),PoliceMode.KEY_RECENT_VIDEO,mPreVideoFilename);
                }
            }
        }).start();
    }

    private void renameFile(){
        if(mIsMarker){
            mIsMarker = false;
            String newName = Storage.renameFile(mVideoFilename);
            String newTitle = Storage.renameTitle(mVideoTitle);
            mVideoFilename = newName;
            mVideoTitle = newTitle;
            mPoliceMode.writeImportantVideoFile("Important Video File",mVideoFilename,mVideoTitle);
            UtilTool.LOGD(TAG,"mVideoFilename = " + mVideoFilename);
        }
    }

    private boolean needDelete(){
        if(Storage.getAvailableSpace() <= Storage.LOW_STORAGE_VIDEO_NEEDDELETE){
            return true;
        }
        return false;
    }

    public void releaseMediaRecorder(){
        UtilTool.LOGD(TAG, "Releasing media recorder.");
        mPoliceMode.startVideoCallBack(false);
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            isFrist180 = false;
        }
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                UtilTool.LOGD(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    public void addVideo(String path, long duration, ContentValues values,
                         OnMediaSavedListener l, ContentResolver resolver) {
        // We don't set a queue limit for video saving because the file
        // is already in the storage. Only updating the database.
        UtilTool.LOGD(TAG,"addVideo");
        new VideoSaveTask(path, duration, values, l, resolver).execute();
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
    	UtilTool.LOGD(TAG,"onInfo what="+what+",extra="+extra);
        //releaseMediaRecorder();
    	if(what == 800) {
    		if(System.currentTimeMillis() - splitTime < 60000) {
    			UtilTool.LOGE(TAG,"onInfo time is not enough :="+(System.currentTimeMillis() - splitTime));
    			return;
    		}
    		splitTime = System.currentTimeMillis();
	        mCurrentVideoFilename = mVideoFilename;
	        saveVideo();
	        renameFile();
	        Settings.System.putString(mPoliceMode.getContext().getContentResolver(),PoliceMode.KEY_RECENT_VIDEO,mVideoFilename);
	        deleteFile();
	        startRecordSplitFile();
    	}
    	else {
    		deleteFile();
    	}
        
    }
    
     @Override
     public void onError(MediaRecorder mr, int what, int extra) {
    	 UtilTool.LOGE(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
         
//         if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
//             // We may have run out of space on the sdcard.
//        	 releaseMediaRecorder();
//        	 
//        	 new Handler().postDelayed(new Runnable() {
//	             @Override
//	             public void run() {
//	                 startVideoRecorder();
//	             	}
//        	 }, 1000);
//         }
     }

    public class VideoSaveTask extends AsyncTask<Void, Void, Uri> {
        private String path;
        private long duration;
        private ContentValues values;
        private OnMediaSavedListener listener;
        private ContentResolver resolver;

        public VideoSaveTask(String path, long duration, ContentValues values,
                             OnMediaSavedListener l, ContentResolver r) {
            this.path = path;
            this.duration = duration;
            this.values = new ContentValues(values);
            this.listener = l;
            this.resolver = r;
        }

        @Override
        protected Uri doInBackground(Void... v) {
            values.put(MediaStore.Video.Media.SIZE, new File(path).length());
            values.put(MediaStore.Video.Media.DURATION, duration);
            Uri uri = null;
            try {
                Uri videoTable = Uri.parse(Storage.VIDEO_BASE_URI);
                uri = resolver.insert(videoTable, values);

                // Rename the video file to the final name. This avoids other
                // apps reading incomplete data.  We need to do it after we are
                // certain that the previous insert to MediaProvider is completed.
                String finalName = values.getAsString(
                        MediaStore.Video.Media.DATA);
                if (new File(path).renameTo(new File(finalName))) {
                    path = finalName;
                }

                resolver.update(uri, values, null, null);
            } catch (Exception e) {
                // We failed to insert into the database. This can happen if
                // the SD card is unmounted.
                UtilTool.LOGE(TAG, "failed to add video to media store:" , e);
                uri = null;
            } finally {
                UtilTool.LOGD(TAG, "Current video URI: " + uri);
            }
            return uri;
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (listener != null) listener.onMediaSaved(uri);
        }
    }

    public interface OnMediaSavedListener {
        public void onMediaSaved(Uri uri);
    }

    static class DefaultHashMap<K, V> extends HashMap<K, V> {
        private V mDefaultValue;

        public void putDefault(V defaultValue) {
            mDefaultValue = defaultValue;
        }

        @Override
        public V get(Object key) {
            V value = super.get(key);
            return (value == null) ? mDefaultValue : value;
        }
        public K getKey(V toCheck) {
            Iterator<K> it = this.keySet().iterator();
            V val;
            K key;
            while(it.hasNext()) {
                key = it.next();
                val = this.get(key);
                if (val.equals(toCheck)) {
                    return key;
                }
            }
            return null;
        }
    }

}
