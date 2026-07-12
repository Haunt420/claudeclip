package com.haunted421.clipbubdeep.action

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.haunted421.clipbubdeep.databinding.ItemActionBinding
import java.util.Collections

class ActionAdapter(
    private val actions: MutableList<Action>,
    private val onListChanged: (List<Action>) -> Unit
) : RecyclerView.Adapter<ActionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemActionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = actions[position]
        holder.binding.tvActionTitle.setText(action.titleResId)
        holder.binding.ivActionIcon.setImageResource(action.iconResId)
        holder.binding.switchEnabled.isChecked = action.enabled
        holder.binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            actions[holder.adapterPosition].enabled = isChecked
            onListChanged(actions.toList())
        }
    }

    override fun getItemCount(): Int = actions.size

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(actions, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        actions.forEachIndexed { index, action -> action.order = index }
        onListChanged(actions.toList())
    }

    fun getActions(): List<Action> = actions.toList()
}
