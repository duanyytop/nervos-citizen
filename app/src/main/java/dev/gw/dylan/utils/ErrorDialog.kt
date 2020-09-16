package dev.gw.dylan.utils

/**
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/utilities/ErrorDialog.java
 */

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import dev.gw.dylan.R

class ErrorDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val activity = activity
        return AlertDialog.Builder(activity)
            .setMessage(arguments.getString(ARG_MESSAGE))
            .setPositiveButton(R.string.ok) { _, _ -> activity.finish() }
            .create()
    }

    companion object {
        private const val ARG_MESSAGE = "message"
        fun newInstance(message: String?): ErrorDialog {
            val dialog = ErrorDialog()
            val args = Bundle()
            args.putString(ARG_MESSAGE, message)
            dialog.arguments = args
            return dialog
        }
    }
}