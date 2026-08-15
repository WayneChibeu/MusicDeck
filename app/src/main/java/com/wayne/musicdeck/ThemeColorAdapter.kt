package com.wayne.musicdeck

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wayne.musicdeck.databinding.ItemThemeColorBinding
import com.wayne.musicdeck.utils.ThemeHelper

class ThemeColorAdapter(
    private val themes: List<ThemeHelper.ThemeItem>,
    private var selectedThemeId: String,
    private val onThemeSelected: (ThemeHelper.ThemeItem) -> Unit
) : RecyclerView.Adapter<ThemeColorAdapter.ThemeViewHolder>() {

    inner class ThemeViewHolder(val binding: ItemThemeColorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ThemeHelper.ThemeItem) {
            val context = itemView.context
            val isSelected = item.id == selectedThemeId

            // Set color circle background tint
            val color = ContextCompat.getColor(context, item.colorResId)
            binding.viewColorCircle.backgroundTintList = android.content.res.ColorStateList.valueOf(color)

            // Set theme display name
            binding.tvThemeName.text = item.name

            // Selected styling indicator
            if (isSelected) {
                binding.viewSelectedRing.visibility = View.VISIBLE
                binding.viewSelectedRing.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.white)
                )
                binding.ivSelectedCheck.visibility = View.VISIBLE
                binding.tvThemeName.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.tvThemeName.setTypeface(null, Typeface.BOLD)
            } else {
                binding.viewSelectedRing.visibility = View.GONE
                binding.ivSelectedCheck.visibility = View.GONE
                binding.tvThemeName.setTextColor(android.graphics.Color.parseColor("#99FFFFFF"))
                binding.tvThemeName.setTypeface(null, Typeface.NORMAL)
            }

            binding.containerThemeItem.setOnClickListener {
                if (selectedThemeId != item.id) {
                    val oldSelected = selectedThemeId
                    selectedThemeId = item.id
                    notifyDataSetChanged()
                    onThemeSelected(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val binding = ItemThemeColorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ThemeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        holder.bind(themes[position])
    }

    override fun getItemCount(): Int = themes.size
}
