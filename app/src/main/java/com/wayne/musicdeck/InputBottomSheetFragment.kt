package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wayne.musicdeck.databinding.FragmentInputBottomSheetBinding

class InputBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentInputBottomSheetBinding? = null
    private val binding get() = _binding!!

    // Configuration
    var title: String = "Enter Input"
    var hint: String = ""
    var initialValue: String? = null
    var positiveButtonText: String = "Save"
    
    // Callback
    var onSaveListener: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup UI
        binding.tvTitle.text = title
        binding.tilInput.hint = hint
        binding.btnSave.text = positiveButtonText
        
        initialValue?.let {
            binding.etInput.setText(it)
            binding.etInput.selectAll()
        }

        // Save Button
        binding.btnSave.setOnClickListener {
            val input = binding.etInput.text.toString().trim()
            if (input.isNotEmpty()) {
                onSaveListener?.invoke(input)
                dismiss()
            } else {
                binding.tilInput.error = "Cannot be empty"
            }
        }
        
        // Show Keyboard automatically
        binding.etInput.requestFocus()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            title: String,
            hint: String = "",
            initialValue: String? = null,
            positiveButtonText: String = "Save"
        ): InputBottomSheetFragment {
            val fragment = InputBottomSheetFragment()
            fragment.title = title
            fragment.hint = hint
            fragment.initialValue = initialValue
            fragment.positiveButtonText = positiveButtonText
            return fragment
        }
    }
}
