package com.wayne.musicdeck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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
