package com.nova.assistant.ui.aliases

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nova.assistant.NovaApplication
import com.nova.assistant.R
import com.nova.assistant.data.db.AppAliasEntity
import com.nova.assistant.databinding.ActivityAliasesBinding
import com.nova.assistant.databinding.ItemAliasBinding
import kotlinx.coroutines.launch

class AliasManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAliasesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAliasesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.aliases_title)

        val app = application as NovaApplication
        val adapter = AliasAdapter { alias ->
            lifecycleScope.launch { app.memoryRepository.removeAlias(alias) }
        }

        binding.recyclerAliases.layoutManager = LinearLayoutManager(this)
        binding.recyclerAliases.adapter = adapter

        binding.buttonAdd.setOnClickListener {
            val packageName = binding.inputPackage.text?.toString()?.trim().orEmpty()
            val alias = binding.inputAlias.text?.toString()?.trim().orEmpty()
            if (packageName.isNotBlank() && alias.isNotBlank()) {
                lifecycleScope.launch {
                    app.memoryRepository.addAlias(packageName, alias)
                    binding.inputPackage.text?.clear()
                    binding.inputAlias.text?.clear()
                }
            }
        }

        lifecycleScope.launch {
            app.memoryRepository.observeAliases().collect { aliases ->
                adapter.submit(aliases)
                binding.textEmpty.visibility = if (aliases.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

private class AliasAdapter(
    private val onDelete: (AppAliasEntity) -> Unit
) : RecyclerView.Adapter<AliasAdapter.Holder>() {

    private val items = mutableListOf<AppAliasEntity>()

    fun submit(newItems: List<AppAliasEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAliasBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.textAliasInfo.text = "${item.alias} → ${item.packageName}"
        holder.binding.buttonDeleteAlias.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    class Holder(val binding: ItemAliasBinding) : RecyclerView.ViewHolder(binding.root)
}
