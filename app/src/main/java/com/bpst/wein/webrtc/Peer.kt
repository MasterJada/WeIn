package com.bpst.wein.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.MediaConstraints


class Peer: PeerConnection.Observer, SdpObserver {

    private constructor(id: String = "")

    private var videoCapturer: VideoCapturer? = null
    private lateinit var videoSource: VideoSource
    lateinit var videoTrack: VideoTrack
    var peerConnection: PeerConnection? = null
    private lateinit var factory: PeerConnectionFactory
    private lateinit var rootEglBase: EglBase

    private val iceServers: List<PeerConnection.IceServer> = arrayListOf()

    private var onOffer: ((SessionDescription) -> Unit)? = null
    private var onAnswer: ((SessionDescription) -> Unit)? = null
    private var onGotStream: ((VideoTrack) -> Unit)? = null
    private var onIceCandidate: ((IceCandidate?) -> Unit)? = null

    fun initialize(context: Context, eglContext: EglBase) {
        rootEglBase = eglContext

        val fieldTrials =
            (PeerConnectionFactory.VIDEO_FRAME_EMIT_TRIAL + "/" + PeerConnectionFactory.TRIAL_ENABLED + "/")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials(fieldTrials)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        factory.printInternalStackTraces(true)
        createPeer()

    }

    /**
     * Parameter to init VideoView which will be used to show local camera
     */
    fun initLocalView(viewRenderer: SurfaceViewRenderer) {
        viewRenderer.init(rootEglBase.eglBaseContext, null)
        viewRenderer.setMirror(true)
        videoTrack.addSink(viewRenderer)
    }

    fun startCamera(context: Context, height: Int = 1280, width: Int = 720, fps: Int = 30) {
        videoCapturer = createCameraCapturer(Camera1Enumerator(false))
        videoCapturer?.initialize(
            SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext),
            context,
            videoSource.capturerObserver
        )
        videoCapturer?.startCapture(height, width, fps)
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

    private fun createPeer() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = factory.createPeerConnection(rtcConfig, this)

        videoSource = factory.createVideoSource(false)
        videoTrack = factory.createVideoTrack("_video", videoSource)
        videoTrack.setEnabled(true)
        val videoConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(videoConstraints)
        val audioTrack = factory.createAudioTrack("_audio", audioSource)

        val mediaStreamLabels = listOf("ARDAMS")
        peerConnection?.addTrack(videoTrack, mediaStreamLabels)
        peerConnection?.addTrack(audioTrack, mediaStreamLabels)
    }

    fun createOffer(callback: ((SessionDescription) -> Unit)) {
        onOffer = callback

        peerConnection?.setAudioRecording(true)
        peerConnection?.startRtcEventLog(0, 200)
        peerConnection?.createOffer(this, MediaConstraints())
    }

    fun setRemoteSdp(sessionDescription: SessionDescription?) {
        peerConnection?.setRemoteDescription(this, sessionDescription)
    }

    private fun setLocalSdp(sessionDescription: SessionDescription?) {
        peerConnection?.setLocalDescription(this, sessionDescription)
    }

    fun createAnswer(callback: ((SessionDescription) -> Unit)) {
        onAnswer = callback
        peerConnection?.createAnswer(this, MediaConstraints())
    }

    fun gotStraemCallback(callback: (VideoTrack) -> Unit) {
        onGotStream = callback
    }

    fun onIceCandidateCallback(callback: (IceCandidate?) -> Unit){
        onIceCandidate = callback
    }

    //region PeerConnection.Observer


    override fun onIceCandidate(iceCandidate: IceCandidate?) {
        Log.i("Peer Observer", "[onIceCandidate($iceCandidate)]")
        onIceCandidate?.invoke(iceCandidate)
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
        peerConnection?.transceivers?.firstOrNull()?.let {
            (it.receiver.track() as? VideoTrack)?.let {
                videoTrack = it
                onGotStream?.invoke(it)
            }
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
        Log.i("Peer Observer", "[onaddtrack($p0 , $p1)]")
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
            setLocalSdp(seesiondescription)
            when (it.type) {
                SessionDescription.Type.OFFER -> {
                    onOffer?.invoke(it)
                }
                SessionDescription.Type.ANSWER -> {
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



     class PeerBuilder{
         fun createPeer(context: Context, egl: EglBase): Peer{
             val peer = Peer()
             peer.initialize(context, egl)
             return peer
         }
    }
}