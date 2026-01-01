package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddToPlaylistBottomSheet : BottomSheetDialogFragment() {

    private var song: Song? = null
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_to_playlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvPlaylists)
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewModel.loadPlaylists()
        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val itemView = LayoutInflater.from(parent.context)
                        .inflate(android.R.layout.simple_list_item_1, parent, false)
                    return object : RecyclerView.ViewHolder(itemView) {}
                }

                override fun getItemCount() = playlists.size

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val playlist = playlists[position]
                    (holder.itemView as android.widget.TextView).apply {
                        text = playlist.name
                        setTextColor(context.getColor(android.R.color.white))
                        setPadding(48, 32, 48, 32)
                        setOnClickListener {
                            song?.let { s ->
                                viewModel.addSongToPlaylist(playlist.id, s)
                                Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                dismiss()
                            }
                        }
                    }
                }
            }
        }

        view.findViewById<View>(R.id.btnCancel)?.setOnClickListener { dismiss() }
    }

    companion object {
        fun newInstance(song: Song): AddToPlaylistBottomSheet {
            return AddToPlaylistBottomSheet().apply {
                this.song = song
            }
        }
    }
}
