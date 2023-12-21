package com.zu.camerautil

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zu.camerautil.bean.CameraInfoWrapper
import com.zu.camerautil.camera.queryCameraInfo
import com.zu.camerautil.camera.sortCamera
import com.zu.camerautil.databinding.ActivityCameraListBinding
import com.zu.camerautil.view.CameraRecyclerViewAdapter

class CameraListActivity : AppCompatActivity() {

    private val cameraInfoMap: HashMap<String, CameraInfoWrapper> by lazy {
        queryCameraInfo(this)
    }

    private val cameraInfoList: ArrayList<CameraInfoWrapper> by lazy {
        ArrayList<CameraInfoWrapper>(cameraInfoMap.size).apply {
            addAll(cameraInfoMap.values)
            sortCamera(this)
        }
    }



    private lateinit var binding: ActivityCameraListBinding

    private val adapter = CameraRecyclerViewAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
    }

    private fun initViews() {
        adapter.addData(cameraInfoList)
        adapter.onItemClickListener = {
            val id = it.cameraID
            val intent = Intent(this, CameraInfoActivity::class.java)
            intent.putExtra("camera_id", id)
            startActivity(intent)
        }
        binding.rvCamera.adapter = adapter
        binding.rvCamera.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rvCamera.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }
}