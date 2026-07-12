package com.haunted421.clipbubdeep.action

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.haunted421.clipbubdeep.databinding.ActivityActionCustomizationBinding

class ActionCustomizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActionCustomizationBinding
    private lateinit var adapter: ActionAdapter
    private lateinit var repository: ActionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionCustomizationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = ActionRepository(this)
        val actions = repository.getActions().toMutableList()

        adapter = ActionAdapter(actions) { updated ->
            repository.saveActions(updated)
        }

        binding.rvActions.layoutManager = LinearLayoutManager(this)
        binding.rvActions.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.onItemMove(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.rvActions)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
