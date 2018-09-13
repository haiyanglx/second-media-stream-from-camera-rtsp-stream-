LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := liblive555

APP_ABI := arm64-v8a

TARGET_ARCH_ABI := arm64

#LOCAL_ARM_MODE := arm
#LOCAL_PRELINK_MODULE := false
LOCAL_MULTILIB := 64

LOCAL_LDLIBS    := -lm -llog -lcutils

#cpp flags?
LOCAL_CPPFLAGS := \
-DNULL=0 -DSOCKLEN_T=socklen_t -DNO_SSTREAM -DBSD=1 -DNO_SSTREAM -fexceptions -DANDROID -DXLOCALE_NOT_USED -fPIC -frtti -D_FILE_OFFSET_BITS=64 -m64 -O2

STL_PATH = $(LOCAL_PATH)/../../../../prebuilts/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a
#STL_PATH=$(NDK_ROOT)/sources/cxx-stl/gnu-libstdc++/4.6/libs/armeabi-v7a
LOCAL_LDLIBS += -L$(STL_PATH) -lsupc++

#LOCAL_LDFLAGS += $(LOCAL_PATH)/../../../../../prebuilts/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9/libs/armeabi-v7a/libsupc++.a \
#	$(LOCAL_PATH)/../../../../../prebuilts/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9/libs/armeabi-v7a/libgnustl_static.a

#include header
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/live/BasicUsageEnvironment \
	$(LOCAL_PATH)/live/BasicUsageEnvironment/include \
	$(LOCAL_PATH)/live/groupsock \
	$(LOCAL_PATH)/live/groupsock/include \
	$(LOCAL_PATH)/live/liveMedia \
	$(LOCAL_PATH)/live/liveMedia/include \
	$(LOCAL_PATH)/live/UsageEnvironment \
	$(LOCAL_PATH)/live/UsageEnvironment/include \
	$(LOCAL_PATH)/live/mediaServer \

LOCAL_MODULE_TAGS := optional

