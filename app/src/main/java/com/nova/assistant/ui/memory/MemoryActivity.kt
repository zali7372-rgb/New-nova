package com.nova.assistant.ui.memory

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nova.assistant.NovaApplication
import com.nova.assistant.R
import com.nova.assistant.data.db.MemoryEntity
import com.nova.assistant.databinding.ActivityMemoryBinding
import com.nova.assistant.databinding.ItemMemoryBinding
import kotlinx.coroutines.launch

class MemoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.memory_title)

        val app = application as NovaApplication
        val adapter = MemoryAdapter { memory ->
            lifecycleScope.launch { app.memoryRepository.delete(memory) }
        }

        binding.recyclerMemories.layoutManager = LinearLayoutManager(this)
        binding.recyclerMemories.adapter = adapter

        binding.buttonDeleteAll.setOnClickListener {
            lifecycleScope.launch { app.memoryRepository.deleteAll() }
        }

        lifecycleScope.launch {
            app.memoryRepository.observeMemories().collect { memories ->
                adapter.submit(memories)
                binding.textEmpty.visibility =
                    if (memories.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }
}

private class MemoryAdapter(
    private val onDelete: (MemoryEntity) -> Unit
) : RecyclerView.Adapter<MemoryAdapter.Holder>() {

    private val items = mutableListOf<MemoryEntity>()

    fun submit(newItems: List<MemoryEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val binding = ItemMemoryBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val memory = items[position]
        holder.binding.textCategory.text = memory.category.name
        holder.binding.textContent.text = memory.content
        holder.binding.buttonDelete.setOnClickListener { onDelete(memory) }
    }

    override fun getItemCount() = items.size

    class Holder(val binding: ItemMemoryBinding) : RecyclerView.ViewHolder(binding.root)
}
