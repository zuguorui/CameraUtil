package com.zu.camerautil

import android.content.Intent
import android.media.MediaCodecInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.zu.camerautil.databinding.ActivityCodecListBinding
import com.zu.camerautil.util.listAllCodec
import com.zu.camerautil.util.listRegularCodec
import com.zu.camerautil.view.CodecListAdapter

class CodecListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodecListBinding

    private val codecList = ArrayList<MediaCodecInfo>()

    private lateinit var adapter: CodecListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodecListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = CodecListAdapter(this).apply {
            onItemClickListener = {position: Int, info: MediaCodecInfo ->
                val intent = Intent(this@CodecListActivity, CodecDetailActivity::class.java)
                intent.putExtra("codec_name", info.name)
                startActivity(intent)
            }
        }
        initCodecList()
    }

    private fun initCodecList() {
        codecList.clear()
        codecList.addAll(listRegularCodec())

        adapter.addData(codecList)

        binding.rvCodec.adapter = adapter
        binding.rvCodec.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rvCodec.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

    }
}