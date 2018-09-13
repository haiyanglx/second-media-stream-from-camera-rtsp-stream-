package com.meige.policerecord.videorecord;

import android.hardware.Camera;

import com.meige.policerecord.videorecord.PoliceMode;
import com.meige.policerecord.UtilTool;
import com.meige.policerecord.setting.ComboPreferences;

/**
 * Created by MEIG-PC003 on 2018/5/3.
 */

public class VideoSecondStreamWarapper {
    public static final String TAG = "VideoSecondStreamWarapper";

    private static VideoSecondStreamWarapper mVideoSecondStreamWarapper;
    private PoliceMode mPoliceMode;
    

    public VideoSecondStreamWarapper(){
    }

    public static VideoSecondStreamWarapper getInstance() {
        if (mVideoSecondStreamWarapper == null) {
            synchronized (VideoSecondStreamWarapper.class) {
                if (mVideoSecondStreamWarapper == null) {
                	mVideoSecondStreamWarapper = new VideoSecondStreamWarapper();
                }
            }
        }
        return mVideoSecondStreamWarapper;
    }

    public void setPoliceMode(PoliceMode policeMode){
        mPoliceMode = policeMode;
    }
    
    public void onStart() {
    	MediaSendThread.getInstance().startMediaThread();
    }
    
    public void onStop() {
    	MediaSendThread.getInstance().stopMediaThread();
    }


    public void onPreviewFrame(byte[] data, Camera camera) {
    	MediaSendThread.getInstance().addVideoFrameData(data);
    	
        camera.addCallbackBuffer(data);
    }

    public void onDestory(){
    	mVideoSecondStreamWarapper = null;
    	MediaSendThread.getInstance().stopMediaThread();
    }
}
