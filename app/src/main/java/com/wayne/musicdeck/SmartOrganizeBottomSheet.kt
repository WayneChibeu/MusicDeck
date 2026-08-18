package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wayne.musicdeck.data.OrganizationSuggestion
import com.wayne.musicdeck.databinding.FragmentSmartOrganizeBinding
import com.wayne.musicdeck.databinding.ItemOrganizationSuggestionBinding

class SmartOrganizeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentSmartOrganizeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmartOrganizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SuggestionAdapter()
        binding.rvSuggestions.adapter = adapter

        // Sync UI with current ViewModel state
        viewModel.useOnlineWisdom.observe(viewLifecycleOwner) { enabled ->
            binding.tvOnlineWisdom.text = if (enabled) "Elite Sync: ON" else "Elite Sync: OFF"
            binding.ivOnlineWisdom.imageTintList = android.content.res.ColorStateList.valueOf(
                if (enabled) android.graphics.Color.parseColor("#4CAF50") else android.graphics.Color.LTGRAY
            )
        }

        binding.btnToggleOnlineWisdom.setOnClickListener {
            val currentState = viewModel.useOnlineWisdom.value ?: false
            binding.loadingProgress.visibility = View.VISIBLE
            binding.rvSuggestions.visibility = View.GONE
            viewModel.setUseOnlineWisdom(!currentState)
        }

        binding.btnResetMetadata.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset to Originals?")
                .setMessage("This will wipe all previous Smart Organization changes and restore your music to their original tags. Use this to fix missing collaborations!")
                .setPositiveButton("Reset") { _, _ ->
                    binding.loadingProgress.visibility = View.VISIBLE
                    binding.rvSuggestions.visibility = View.GONE
                    viewModel.resetOrganization()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Trigger initial scan if no suggestions yet
        if (viewModel.organizationSuggestions.value.isNullOrEmpty()) {
            binding.loadingProgress.visibility = View.VISIBLE
            viewModel.generateOrganizationSuggestions()
        }

        viewModel.organizationSuggestions.observe(viewLifecycleOwner) { suggestions ->
            binding.loadingProgress.visibility = View.GONE
            binding.rvSuggestions.visibility = View.VISIBLE
            if (suggestions.isEmpty()) {
                binding.tvNoSuggestions.visibility = View.VISIBLE
                binding.btnApplyAll.isEnabled = false
                binding.btnApplyAll.alpha = 0.5f
                binding.tvDescription.text = "Library is already optimized."
            } else {
                binding.tvNoSuggestions.visibility = View.GONE
                binding.btnApplyAll.isEnabled = true
                binding.btnApplyAll.alpha = 1.0f
                binding.tvDescription.text = "I've found ${suggestions.size} improvements:"
                adapter.submitList(suggestions)
            }
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnApplyAll.setOnClickListener {
            val suggestions = viewModel.organizationSuggestions.value ?: emptyList()
            if (suggestions.isNotEmpty()) {
                viewModel.applyOrganizationSuggestions(suggestions)
                android.widget.Toast.makeText(context, "Library organized!", android.widget.Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SuggestionAdapter : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {
        private var items = listOf<OrganizationSuggestion>()

        fun submitList(list: List<OrganizationSuggestion>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemOrganizationSuggestionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(private val binding: ItemOrganizationSuggestionBinding) :
            RecyclerView.ViewHolder(binding.root) {
            
            fun bind(item: OrganizationSuggestion) {
                // Show artist/album/title changes concisely
                val before = mutableListOf<String>()
                val after = mutableListOf<String>()

                if (item.currentArtist != item.suggestedArtist) {
                    before.add(item.currentArtist)
                    after.add(item.suggestedArtist)
                }
                if (item.currentAlbum != item.suggestedAlbum) {
                    before.add(item.currentAlbum)
                    after.add(item.suggestedAlbum)
                }
                if (item.currentTitle != item.suggestedTitle) {
                    before.add(item.currentTitle)
                    after.add(item.suggestedTitle)
                }

                binding.tvBefore.text = before.joinToString(" • ")
                binding.tvAfter.text = after.joinToString(" • ")
                binding.tvReason.text = item.reason
            }
        }
    }
}
