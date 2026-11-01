/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.workbench

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.editor.databinding.ItemThemeBinding

/**
 * The theme rows.
 *
 * A row says three things and no more: what the theme calls itself, which file
 * it is, and what it looks like. What it does *not* do is draw a keyboard —
 * that needs `ColorManager`, and `ColorManager` can only hold one theme.
 */
class ThemeCardAdapter(
    private val onOpen: (ThemeEntry) -> Unit,
) : RecyclerView.Adapter<ThemeCardAdapter.Holder>() {
    private var entries: List<ThemeEntry> = emptyList()

    fun submit(list: List<ThemeEntry>) {
        val previous = entries
        entries = list
        DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previous.size

                override fun getNewListSize(): Int = list.size

                override fun areItemsTheSame(
                    oldPos: Int,
                    newPos: Int,
                ): Boolean = previous[oldPos].id == list[newPos].id

                override fun areContentsTheSame(
                    oldPos: Int,
                    newPos: Int,
                ): Boolean = previous[oldPos] == list[newPos]
            },
        ).dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val binding = ItemThemeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.bind(entries[position])
    }

    inner class Holder(
        private val binding: ItemThemeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: ThemeEntry) {
            binding.name.text = entry.name
            binding.meta.text = entry.id
            binding.swatches.setColors(entry.swatches)
            binding.failure.text = entry.failure
            binding.failure.visibility = if (entry.failure == null) View.GONE else View.VISIBLE
            binding.root.setOnClickListener { onOpen(entry) }
        }
    }
}