#build the needed source file
LOCAL_SRC_FILES :=\
./com_meige_rtspservice_rtsp_RTSPService.cpp \
./H264DeviceSource.cpp \
./H264VideoDeviceServerMediaSubsession.cpp \
./live/groupsock/GroupEId.cpp \
./live/groupsock/IOHandlers.cpp \
./live/groupsock/GroupsockHelper.cpp \
./live/groupsock/inet.c \
./live/groupsock/NetAddress.cpp \
./live/groupsock/NetInterface.cpp \
./live/groupsock/Groupsock.cpp \
./live/UsageEnvironment/UsageEnvironment.cpp \
./live/UsageEnvironment/strDup.cpp \
./live/UsageEnvironment/HashTable.cpp \
./live/mediaServer/live555MediaServer.cpp \
./live/mediaServer/DynamicRTSPServer.cpp \
./live/BasicUsageEnvironment/BasicTaskScheduler0.cpp \
./live/BasicUsageEnvironment/BasicTaskScheduler.cpp \
./live/BasicUsageEnvironment/DelayQueue.cpp \
./live/BasicUsageEnvironment/BasicUsageEnvironment.cpp \
./live/BasicUsageEnvironment/BasicUsageEnvironment0.cpp \
./live/BasicUsageEnvironment/BasicHashTable.cpp \
./live/liveMedia/WAVAudioFileSource.cpp \
./live/liveMedia/BasicUDPSource.cpp \
./live/liveMedia/InputFile.cpp \
./live/liveMedia/MatroskaFileParser.cpp \
./live/liveMedia/TheoraVideoRTPSource.cpp \
./live/liveMedia/H264VideoStreamDiscreteFramer.cpp \
./live/liveMedia/H263plusVideoRTPSource.cpp \
./live/liveMedia/H263plusVideoFileServerMediaSubsession.cpp \
./live/liveMedia/VideoRTPSink.cpp \
./live/liveMedia/MultiFramedRTPSink.cpp \
./live/liveMedia/FramedFilter.cpp \
./live/liveMedia/MPEG1or2FileServerDemux.cpp \
./live/liveMedia/H263plusVideoRTPSink.cpp \
./live/liveMedia/MP3ADU.cpp \
./live/liveMedia/AMRAudioFileSource.cpp \
./live/liveMedia/RTSPServer.cpp \
./live/liveMedia/AVIFileSink.cpp \
./live/liveMedia/SimpleRTPSource.cpp \
./live/liveMedia/JPEGVideoRTPSource.cpp \
./live/liveMedia/StreamParser.cpp \
./live/liveMedia/OggFile.cpp \
./live/liveMedia/T140TextRTPSink.cpp \
./live/liveMedia/OggFileParser.cpp \
./live/liveMedia/RTPSink.cpp \
./live/liveMedia/MPEGVideoStreamParser.cpp \
./live/liveMedia/RTSPCommon.cpp \
./live/liveMedia/RTPInterface.cpp \
./live/liveMedia/MP3FileSource.cpp \
./live/liveMedia/H265VideoStreamDiscreteFramer.cpp \
./live/liveMedia/MPEG4VideoStreamFramer.cpp \
./live/liveMedia/GenericMediaServer.cpp \
./live/liveMedia/VorbisAudioRTPSink.cpp \
./live/liveMedia/H264VideoRTPSink.cpp \
./live/liveMedia/MPEG1or2VideoStreamDiscreteFramer.cpp \
./live/liveMedia/MultiFramedRTPSource.cpp \
./live/liveMedia/MPEG1or2VideoRTPSink.cpp \
./live/liveMedia/AC3AudioRTPSource.cpp \
./live/liveMedia/H261VideoRTPSource.cpp \
./live/liveMedia/MP3StreamState.cpp \
./live/liveMedia/VP8VideoRTPSink.cpp \
./live/liveMedia/rtcp_from_spec.c \
./live/liveMedia/OggFileSink.cpp \
./live/liveMedia/uLawAudioFilter.cpp \
./live/liveMedia/ServerMediaSession.cpp \
./live/liveMedia/QuickTimeFileSink.cpp \
./live/liveMedia/MPEG2TransportStreamFramer.cpp \
./live/liveMedia/JPEGVideoRTPSink.cpp \
./live/liveMedia/H265VideoStreamFramer.cpp \
./live/liveMedia/MatroskaFileServerMediaSubsession.cpp \
./live/liveMedia/H263plusVideoStreamFramer.cpp \
./live/liveMedia/OggFileServerMediaSubsession.cpp \
./live/liveMedia/MediaSession.cpp \
./live/liveMedia/H264or5VideoStreamFramer.cpp \
./live/liveMedia/SIPClient.cpp \
./live/liveMedia/MPEG2TransportStreamTrickModeFilter.cpp \
./live/liveMedia/Media.cpp \
./live/liveMedia/DVVideoRTPSink.cpp \
./live/liveMedia/MediaSink.cpp \
./live/liveMedia/DVVideoFileServerMediaSubsession.cpp \
./live/liveMedia/MPEG4VideoStreamDiscreteFramer.cpp \
./live/liveMedia/OnDemandServerMediaSubsession.cpp \
./live/liveMedia/ADTSAudioFileServerMediaSubsession.cpp \
./live/liveMedia/QuickTimeGenericRTPSource.cpp \
./live/liveMedia/BasicUDPSink.cpp \
./live/liveMedia/Locale.cpp \
./live/liveMedia/FramedSource.cpp \
./live/liveMedia/WAVAudioFileServerMediaSubsession.cpp \
./live/liveMedia/TCPStreamSink.cpp \
./live/liveMedia/MP3ADURTPSource.cpp \
./live/liveMedia/AC3AudioStreamFramer.cpp \
./live/liveMedia/MPEG4VideoFileServerMediaSubsession.cpp \
./live/liveMedia/MatroskaDemuxedTrack.cpp \
./live/liveMedia/FileSink.cpp \
./live/liveMedia/MP3ADUTranscoder.cpp \
./live/liveMedia/RTSPRegisterSender.cpp \
./live/liveMedia/AMRAudioRTPSink.cpp \
./live/liveMedia/H264VideoStreamFramer.cpp \
./live/liveMedia/AC3AudioRTPSink.cpp \
./live/liveMedia/MPEG1or2VideoStreamFramer.cpp \
./live/liveMedia/MPEG2TransportStreamFromESSource.cpp \
./live/liveMedia/AudioRTPSink.cpp \
./live/liveMedia/VP9VideoRTPSink.cpp \
./live/liveMedia/MPEG1or2VideoRTPSource.cpp \
./live/liveMedia/MPEG4GenericRTPSource.cpp \
./live/liveMedia/H265VideoRTPSource.cpp \
./live/liveMedia/H263plusVideoStreamParser.cpp \
./live/liveMedia/MP3InternalsHuffmanTable.cpp \
./live/liveMedia/MediaSource.cpp \
./live/liveMedia/MPEG1or2AudioRTPSource.cpp \
./live/liveMedia/VorbisAudioRTPSource.cpp \
./live/liveMedia/MPEG1or2Demux.cpp \
./live/liveMedia/MPEG1or2DemuxedServerMediaSubsession.cpp \
./live/liveMedia/TextRTPSink.cpp \
./live/liveMedia/AC3AudioFileServerMediaSubsession.cpp \
./live/liveMedia/MPEG4LATMAudioRTPSource.cpp \
./live/liveMedia/GSMAudioRTPSink.cpp \
./live/liveMedia/MPEGVideoStreamFramer.cpp \
./live/liveMedia/StreamReplicator.cpp \
./live/liveMedia/MP3Internals.cpp \
./live/liveMedia/DVVideoStreamFramer.cpp \
./live/liveMedia/MPEG2TransportUDPServerMediaSubsession.cpp \
./live/liveMedia/MP3ADURTPSink.cpp \
./live/liveMedia/MatroskaFileServerDemux.cpp \
./live/liveMedia/RTSPClient.cpp \
./live/liveMedia/JPEGVideoSource.cpp \
./live/liveMedia/ourMD5.cpp \
./live/liveMedia/QCELPAudioRTPSource.cpp \
./live/liveMedia/MPEG2TransportStreamMultiplexor.cpp \
./live/liveMedia/H264VideoFileServerMediaSubsession.cpp \
./live/liveMedia/Base64.cpp \
./live/liveMedia/PassiveServerMediaSubsession.cpp \
./live/liveMedia/MP3InternalsHuffman.cpp \
./live/liveMedia/FileServerMediaSubsession.cpp \
./live/liveMedia/H264or5VideoRTPSink.cpp \
./live/liveMedia/AMRAudioFileServerMediaSubsession.cpp \
./live/liveMedia/OutputFile.cpp \
./live/liveMedia/MPEG2TransportStreamAccumulator.cpp \
./live/liveMedia/BitVector.cpp \
./live/liveMedia/H265VideoRTPSink.cpp \
./live/liveMedia/ByteStreamMultiFileSource.cpp \
./live/liveMedia/MPEG2TransportStreamIndexFile.cpp \
./live/liveMedia/MPEG2TransportFileServerMediaSubsession.cpp \
./live/liveMedia/AMRAudioRTPSource.cpp \
./live/liveMedia/VP9VideoRTPSource.cpp \
./live/liveMedia/RTCP.cpp \
./live/liveMedia/MPEG4ESVideoRTPSource.cpp \
./live/liveMedia/TheoraVideoRTPSink.cpp \
./live/liveMedia/MPEG1or2AudioRTPSink.cpp \
./live/liveMedia/SimpleRTPSink.cpp \
./live/liveMedia/MPEG4GenericRTPSink.cpp \
./live/liveMedia/ADTSAudioFileSource.cpp \
./live/liveMedia/DVVideoRTPSource.cpp \
./live/liveMedia/RTSPServerSupportingHTTPStreaming.cpp \
./live/liveMedia/VP8VideoRTPSource.cpp \
./live/liveMedia/MP3ADUdescriptor.cpp \
./live/liveMedia/AMRAudioSource.cpp \
./live/liveMedia/MPEG2TransportStreamFromPESSource.cpp \
./live/liveMedia/RTSPServerRegister.cpp \
./live/liveMedia/MP3AudioFileServerMediaSubsession.cpp \
./live/liveMedia/AMRAudioFileSink.cpp \
./live/liveMedia/MP3ADUinterleaving.cpp \
./live/liveMedia/ByteStreamFileSource.cpp \
./live/liveMedia/MPEG4ESVideoRTPSink.cpp \
./live/liveMedia/MP3AudioMatroskaFileServerMediaSubsession.cpp \
./live/liveMedia/MPEG1or2DemuxedElementaryStream.cpp \
./live/liveMedia/EBMLNumber.cpp \
./live/liveMedia/ProxyServerMediaSession.cpp \
./live/liveMedia/MPEG1or2VideoFileServerMediaSubsession.cpp \
./live/liveMedia/OggDemuxedTrack.cpp \
./live/liveMedia/MatroskaFile.cpp \
./live/liveMedia/AudioInputDevice.cpp \
./live/liveMedia/H265VideoFileServerMediaSubsession.cpp \
./live/liveMedia/H264or5VideoFileSink.cpp \
./live/liveMedia/FramedFileSource.cpp \
./live/liveMedia/H264VideoRTPSource.cpp \
./live/liveMedia/DeviceSource.cpp \
./live/liveMedia/H265VideoFileSink.cpp \
./live/liveMedia/DigestAuthentication.cpp \
./live/liveMedia/MPEG1or2AudioStreamFramer.cpp \
./live/liveMedia/RTPSource.cpp \
./live/liveMedia/H264VideoFileSink.cpp \
./live/liveMedia/MPEG4LATMAudioRTPSink.cpp \
./live/liveMedia/ByteStreamMemoryBufferSource.cpp \
./live/liveMedia/OggFileServerDemux.cpp \
./live/liveMedia/MPEG2IndexFromTransportStream.cpp \
./live/liveMedia/MP3Transcoder.cpp \
./live/liveMedia/H264or5VideoStreamDiscreteFramer.cpp

include $(BUILD_SHARED_LIBRARY)
