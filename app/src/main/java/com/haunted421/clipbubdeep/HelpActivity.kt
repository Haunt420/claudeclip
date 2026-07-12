package com.haunted421.clipbubdeep

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.haunted421.clipbubdeep.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.tvHelpContent.text = getString(R.string.help_content)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
