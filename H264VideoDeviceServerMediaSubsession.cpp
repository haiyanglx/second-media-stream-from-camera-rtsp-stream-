/**********
This library is free software; you can redistribute it and/or modify it under
the terms of the GNU Lesser General Public License as published by the
Free Software Foundation; either version 3 of the License, or (at your
option) any later version. (See <http://www.gnu.org/copyleft/lesser.html>.)

This library is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
more details.

You should have received a copy of the GNU Lesser General Public License
along with this library; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
**********/
// "liveMedia"
// Copyright (c) 1996-2018 Live Networks, Inc.  All rights reserved.
// A 'ServerMediaSubsession' object that creates new, unicast, "RTPSink"s
// on demand, from a H264 video file.
// Implementation

#include "H264VideoDeviceServerMediaSubsession.hh"
#include "H264VideoRTPSink.hh"
#include "ByteStreamFileSource.hh"
#include "H264VideoStreamFramer.hh"
#include "H264DeviceSource.hh"

H264VideoDeviceServerMediaSubsession*
H264VideoDeviceServerMediaSubsession::createNew(UsageEnvironment& env,
					      char const* deviceName,
					      Boolean reuseFirstSource) {
  return new H264VideoDeviceServerMediaSubsession(env, deviceName, reuseFirstSource);
}

H264VideoDeviceServerMediaSubsession::H264VideoDeviceServerMediaSubsession(UsageEnvironment& env,
								       char const* deviceName, Boolean reuseFirstSource)
  : OnDemandServerMediaSubsession(env, reuseFirstSource),
    fAuxSDPLine(NULL), fDoneFlag(0), fDummyRTPSink(NULL) {
}

H264VideoDeviceServerMediaSubsession::~H264VideoDeviceServerMediaSubsession() {
  delete[] fAuxSDPLine;
}

static void afterPlayingDummy(void* clientData) {
  H264VideoDeviceServerMediaSubsession* subsess = (H264VideoDeviceServerMediaSubsession*)clientData;
  subsess->afterPlayingDummy1();
}

void H264VideoDeviceServerMediaSubsession::afterPlayingDummy1() {
  // Unschedule any pending 'checking' task:
  envir().taskScheduler().unscheduleDelayedTask(nextTask());
  // Signal the event loop that we're done:
  setDoneFlag();
}

static void checkForAuxSDPLine(void* clientData) {
  H264VideoDeviceServerMediaSubsession* subsess = (H264VideoDeviceServerMediaSubsession*)clientData;
  subsess->checkForAuxSDPLine1();
}

void H264VideoDeviceServerMediaSubsession::checkForAuxSDPLine1() {
  nextTask() = NULL;
  LOGI("%s (%d): checkForAuxSDPLine1()", __FILE__, __LINE__);
  char const* dasl;
  if (fAuxSDPLine != NULL) {
  	LOGI("%s (%d): fAuxSDPLine != NULL we're done: fAuxSDPLine=[%s]", __FILE__, __LINE__, fAuxSDPLine);
    // Signal the event loop that we're done:
    setDoneFlag();
  } else if (fDummyRTPSink != NULL && (dasl = fDummyRTPSink->auxSDPLine()) != NULL) {
    LOGI("%s (%d): after fDummyRTPSink->auxSDPLine() dasl=[%s]", __FILE__, __LINE__, dasl);
    fAuxSDPLine = strDup(dasl);
    fDummyRTPSink = NULL;

    // Signal the event loop that we're done:
    setDoneFlag();
  } else if (!fDoneFlag) {
    LOGI("%s (%d): try again after a brief delay: 100ms", __FILE__, __LINE__);
    // try again after a brief delay:
    int uSecsToDelay = 100000; // 100 ms
    nextTask() = envir().taskScheduler().scheduleDelayedTask(uSecsToDelay,
			      (TaskFunc*)checkForAuxSDPLine, this);
  }
}

char const* H264VideoDeviceServerMediaSubsession::getAuxSDPLine(RTPSink* rtpSink, FramedSource* inputSource) {
  if (fAuxSDPLine != NULL) return fAuxSDPLine; // it's already been set up (for a previous client)
  LOGI("%s (%d): getAuxSDPLine(...)", __FILE__, __LINE__);
  if (fDummyRTPSink == NULL) { // we're not already setting it up for another, concurrent stream
    // Note: For H264 video files, the 'config' information ("profile-level-id" and "sprop-parameter-sets") isn't known
    // until we start reading the file.  This means that "rtpSink"s "auxSDPLine()" will be NULL initially,
    // and we need to start reading data from our file until this changes.
    fDummyRTPSink = rtpSink;
    LOGI("%s (%d): Start reading the file:", __FILE__, __LINE__);
    // Start reading the file:
    fDummyRTPSink->startPlaying(*inputSource, afterPlayingDummy, this);
    LOGI("%s (%d): Check whether the sink's 'auxSDPLine()' is ready: call checkForAuxSDPLine(this)", __FILE__, __LINE__);
    // Check whether the sink's 'auxSDPLine()' is ready:
    checkForAuxSDPLine(this);
  }
  LOGI("%s (%d): doEventLoop...", __FILE__, __LINE__);
  envir().taskScheduler().doEventLoop(&fDoneFlag);

  return fAuxSDPLine;
}

FramedSource* H264VideoDeviceServerMediaSubsession::createNewStreamSource(unsigned /*clientSessionId*/, unsigned& estBitrate) {
  estBitrate = 500; // kbps, estimate
  LOGI("%s (%d): H264VideoDeviceServerMediaSubsession::createNewStreamSource:", __FILE__, __LINE__);
  // Create the video source:
  H264DeviceSource* deviceSource = H264DeviceSource::createNew(envir(), H264DeviceParameters());
  LOGI("%s (%d): %s" , __FILE__, __LINE__, deviceSource == NULL ? "deviceSource == NULL" : "deviceSource != NULL");
  if (deviceSource == NULL) return NULL;

  // Create a framer for the Video Elementary Stream:
  return H264VideoStreamFramer::createNew(envir(), deviceSource);
}

RTPSink* H264VideoDeviceServerMediaSubsession
::createNewRTPSink(Groupsock* rtpGroupsock,
		   unsigned char rtpPayloadTypeIfDynamic,
		   FramedSource* /*inputSource*/) {
  LOGI("%s (%d): createNewRTPSink(...)" , __FILE__, __LINE__);
  return H264VideoRTPSink::createNew(envir(), rtpGroupsock, rtpPayloadTypeIfDynamic);
}
