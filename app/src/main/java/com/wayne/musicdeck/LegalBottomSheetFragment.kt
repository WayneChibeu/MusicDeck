package com.wayne.musicdeck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wayne.musicdeck.utils.AppUpdateManager
import kotlinx.coroutines.launch

class LegalBottomSheetFragment : BottomSheetDialogFragment() {

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
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "2.8.0"
        } catch (e: Exception) {
            "2.8.0"
        }
        tvVersionCode.text = currentVersion

        // Check for Updates
        val btnCheckUpdates = view.findViewById<View>(R.id.btnCheckUpdates)
        val tvUpdateStatus = view.findViewById<TextView>(R.id.tvUpdateStatus)
        val ivUpdateIcon = view.findViewById<ImageView>(R.id.ivUpdateIcon)

        btnCheckUpdates.setOnClickListener {
            val rotateAnim = RotateAnimation(
                0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 800
                repeatCount = Animation.INFINITE
            }
            ivUpdateIcon.startAnimation(rotateAnim)
            tvUpdateStatus.text = "Checking for latest release..."
            btnCheckUpdates.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val result = AppUpdateManager.checkForUpdates(currentVersion)
                ivUpdateIcon.clearAnimation()
                btnCheckUpdates.isEnabled = true

                when (result) {
                    is AppUpdateManager.CheckResult.NewUpdate -> {
                        tvUpdateStatus.text = "New update available: ${result.release.tagName}"
                        try {
                            UpdateBottomSheetFragment.newInstance(result.release, result.apkAsset)
                                .show(parentFragmentManager, "update_dialog")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    is AppUpdateManager.CheckResult.UpToDate -> {
                        tvUpdateStatus.text = "MusicDeck is up to date (v$currentVersion)"
                        Toast.makeText(
                            requireContext(),
                            "You are using the latest version of MusicDeck (v$currentVersion)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is AppUpdateManager.CheckResult.Error -> {
                        tvUpdateStatus.text = "Check failed: ${result.message}"
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    }
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
