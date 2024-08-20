package com.example.evision_phising

import android.app.AlertDialog
import android.content.Context

class AlertWarn(private val context: Context) {
    fun showAlert(message: String) {
        AlertDialog.Builder(context)
            .setTitle("경고")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()  // 확인 버튼 클릭 시 경고창 종료
            }
            .show()
    }
}
