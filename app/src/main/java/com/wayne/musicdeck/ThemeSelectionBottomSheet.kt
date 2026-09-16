package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wayne.musicdeck.databinding.FragmentThemeSelectionBinding
import com.wayne.musicdeck.utils.ThemeHelper

class ThemeSelectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentThemeSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThemeSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentTheme = ThemeHelper.getTheme(requireContext())

        // 1. Dynamic Wallpaper Option (Universal: Android 8.1+ / Palette Extraction)
        if (ThemeHelper.isDynamicSupported()) {
            binding.cardDynamicTheme.visibility = View.VISIBLE
            val isDynamicSelected = currentTheme == ThemeHelper.THEME_DYNAMIC
            binding.ivDynamicCheck.visibility = if (isDynamicSelected) View.VISIBLE else View.GONE

            // Display cached wallpaper color immediately
            val initialColor = ThemeHelper.getWallpaperSeedColor(requireContext())
            val colorStateList = android.content.res.ColorStateList.valueOf(initialColor)
            val bgAlpha = androidx.core.graphics.ColorUtils.setAlphaComponent(initialColor, 45)
            binding.viewDynamicColorCircle.backgroundTintList = android.content.res.ColorStateList.valueOf(bgAlpha)
            binding.ivDynamicIcon.imageTintList = colorStateList
            binding.ivDynamicCheck.imageTintList = colorStateList

            // Asynchronously refresh in case user changed wallpaper recently
            ThemeHelper.refreshWallpaperColorAsync(requireContext()) { freshColor ->
                _binding?.let { b ->
                    val freshStateList = android.content.res.ColorStateList.valueOf(freshColor)
                    val freshBg = androidx.core.graphics.ColorUtils.setAlphaComponent(freshColor, 45)
                    b.viewDynamicColorCircle.backgroundTintList = android.content.res.ColorStateList.valueOf(freshBg)
                    b.ivDynamicIcon.imageTintList = freshStateList
                    b.ivDynamicCheck.imageTintList = freshStateList
                }
            }

            binding.cardDynamicTheme.setOnClickListener {
                applyTheme(ThemeHelper.THEME_DYNAMIC)
            }
        } else {
            binding.cardDynamicTheme.visibility = View.GONE
        }

        // 2. Curated Brand Color Palettes
        val brandThemes = ThemeHelper.getAvailableThemes().filter { !it.isDynamic }
        binding.rvThemeColors.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvThemeColors.adapter = ThemeColorAdapter(brandThemes, currentTheme) { selectedItem ->
            applyTheme(selectedItem.id)
        }
    }

    private fun applyTheme(theme: String) {
        val current = ThemeHelper.getTheme(requireContext())
        if (current != theme) {
            ThemeHelper.saveTheme(requireContext(), theme)
            // Recreate activity to apply theme smoothly
            requireActivity().recreate()
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
