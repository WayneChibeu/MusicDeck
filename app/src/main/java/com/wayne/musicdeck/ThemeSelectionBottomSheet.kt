package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wayne.musicdeck.utils.ThemeHelper
import com.wayne.musicdeck.databinding.FragmentThemeSelectionBinding

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

        binding.btnThemeViolet.setOnClickListener { applyTheme(ThemeHelper.THEME_VIOLET) }
        binding.btnThemeOcean.setOnClickListener { applyTheme(ThemeHelper.THEME_OCEAN) }
        binding.btnThemeRose.setOnClickListener { applyTheme(ThemeHelper.THEME_ROSE) }
        binding.btnThemeNeon.setOnClickListener { applyTheme(ThemeHelper.THEME_NEON) }
        binding.btnThemeAmber.setOnClickListener { applyTheme(ThemeHelper.THEME_AMBER) }
        binding.btnThemeSky.setOnClickListener { applyTheme(ThemeHelper.THEME_SKY) }
    }
    
    private fun applyTheme(theme: String) {
        val current = ThemeHelper.getTheme(requireContext())
        if (current != theme) {
            ThemeHelper.saveTheme(requireContext(), theme)
            // Recreate activity to apply theme
            requireActivity().recreate()
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
