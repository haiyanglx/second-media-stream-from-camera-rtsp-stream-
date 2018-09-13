
#include "com_meige_rtspservice_rtsp_RTSPService.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
// use local socket 
#include <sys/socket.h>
#include <sys/un.h>
#include <cutils/sockets.h>

#include "liveMedia.hh"
#include "BasicUsageEnvironment.hh"
#include <GroupsockHelper.hh>
#include "H264DeviceSource.hh"
#include "H264VideoDeviceServerMediaSubsession.hh"


#ifdef __cplusplus
extern "C" {
#endif

#define __TEST__

UsageEnvironment* env;

// To make the second and subsequent client for each stream reuse the same
// input stream as the first client (rather than playing the file from the
// start for each client), change the following "False" to "True":
Boolean reuseFirstSource = False;

// To stream *only* MPEG-1 or 2 video "I" frames
// (e.g., to reduce network bandwidth),
// change the following "False" to "True":
Boolean iFramesOnly = False;

static void announceStream(RTSPServer* rtspServer, ServerMediaSession* sms,
			   char const* streamName, char const* inputFileName); // fwd

static char newDemuxWatchVariable;

static MatroskaFileServerDemux* matroskaDemux;
static void onMatroskaDemuxCreation(MatroskaFileServerDemux* newDemux, void* /*clientData*/) {
  matroskaDemux = newDemux;
  newDemuxWatchVariable = 1;
}

static OggFileServerDemux* oggDemux;
static void onOggDemuxCreation(OggFileServerDemux* newDemux, void* /*clientData*/) {
  oggDemux = newDemux;
  newDemuxWatchVariable = 1;
}

static void announceStream(RTSPServer* rtspServer, ServerMediaSession* sms,
			   char const* streamName, char const* inputFileName) {
  LOGI("announceStream: streamName=%s; inputFileName=%s", streamName, inputFileName);
  char* url = rtspServer->rtspURL(sms);
  UsageEnvironment& env = rtspServer->envir();
  env << "\n\"" << streamName << "\" stream, from the file \""
      << inputFileName << "\"\n";
  env << "Play this stream using the URL \"" << url << "\"\n";
  delete[] url;
}
#ifdef __TEST__
#define SERVER_MEDIA_SESSION_MAX 8
RTSPServer* s_rtspServer;
ServerMediaSession* s_smss[SERVER_MEDIA_SESSION_MAX];
unsigned int s_sms_index = 0;
TaskScheduler* s_scheduler;
volatile char s_stop_flag = 0;

static int init_main() {
#ifdef _ANDROID_PLATFORM_
  LOGI("init_main()");
#endif
  // Begin by setting up our usage environment:
  LOGI("BasicTaskScheduler::createNew");
  TaskScheduler* scheduler = BasicTaskScheduler::createNew();
  s_scheduler = scheduler;
  LOGI("BasicUsageEnvironment::createNew");
  env = BasicUsageEnvironment::createNew(*scheduler);

  UserAuthenticationDatabase* authDB = NULL;
#ifdef ACCESS_CONTROL
  // To implement client access control to the RTSP server, do the following:
  authDB = new UserAuthenticationDatabase;
  authDB->addUserRecord("username1", "password1"); // replace these with real strings
  // Repeat the above with each <username>, <password> that you wish to allow
  // access to the server.
#endif

  // Create the RTSP server:
  LOGI("RTSPServer::createNew");
  RTSPServer* rtspServer = RTSPServer::createNew(*env, 8554, authDB);
  s_rtspServer = rtspServer;
  if (rtspServer == NULL) {
    *env << "Failed to create RTSP server: " << env->getResultMsg() << "\n";
    return (1);
  }

  char const* descriptionString
    = "Session streamed by \"RtspService\"";

  // A H.264 video elementary stream:
  {
    char const* streamName = "h264ESVideoTest";
    char const* inputFileName = "/sdcard/DCIM/test.h264";
	LOGI("ServerMediaSession::createNew");
    ServerMediaSession* sms
      = ServerMediaSession::createNew(*env, streamName, streamName,
				      descriptionString);
	LOGI("sms->addSubsession");
    sms->addSubsession(H264VideoFileServerMediaSubsession
		       ::createNew(*env, inputFileName, reuseFirstSource));
	LOGI("rtspServer->addServerMediaSession");
    rtspServer->addServerMediaSession(sms);
    s_smss[s_sms_index++] = sms;
    announceStream(rtspServer, sms, streamName, inputFileName);
  }

  // A MP3 audio stream (actually, any MPEG-1 or 2 audio file will work):
  {
    char const* streamName = "mp3AudioTest";
    char const* inputFileName = "/sdcard/DCIM/test.mp3";
    ServerMediaSession* sms
      = ServerMediaSession::createNew(*env, streamName, streamName,
				      descriptionString);
    Boolean useADUs = False;
    Interleaving* interleaving = NULL;
#ifdef STREAM_USING_ADUS
    useADUs = True;
#ifdef INTERLEAVE_ADUS
    unsigned char interleaveCycle[] = {0,2,1,3}; // or choose your own...
    unsigned const interleaveCycleSize
      = (sizeof interleaveCycle)/(sizeof (unsigned char));
    interleaving = new Interleaving(interleaveCycleSize, interleaveCycle);
#endif
#endif
    sms->addSubsession(MP3AudioFileServerMediaSubsession
		       ::createNew(*env, inputFileName, reuseFirstSource,
				   useADUs, interleaving));
    rtspServer->addServerMediaSession(sms);
    s_smss[s_sms_index++] = sms;
    announceStream(rtspServer, sms, streamName, inputFileName);
  }

  // A device source from camera 
  {
    char const* streamName = "h264DeviceTest";
    char const* deviceName = "MeigCamera";
	LOGI("ServerMediaSession::createNew");
    ServerMediaSession* sms
      = ServerMediaSession::createNew(*env, streamName, streamName,
				      descriptionString);
	LOGI("sms->addSubsession");
    sms->addSubsession(H264VideoDeviceServerMediaSubsession
		       ::createNew(*env, deviceName, reuseFirstSource));
	LOGI("rtspServer->addServerMediaSession");
    rtspServer->addServerMediaSession(sms);
    s_smss[s_sms_index++] = sms;
    announceStream(rtspServer, sms, streamName, deviceName);
  }
  //LOGI("doEventLoop()");
  //env->taskScheduler().doEventLoop();

  return 0;
}

static void doEventLoop() {
  LOGI("doEventLoop()");
  s_stop_flag = 0;
  env->taskScheduler().doEventLoop(&s_stop_flag);
  //while(1) {
  //  (BasicTaskScheduler0)env->taskScheduler().SingleStep();
  //}
}

static void release() {
  // release resource 
#if 1
  if(s_rtspServer != NULL) {
    // STEP1, sms;
    // release framesource(S1)
    //rtspServer->closeAllClientSessionsForServerMediaSession(sms);
    // release mediasession(S2) and mediasubsession(S3)
    //rtspServer->removeServerMediaSession(sms);
    unsigned int num = s_rtspServer->numClientSessions();
	LOGI("release: num=%d; s_sms_index=%d", num, s_sms_index);
	for(int i=0; i<s_sms_index; i++){
	  if(s_smss[i] != NULL){
        s_rtspServer->deleteServerMediaSession(s_smss[i]);
		s_smss[i] = NULL;
	  }
	}
	s_sms_index = 0;
    // STEP2, rtspServer(S4);
    Medium::close(s_rtspServer);
    s_rtspServer = NULL;
  }
  // STEP3, env(S5);
  if(env != NULL) {
    env->reclaim();
    env = NULL;
  }
  // STEP4, scheduler(S6);
  if(s_scheduler != NULL) {
    delete s_scheduler;
    s_scheduler = NULL;
  }
#endif

}

static void stopEventLoop()
{
  LOGI("stopEventLoop()");
  //volatile char flag = 1;
  //env->taskScheduler().doEventLoop(&flag);
  s_stop_flag = 1;
}

#endif

#if 1 
static int client_socket_fd = -1;
#define PATH "com.meige.localsocket"
#define NAMESPACE ANDROID_SOCKET_NAMESPACE_ABSTRACT
#define FRAME_MAX_SIZE 32768

static int init_local_socket()
{
	LOGI("%s(%d): init_local_socket; to call socket_local_server", __FILE__, __LINE__);
	int serverID = socket_local_server(PATH, NAMESPACE, SOCK_STREAM);
	LOGI("%s(%d): serverID=%d", __FILE__, __LINE__, serverID);
	if(serverID < 0){
		LOGE("socket_local_server failed :%d\n",serverID);
		return serverID;
	}
	int socketID;
	while(1)
	{
	    LOGI("%s(%d): accept...", __FILE__, __LINE__);
		socketID= accept(serverID,NULL,NULL);
		LOGI("%s(%d): errno=%d: [%s]", __FILE__, __LINE__, errno, strerror(errno));
	    LOGI("%s(%d): accept... socketID=%d", __FILE__, __LINE__, socketID);
	    if(socketID<0)
	    {
	      LOGE("cannot accept requst");
	      continue;
	    }
		// close last client connection 
		if(client_socket_fd > 0)
		{
	      close(client_socket_fd);
		}
		// set new client connection 
		client_socket_fd = socketID;
	}
	
	return 0;
}

static int readFromLocalSocket(unsigned char * buffer, int bufferSize)
{
  if(client_socket_fd < 0)
  {
    return -1;
  }
  memset(buffer, 0, bufferSize);
  LOGI("%s(%d): readFromLocalSocket: client_socket_fd=%d", __FILE__, __LINE__, client_socket_fd);
  int ret = read(client_socket_fd, buffer, bufferSize);
  LOGI("%s(%d): n2=%d", __FILE__, __LINE__, ret);
  if(ret <= 0)
  {
    LOGI("%s(%d): socket read failed (read data)", __FILE__, __LINE__);
	close(client_socket_fd);
	client_socket_fd = -1;
	return -1;
  }
  for(int i=0; i<(8<ret?8:ret); i++)
  {
    LOGI("#### 0x%02x ####", buffer[i]);
  }
  return ret;
}
#endif 

static jmethodID method_getFrame;
static JNIEnv * sJNIEnv;
static jclass sJNIClazz;
static unsigned long long _i = 0;

unsigned char isSourceAvailable(){
  LOGI("%s(%d): isSourceAvailable() client_socket_fd=%d", __FILE__, __LINE__, client_socket_fd);
  return client_socket_fd < 0 ? 0 : 1;
}

int getDeviceSourceFrame(unsigned char * buffer){
  LOGI("%s(%d): getDeviceSourceFrame %llu", __FILE__, __LINE__, _i++);
#if 1
  return readFromLocalSocket(buffer,FRAME_MAX_SIZE);
#endif 
}

int addFrameToQueue(unsigned char * buf, int len)
{
  LOGI("%s(%d): addFrameToQueue len=%d; PIPE_BUF=%d", __FILE__, __LINE__, len, PIPE_BUF);
  return 1;
}

/*
 * Class:     com_meige_rtspservice_rtsp_RTSPService
 * Method:    classInitNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_meige_rtspservice_rtsp_RTSPService_classInitNative
  (JNIEnv * env, jclass clazz) {
    LOGI("JNI: classInitNative()");
	sJNIEnv = env;
	sJNIClazz = (jclass)env->NewGlobalRef(clazz);
    method_getFrame = env->GetStaticMethodID(clazz, "getFrame", "()[B");
}

/*
 * Class:     com_meige_rtspservice_rtsp_RTSPService
 * Method:    initNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_meige_rtspservice_rtsp_RTSPService_initNative
  (JNIEnv *, jobject) {
  LOGI("Enter JNI; init_main()");
  init_local_socket();
  //init_main();
}

/*
 * Class:     com_meige_rtspservice_rtsp_RTSPService
 * Method:    startNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_meige_rtspservice_rtsp_RTSPService_startNative
  (JNIEnv *, jobject) {
  LOGI("JNI: startNative()");
  init_main();
  doEventLoop();
}

/*
 * Class:     com_meige_rtspservice_rtsp_RTSPService
 * Method:    stopNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_meige_rtspservice_rtsp_RTSPService_stopNative
  (JNIEnv *, jobject) {
  LOGI("JNI: stopNative()");
  stopEventLoop();
  release();
}

/*
 * Class:     com_meige_rtspservice_rtsp_RTSPService
 * Method:    cleanupNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_meige_rtspservice_rtsp_RTSPService_cleanupNative
  (JNIEnv *, jobject) {
  LOGI("JNI: cleanupNative()");
  if (sJNIClazz!= NULL) {
    sJNIEnv->DeleteGlobalRef(sJNIClazz);
    sJNIClazz = NULL;
  }
}

/*
 * Class:     com_meige_rtspservice_rtsp_RTSPService
 * Method:    cleanupNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_meige_rtspservice_rtsp_RTSPService_setNativeFrame
  (JNIEnv *env, jobject obj, jbyteArray data, jint len) {
  LOGI("JNI: setNativeFrame() len=%d", len);
  jbyte *frm = NULL;
  frm = env->GetByteArrayElements(data, NULL);
  unsigned char * buf = (unsigned char *)frm;
  //addFrameToQueue(buf, len);
  env->ReleaseByteArrayElements(data, frm, 0);
}

#ifdef __cplusplus
}
#endif
