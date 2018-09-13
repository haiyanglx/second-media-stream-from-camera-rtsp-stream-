package com.meige.policerecord.videorecord;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.meige.policerecord.videorecord.PoliceMode;

import com.meige.policerecord.UtilTool;
import com.meige.policerecord.storage.FileSwapHelper;
import com.meige.policerecord.storage.Storage;

public class MediaSendThread extends Thread {
    private static final String TAG = "MediaSendThread";

    public static final int TRACK_VIDEO = 0;
    public static final int TRACK_AUDIO = 1;
    public static boolean DEBUG = false;
    private static MediaSendThread mMediaSendThread = null;
    private final Object lock = new Object();
    
    private Vector<MuxerData> muxerDatas;
    private volatile boolean isExit = false;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private volatile boolean isVideoAdd;
    
    private VideoEncodeThread videoThread;
   
    private boolean isMediaThreadStart = false;
    private MediaFormat videoMediaFormat;
    
    private FileOutputStream file = null;
    private String filename = "/sdcard/camera.h264";
    private String localSocketName = "com.meige.localsocket";

    private LocalSocket mRtspSocket;
    
    private MediaSendThread() {
        
    }

    public static MediaSendThread getInstance(){
        if(mMediaSendThread == null){
        	mMediaSendThread = new MediaSendThread();
        }
        return mMediaSendThread;
    }

    

    public  boolean startMediaThread() {
    	if(DEBUG) {
    		try {
				file = new FileOutputStream(filename);
			} catch (FileNotFoundException e) {
				UtilTool.LOGD(TAG, "File open error"+e);
				e.printStackTrace();
			}
    	}
        if (mMediaSendThread != null) {
            synchronized (MediaSendThread.class) {
                if (mMediaSendThread != null) {
                	mMediaSendThread.start();
                }
                return true;
            }
        }else{
            return false;
        }
    }

    public  boolean stopMediaThread() {
    	if(DEBUG) {
    		try {
				file.flush();
				file.close();
			} catch (IOException e) {
				UtilTool.LOGD("luxun", "File close error");
				e.printStackTrace();
			}
    	}
        if (mMediaSendThread != null) {
        	mMediaSendThread.exit();
            try {
            	mMediaSendThread.join();
            } catch (InterruptedException e) {

            }
            mMediaSendThread = null;
            return true;
        }else{
            return false;
        }
    }

    public  void addVideoFrameData(byte[] data) {
        if (mMediaSendThread != null) {
        	mMediaSendThread.addVideoData(data);
        }
    }

    private void initMediaThread() {
    	muxerDatas = new Vector<MuxerData>();
        videoThread = new VideoEncodeThread(PoliceMode.PreivewWidth, PoliceMode.PreivewHight,new WeakReference<MediaSendThread>(this));
        videoThread.start();
        restartMediaThread();
        initLocalSocket();
    }

    private void addVideoData(byte[] data) {
        if (videoThread != null) {
            videoThread.add(data);
        }
    }

    private void restartMediaThread() {
        try {
            resetMediaThread();
            requestStart();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopMediaProcess() {
            isMediaThreadStart = false;
    }

    private void resetMediaThread() throws Exception {
    	stopMediaProcess();
    }

    private void exit() {
    	closeLocalSocket();
        if (videoThread != null) {
            videoThread.exit();
            try {
                videoThread.join();
            } catch (InterruptedException e) {

            }
        }
        
        isExit = true;
        synchronized (lock) {
            lock.notify();
        }
    }


    public void addMuxerData(MuxerData data) {
        if (muxerDatas == null) {
            return;
        }
        muxerDatas.add(data);
        synchronized (lock) {
            lock.notify();
        }
    }


    @Override
    public void run() {
    	initMediaThread();
        while (!isExit) {
            if (isMediaThreadStart) {
                //混合器开启后
                if (muxerDatas.isEmpty()) {
                    synchronized (lock) {
                        try {
                            UtilTool.LOGD(TAG, "wait...");
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    
                        MuxerData data = muxerDatas.remove(0);
                        int track;
                        if (data.trackIndex == TRACK_VIDEO) {
                            track = videoTrackIndex;
                        }
                        sendToLocalSocket(data.byteBuf, data.bufferLen);
                        
                        if(DEBUG) {
                        	
                        	try {
//                        		byte[] b = new byte[data.bufferInfo.size];  
//                        		data.byteBuf.get(b); 
                        		file.write(data.byteBuf, 0, data.bufferLen);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        UtilTool.LOGD(TAG, "write to writeSampleData" + data.bufferLen);
                }
            } else {
                //混合器未开启
                synchronized (lock) {
                    try {
                        UtilTool.LOGD(TAG, "lock.wait()...");
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        stopMediaProcess();
        UtilTool.LOGD(TAG, "exit MediaThread...");
    }

    private void requestStart() {
        synchronized (lock) {
        	isMediaThreadStart = true;
            UtilTool.LOGD(TAG, "requestStart mediaMuxer.start() add write date...");
            lock.notify();
            
        }
    }
    
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrackIndex {
    }


    /**
     * 封装需要传输的数据类型
     */
    public static class MuxerData {
        int trackIndex;
        byte[] byteBuf;
        int bufferLen;

        public MuxerData(@TrackIndex int trackIndex, byte[] byteBuf, int bufferLen) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferLen = bufferLen;
        }
    }

    
	private void initLocalSocket() {
		UtilTool.LOGD(TAG, "initLocalSocket");
		try {
			mRtspSocket = new LocalSocket();
			LocalSocketAddress lsa = new LocalSocketAddress(localSocketName,
					LocalSocketAddress.Namespace.ABSTRACT);
			UtilTool.LOGD(TAG, "to connect ...");
			mRtspSocket.connect(lsa);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void closeLocalSocket() {
		UtilTool.LOGD(TAG, "closeLocalSocket");
		try {
			if(mRtspSocket != null) {
				mRtspSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendToLocalSocket(byte[] data, int length) {
		if (mRtspSocket == null)
			return;
		UtilTool.LOGD(TAG, "sendToLocalSocket: length=" + length);
		try {
			OutputStream os = mRtspSocket.getOutputStream();
			os.write(data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
