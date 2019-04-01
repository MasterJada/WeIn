package com.bpst.wein

import android.Manifest
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import com.bpst.wein.webrtc.Peer
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.EglBase

class MainActivity : AppCompatActivity() {
    val peer = Peer()
    val peer2 = Peer()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        peer.initialize(applicationContext)
        peer2.initialize(applicationContext)

        val egl = EglBase.create()
        incoming_video.init(egl.eglBaseContext, null)
        peer.createPeer()
        peer2.createPeer()
        peer2.gotStraemCallback {
            it.videoTracks.firstOrNull()?.let {
                it.addSink { videoFrame ->
                    print("")
                }
            }
        }
        startCamera()
        bt_call.setOnClickListener {
                peer.createOffer{
                    peer2.setRemoteSdp(it)
                    peer2.createAnswer {
                        peer.setRemoteSdp(it)
                    }

                }
        }
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_NETWORK_STATE), 101)
        }
    }

    private fun startCamera(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            return
        }
        peer.startCamera(outgoing_video, this)

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            startCamera()
        }
    }


}
