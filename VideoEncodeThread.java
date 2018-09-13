package com.meige.policerecord.videorecord;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

import com.meige.policerecord.videorecord.PoliceMode;
import com.meige.policerecord.UtilTool;

public class VideoEncodeThread extends Thread {
    private static final String TAG = "VideoEncodeThread";
    private static final boolean VERBOSE = false; // lots of logging
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 20; // 15fps
    private static final int IFRAME_INTERVAL = 2; // 10 between
    // I-frames
    private static final int TIMEOUT_USEC = 10000;
    private static final int COMPRESS_RATIO = 256;
    private static final int BIT_RATE = PoliceMode.PreivewHight * PoliceMode.PreivewHight * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.
    public static boolean DEBUG = true;
    private final Object lock = new Object();
    byte[] mFrameData;
    Vector<byte[]> frameBytes;
    private int mWidth;
    private int mHeight;
    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mColorFormat;
    private long mStartTime = 0;
    private volatile boolean isExit = false;
    private MediaFormat mediaFormat;
    private MediaCodecInfo codecInfo;
    private volatile boolean isStart = false;
    private WeakReference<MediaSendThread> mediaSendThread;
    private byte[] spspps = null;
    private byte[] encodeOutputData = null;

    public VideoEncodeThread(int mWidth, int mHeight,WeakReference<MediaSendThread> MediaSendThread) {
        this.mWidth = mWidth;
        this.mHeight = mHeight;
        this.mediaSendThread = MediaSendThread;
        frameBytes = new Vector<byte[]>();
        encodeOutputData = new byte[mWidth*mHeight*3/2];
        prepare();
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo,
                                         String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        UtilTool.LOGD(TAG,
                "couldn't find a good color format for " + codecInfo.getName()
                        + " / " + mimeType);
        return 0; // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            	UtilTool.LOGE(TAG,"COLOR_FormatYUV420Planar");
            	return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                UtilTool.LOGE(TAG,"COLOR_FormatYUV420PackedPlanar");
            	return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                UtilTool.LOGE(TAG,"COLOR_FormatYUV420SemiPlanar");
            	return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                UtilTool.LOGE(TAG,"COLOR_FormatYUV420PackedSemiPlanar");
            	return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                UtilTool.LOGE(TAG,"COLOR_TI_FormatYUV420PackedSemiPlanar");
            	return true;
            default:
                return false;
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes,
                                             int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }

    public void exit() {
        isExit = true;
        synchronized (lock) {
            lock.notify();
        }
    }

    public void add(byte[] data) {
        if (frameBytes != null) {
            frameBytes.add(data);
            synchronized (lock) {
                lock.notify();
            }
        }
    }

    private void prepare() {
        UtilTool.LOGD(TAG, "VideoEncoder()");
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
        mBufferInfo = new MediaCodec.BufferInfo();
        codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            UtilTool.LOGD(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        UtilTool.LOGD(TAG, "found codec: " + codecInfo.getName());
        mColorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        UtilTool.LOGD(TAG, "found colorFormat: " + mColorFormat);
        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
                this.mWidth, this.mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        UtilTool.LOGD(TAG, "format: " + mediaFormat);
    }

    private void startMediaCodec() throws IOException {
        mMediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        mMediaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        isStart = true;
    }

    private void stopMediaCodec() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        isStart = false;
        UtilTool.LOGD(TAG, "stop video record...");
    }

    public synchronized void restart() {
        isStart = false;
        frameBytes.clear();
    }

    private void encodeFrame(byte[] input/* , byte[] output */) {
    	if(VERBOSE) {
    		UtilTool.LOGD(TAG, "encodeFrame()");
    	}
        NV21toI420SemiPlanar(input, mFrameData, this.mWidth, this.mHeight);
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if(VERBOSE) {
        	UtilTool.LOGD(TAG, "inputBufferIndex-->" + inputBufferIndex);
        }
        if (inputBufferIndex >= 0) {
            long endTime = System.nanoTime();
            long ptsUsec = (endTime - mStartTime) / 1000;
            if(VERBOSE) {
            	UtilTool.LOGD(TAG, "resentationTime: " + ptsUsec);
            }
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(mFrameData);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0,
            		(mFrameData.length), System.nanoTime() / 1000, 0);
        } else {
            // either all in use, or we timed out during initial setup
            UtilTool.LOGD(TAG, "input buffer not available");
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        UtilTool.LOGD(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                UtilTool.LOGD(TAG, "add video INFO_OUTPUT_FORMAT_CHANGED " + newFormat.toString());
            } else if (outputBufferIndex < 0) {
            } else {
            	if(VERBOSE) { 
            		UtilTool.LOGD(TAG, "perform encoding");
            	}
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    UtilTool.LOGD(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG size="+mBufferInfo.size);
                    //mBufferInfo.size = 0;
                    
                    spspps = new byte[mBufferInfo.size];
    	            outputBuffer.get(spspps);
    	            
    	            
                }
                if (mBufferInfo.size != 0) {
                    
                	MediaSendThread mediaSendThread = this.mediaSendThread.get();
                	
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    if (mediaSendThread != null) {
                    	if(VERBOSE) {
                    		UtilTool.LOGD(TAG, "add video data " + mBufferInfo.size);
                    	}
                        byte[] outData = new byte[mBufferInfo.size];
        	            outputBuffer.get(outData);
        	            
        	            if(outData[4] == 0x65) {
        	            	System.arraycopy(spspps, 0, encodeOutputData, 0, spspps.length);
        	            	System.arraycopy(outData, 0, encodeOutputData, spspps.length, outData.length);
        	            	
                        mediaSendThread.addMuxerData(new MediaSendThread.MuxerData(
                        		MediaSendThread.TRACK_VIDEO, encodeOutputData, spspps.length+outData.length
                        ));
        	            }
        	            else {
        	            	mediaSendThread.addMuxerData(new MediaSendThread.MuxerData(
                            		MediaSendThread.TRACK_VIDEO, outData, outData.length
                            ));
        	            }
                    }
                    if(VERBOSE) {
                    	UtilTool.LOGD(TAG, "sent " + mBufferInfo.size + " frameBytes to sendThread");
                    }
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        } while (outputBufferIndex >= 0);
    }

    @Override
    public void run() {
        while (!isExit) {
            if (!isStart) {
                stopMediaCodec();
                try {
                    UtilTool.LOGD(TAG, "video -- startMediaCodec...");
                    startMediaCodec();
                } catch (IOException e) {
                    isStart = false;
                }
            } else if (!frameBytes.isEmpty()) {
                byte[] bytes = this.frameBytes.remove(0);
                try {
                    encodeFrame(bytes);
                } catch (Exception e) {
                    UtilTool.LOGE(TAG, "ecode Video data faile",e);
                    e.printStackTrace();
                }
            } else if (frameBytes.isEmpty()) {
                synchronized (lock) {
                    try {
                    	if(VERBOSE) {
                    		UtilTool.LOGD(TAG, "video -- lock.wait() data...");
                    	}
                        lock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        UtilTool.LOGD(TAG, "Video recording exit...");
    }
}
