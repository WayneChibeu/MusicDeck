package com.wayne.musicdeck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
        try {
            val ctx = requireContext()
            val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            tvVersionCode.text = version
        } catch (e: Exception) {
            tvVersionCode.text = "2.7.0"
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
