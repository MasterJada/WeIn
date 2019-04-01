package com.bpst.wein.webrtc

import android.content.Context
import android.os.Build
import android.util.Log
import org.webrtc.*
import org.webrtc.MediaConstraints


class Peer : PeerConnection.Observer, SdpObserver {

    private var videoCapturer: VideoCapturer? = null
    private lateinit var videoSource: VideoSource
    private lateinit var videoTrack: VideoTrack
    private var peerConnection: PeerConnection? = null
    private lateinit var factory: PeerConnectionFactory
    private val rootEglBase = EglBase.create()

    private val iceServers: List<PeerConnection.IceServer> = arrayListOf(
        PeerConnection.IceServer.builder("stun:74.125.140.127:19302").createIceServer(),
     PeerConnection.IceServer.builder("stun:[2A00:1450:400C:C08::7F]:19302").createIceServer())

    private var onOffer: ((SessionDescription) -> Unit)? = null
    private var onAnswer: ((SessionDescription) -> Unit)? = null
    private var onGotStream:((MediaStream) -> Unit)? = null

    fun initialize(context: Context) {
        val fieldTrials = (PeerConnectionFactory.VIDEO_FRAME_EMIT_TRIAL + "/" + PeerConnectionFactory.TRIAL_ENABLED + "/")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials(fieldTrials)
                .setEnableInternalTracer(true)
                .setInjectableLogger({s, severity, sev ->
                    Log.i("Peer log", s)
                },Logging.Severity.LS_ERROR)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
        factory.printInternalStackTraces(true)


    }

    fun startCamera(viewRenderer: SurfaceViewRenderer, context: Context) {
        videoCapturer =
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                    createCameraCapturer(Camera2Enumerator(context))
                } else {
                    createCameraCapturer(Camera1Enumerator(true))
                }

        videoSource = factory.createVideoSource(false)
        videoTrack = factory.createVideoTrack("_video", videoSource)
        videoCapturer?.initialize(
            SurfaceTextureHelper.create("101", rootEglBase.eglBaseContext),
            context,
            videoSource.capturerObserver
        )
        viewRenderer.init(rootEglBase.eglBaseContext, null)
        viewRenderer.setMirror(true)

        videoTrack.addSink(viewRenderer)
        videoCapturer?.startCapture(640, 480, 30)

        val videoConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(videoConstraints)
        val audioTrack = factory.createAudioTrack("_audio", audioSource)

        val mediaStreamLabels = listOf("ARDAMS")
        peerConnection?.addTrack(videoTrack, mediaStreamLabels)
        peerConnection?.addTrack(audioTrack, mediaStreamLabels)
    }


    fun gotStraemCallback(callback: (MediaStream) -> Unit){
        onGotStream = callback
    }
    fun createPeer() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        peerConnection = factory.createPeerConnection(rtcConfig, this)

    }

    fun createOffer() {
        peerConnection?.createOffer(this, MediaConstraints())

    }

    fun createOffer(callback: ((SessionDescription) -> Unit)) {
        onOffer = callback

        peerConnection?.setAudioRecording(true)
        peerConnection?.startRtcEventLog(0, 200)
        peerConnection?.createOffer(this, MediaConstraints())
    }

    fun setRemoteSdp(seesiondescription: SessionDescription?) {
        peerConnection?.setRemoteDescription(this, seesiondescription)

    }
    fun setLocalSdp(seesiondescription: SessionDescription?) {
        peerConnection?.setLocalDescription(this, seesiondescription)
    }


    fun createAnswer() {
        peerConnection?.createAnswer(this, MediaConstraints())
    }

    fun createAnswer(callback: ((SessionDescription) -> Unit)) {
        onAnswer = callback
        peerConnection?.createAnswer(this, MediaConstraints())
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        deviceNames.forEach { deviceName ->
            if (enumerator.isFrontFacing(deviceName)) {
                enumerator.createCapturer(deviceName, null)?.let {
                    return it
                }
            }
        }
        return null
    }

    //region PeerConnection.Observer
    override fun onIceCandidate(iceCandidate: IceCandidate?) {
        Log.i("Peer Observer", "[onSetFailure($iceCandidate)]")
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        Log.i("Peer Observer", "[onDataChannel($dataChannel)]")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.i("Peer Observer", "[onIceConnectionReceivingChange($p0)]")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        Log.i("Peer Observer", "[onIceConnectionChange($p0)]")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Log.i("Peer Observer", "[onIceGatheringChange($p0)]")
    }

    override fun onAddStream(p0: MediaStream?) {
        Log.i("Peer Observer", "[onAddStream($p0)]")
        p0?.let { stream ->
            print(stream.audioTracks.size)
            onGotStream?.invoke(stream)
        }
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.i("Peer Observer", "[onSignalingChange($p0)]")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Log.i("Peer Observer", "[onIceCandidatesRemoved($p0)]")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        Log.i("Peer Observer", "[onRemoveStream($p0)]")
    }

    override fun onRenegotiationNeeded() {
        Log.i("Peer Observer", "[onRenegotiationNeeded()]")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Log.i("Peer Observer", "[onRenegotiationNeeded($p0 , $p1)]")
    }
    //endregion

    //region SdpObserver
    override fun onSetFailure(error: String?) {
        Log.i("Peer SdpObserver", "[onSetFailure($error)]")
    }

    override fun onSetSuccess() {
        Log.i("Peer SdpObserver", "[onSetSuccess()]")

    }

    override fun onCreateSuccess(seesiondescription: SessionDescription?) {
        seesiondescription?.let {
            when (it.type) {
                SessionDescription.Type.OFFER -> {
                    setLocalSdp(seesiondescription)
                    onOffer?.invoke(it)

                }
                SessionDescription.Type.ANSWER -> {
                    setLocalSdp(seesiondescription)
                    onAnswer?.invoke(it)

                }
                else -> Log.i("Peer", "Something strange...")
            }
        }
    }

    override fun onCreateFailure(error: String?) {
        Log.i("Peer SdpObserver", "[onCreateFailure($error)]")
    }
    //endregion
}