package com.wayne.musicdeck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class LegalBottomSheetFragment : BottomSheetDialogFragment() {

    private val updateManager: com.wayne.musicdeck.update.UpdateManager by inject()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Version
        val tvVersionCode = view.findViewById<TextView>(R.id.tvVersionCode)
        val currentVersion: String = try {
            val ctx = requireContext()
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "2.10.0"
        } catch (e: Exception) {
            "2.10.0"
        }
        tvVersionCode.text = currentVersion

        // Check for Updates
        val btnCheckUpdates = view.findViewById<View>(R.id.btnCheckUpdates)
        val tvUpdateStatus = view.findViewById<TextView>(R.id.tvUpdateStatus)
        val progressChecking = view.findViewById<View>(R.id.progressCheckingUpdates)
        val iconUpdate = view.findViewById<View>(R.id.iconUpdateStatus)

        btnCheckUpdates.setOnClickListener {
            progressChecking.visibility = View.VISIBLE
            iconUpdate.visibility = View.GONE
            tvUpdateStatus.text = "Checking GitHub..."

            viewLifecycleOwner.lifecycleScope.launch {
                val result = updateManager.checkForUpdate()
                progressChecking.visibility = View.GONE
                iconUpdate.visibility = View.VISIBLE

                result.onSuccess { info ->
                    if (info.isUpdateAvailable) {
                        tvUpdateStatus.text = "Update available: ${info.latestVersion}"
                        updateManager.showUpdateDialog(requireActivity(), info)
                    } else {
                        tvUpdateStatus.text = "MusicDeck is up to date (v$currentVersion)"
                        android.widget.Toast.makeText(requireContext(), "You're on the latest version!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    tvUpdateStatus.text = "Check failed. Tap to retry."
                    android.widget.Toast.makeText(requireContext(), "Update check failed: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        // Terms & Privacy
        view.findViewById<View>(R.id.btnTermsPrivacy).setOnClickListener {
            TermsBottomSheetFragment().show(parentFragmentManager, "terms")
        }

        // Open Source Licenses
        view.findViewById<View>(R.id.btnLicenses).setOnClickListener {
            LicensesBottomSheetFragment().show(parentFragmentManager, "licenses")
        }

        // GitHub Repository
        view.findViewById<View>(R.id.btnGitHub).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/WayneChibeu/MusicDeck"))
            startActivity(intent)
        }
    }
}
