package com.haunted421.clipbubdeep.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.haunted421.clipbubdeep.R
import com.haunted421.clipbubdeep.databinding.ActivityHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private lateinit var manager: ClipboardHistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        manager = ClipboardHistoryManager.getInstance(this)
        adapter = HistoryAdapter(
            onItemClick = { entry ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ClipBub", entry.text))
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { entry ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_entry)
                    .setMessage(entry.text.take(120))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch { manager.delete(entry) }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        )

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_all)
                .setMessage(R.string.clear_all_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch { manager.deleteAll() }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                observeEntries(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        observeEntries("")
    }

    private var currentJob: kotlinx.coroutines.Job? = null

    private fun observeEntries(query: String) {
        currentJob?.cancel()
        currentJob = lifecycleScope.launch {
            manager.searchEntries(query).collectLatest { entries ->
                adapter.submitList(entries)
                binding.tvEmpty.visibility =
                    if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
