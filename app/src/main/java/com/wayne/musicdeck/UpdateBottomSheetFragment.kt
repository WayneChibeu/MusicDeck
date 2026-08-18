package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.wayne.musicdeck.data.model.GitHubAsset
import com.wayne.musicdeck.data.model.GitHubRelease
import com.wayne.musicdeck.utils.AppUpdateManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class UpdateBottomSheetFragment : BottomSheetDialogFragment() {

    private var release: GitHubRelease? = null
    private var apkAsset: GitHubAsset? = null
    private var downloadedApkFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            release = pendingRelease
            apkAsset = pendingAsset
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.layout_update_bottom_sheet, container, false)
        } catch (e: Throwable) {
            e.printStackTrace()
            dismissAllowingStateLoss()
            android.widget.FrameLayout(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val rel = release ?: run {
                dismissAllowingStateLoss()
                return
            }
            val asset = apkAsset ?: run {
                dismissAllowingStateLoss()
                return
            }

            val tvUpdateVersion = view.findViewById<TextView>(R.id.tvUpdateVersion)
            val tvUpdateSize = view.findViewById<TextView>(R.id.tvUpdateSize)
            val tvChangelog = view.findViewById<TextView>(R.id.tvChangelog)
            val layoutProgress = view.findViewById<LinearLayout>(R.id.layoutProgress)
            val tvProgressStatus = view.findViewById<TextView>(R.id.tvProgressStatus)
            val tvProgressPercent = view.findViewById<TextView>(R.id.tvProgressPercent)
            val progressBarDownload = view.findViewById<ProgressBar>(R.id.progressBarDownload)
            val btnLater = view.findViewById<Button>(R.id.btnLater)
            val btnDownloadInstall = view.findViewById<Button>(R.id.btnDownloadInstall)

            val versionName = if (!rel.tagName.startsWith("v", ignoreCase = true)) "v${rel.tagName}" else rel.tagName
            tvUpdateVersion?.text = "MusicDeck $versionName"

            val sizeMb = String.format(Locale.US, "%.1f MB", asset.size / (1024.0 * 1024.0))
            tvUpdateSize?.text = sizeMb

            val rawNotes = if (!rel.body.isNullOrBlank()) {
                rel.body.trim()
            } else {
                "Performance improvements, design polish, and bug fixes."
            }
            tvChangelog?.text = formatReleaseNotes(rawNotes)

            btnLater?.setOnClickListener {
                dismissAllowingStateLoss()
            }

            btnDownloadInstall?.setOnClickListener {
                if (downloadedApkFile != null && downloadedApkFile!!.exists()) {
                    AppUpdateManager.installApk(requireActivity(), downloadedApkFile!!)
                    dismissAllowingStateLoss()
                    return@setOnClickListener
                }

                // Start Download
                layoutProgress?.visibility = View.VISIBLE
                btnDownloadInstall.isEnabled = false
                btnDownloadInstall.text = "Downloading..."
                btnLater?.isEnabled = false

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val result = AppUpdateManager.downloadApk(requireContext(), asset) { percent ->
                            progressBarDownload?.progress = percent
                            tvProgressPercent?.text = "$percent%"
                        }

                        if (result.isSuccess) {
                            val file = result.getOrNull()
                            downloadedApkFile = file
                            tvProgressStatus?.text = "Download complete!"
                            btnDownloadInstall.isEnabled = true
                            btnDownloadInstall.text = "Install Now"
                            btnLater?.isEnabled = true

                            if (file != null) {
                                AppUpdateManager.installApk(requireActivity(), file)
                            }
                        } else {
                            layoutProgress?.visibility = View.GONE
                            btnDownloadInstall.isEnabled = true
                            btnDownloadInstall.text = "Retry Download"
                            btnLater?.isEnabled = true
                            val err = result.exceptionOrNull()?.localizedMessage ?: "Download failed"
                            Toast.makeText(requireContext(), "Update failed: $err", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Throwable) {
                        layoutProgress?.visibility = View.GONE
                        btnDownloadInstall.isEnabled = true
                        btnDownloadInstall.text = "Retry Download"
                        btnLater?.isEnabled = true
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            dismissAllowingStateLoss()
        }
    }

    private fun formatReleaseNotes(markdown: String): CharSequence {
        return try {
            val lines = markdown.lines().map { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("###") -> "<b>${trimmed.removePrefix("###").trim()}</b>"
                    trimmed.startsWith("##") -> "<b>${trimmed.removePrefix("##").trim()}</b>"
                    trimmed.startsWith("#") -> "<b>${trimmed.removePrefix("#").trim()}</b>"
                    trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ").trim()}"
                    trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ").trim()}"
                    else -> trimmed
                }
            }
            val joined = lines.joinToString("<br>")
                .replace(Regex("\\*\\*(.*?)\\*\\*")) { match -> "<b>${match.groupValues[1]}</b>" }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(joined, android.text.Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(joined)
            }
        } catch (e: Throwable) {
            markdown
        }
    }

    companion object {
        var pendingRelease: GitHubRelease? = null
        var pendingAsset: GitHubAsset? = null

        fun newInstance(release: GitHubRelease, asset: GitHubAsset): UpdateBottomSheetFragment {
            pendingRelease = release
            pendingAsset = asset
            return UpdateBottomSheetFragment()
        }
    }
}
