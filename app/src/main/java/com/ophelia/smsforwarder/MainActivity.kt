package com.ophelia.smsforwarder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.ophelia.smsforwarder.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: SettingsStore

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        applyWindowInsets()

        store = SettingsStore(this)
        ErrorNotifier.ensureChannel(this)
        populate(store.load())

        binding.buttonSave.setOnClickListener {
            if (saveFromForm() != null) toast(getString(R.string.toast_saved))
        }
        binding.buttonTest.setOnClickListener { sendTestMail() }
        binding.buttonPermission.setOnClickListener { requestPermissions() }
        binding.buttonBattery.setOnClickListener { requestIgnoreBatteryOptimization() }
        binding.buttonClearLog.setOnClickListener {
            store.clearLog()
            refreshLog()
        }

        if (!hasSmsPermission()) requestPermissions()
    }

    /**
     * Android 15 (targetSdk 35) 以降は常に edge-to-edge で描画され、adjustResize も効かないため、
     * ステータスバー・ナビゲーションバー・キーボードの分だけ自分で余白を空ける。
     * キーボード分はルートの下パディングにして ScrollView 自体を縮め、
     * フォーカス中の入力欄が自動でスクロールされるようにする。
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.root.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom),
            )
            binding.appBar.updatePadding(top = bars.top)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshLog()
    }

    // ---- フォーム ----

    private fun populate(s: MailSettings) = with(binding) {
        switchEnabled.isChecked = s.enabled
        editHost.setText(s.smtpHost)
        editPort.setText(s.smtpPort.toString())
        if (s.useSsl) radioSsl.isChecked = true else radioStarttls.isChecked = true
        editUsername.setText(s.username)
        editPassword.setText(s.password)
        editFrom.setText(s.fromAddress)
        editTo.setText(s.toAddress)
        editSubjectPrefix.setText(s.subjectPrefix)
    }

    /** 入力を検証して保存する。エラーがあれば該当欄に表示して null を返す。 */
    private fun saveFromForm(): MailSettings? {
        val settings = readForm() ?: return null
        store.save(settings)
        return settings
    }

    private fun readForm(): MailSettings? = with(binding) {
        var ok = true

        fun required(layout: TextInputLayout): String {
            val value = layout.editText?.text?.toString()?.trim().orEmpty()
            if (value.isEmpty()) {
                layout.error = getString(R.string.error_required)
                ok = false
            } else {
                layout.error = null
            }
            return value
        }

        val host = required(layoutHost)
        val username = required(layoutUsername)
        val password = required(layoutPassword)
        val from = required(layoutFrom)
        val to = required(layoutTo)

        val port = editPort.text?.toString()?.trim()?.toIntOrNull()
        if (port == null || port !in 1..65535) {
            layoutPort.error = getString(R.string.error_port)
            ok = false
        } else {
            layoutPort.error = null
        }

        if (!ok || port == null) return null

        MailSettings(
            enabled = switchEnabled.isChecked,
            smtpHost = host,
            smtpPort = port,
            useSsl = radioSsl.isChecked,
            username = username,
            password = password,
            fromAddress = from,
            toAddress = to,
            subjectPrefix = editSubjectPrefix.text?.toString()?.trim().orEmpty(),
        )
    }

    // ---- テスト送信 ----

    private fun sendTestMail() {
        val settings = saveFromForm() ?: return
        binding.buttonTest.isEnabled = false
        toast(getString(R.string.toast_test_sending))

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MailSender.send(
                        settings,
                        ForwardWorker.buildSubject(
                            settings.subjectPrefix,
                            getString(R.string.mail_subject_test),
                        ),
                        getString(R.string.mail_body_test),
                    )
                }
            }
            binding.buttonTest.isEnabled = true
            result.fold(
                onSuccess = {
                    store.appendLog("テスト送信成功 → ${settings.toAddress}")
                    toast(getString(R.string.toast_test_ok))
                },
                onFailure = { e ->
                    val reason = e.message ?: e.javaClass.simpleName
                    store.appendLog("テスト送信失敗: $reason")
                    toast(getString(R.string.toast_test_ng, reason), long = true)
                },
            )
            refreshLog()
        }
    }

    // ---- 権限・電池最適化 ----

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun requestIgnoreBatteryOptimization() {
        if (isIgnoringBatteryOptimizations()) {
            toast(getString(R.string.toast_battery_already))
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun updateStatus() = with(binding) {
        val smsOk = hasSmsPermission()
        textPermissionStatus.setText(
            if (smsOk) R.string.status_permission_granted else R.string.status_permission_denied
        )
        buttonPermission.isEnabled = !smsOk

        val batteryOk = isIgnoringBatteryOptimizations()
        textBatteryStatus.setText(
            if (batteryOk) R.string.status_battery_ok else R.string.status_battery_ng
        )
        buttonBattery.isEnabled = !batteryOk
    }

    // ---- ログ ----

    private fun refreshLog() {
        val lines = store.readLog()
        binding.textLog.text =
            if (lines.isEmpty()) getString(R.string.log_empty) else lines.joinToString("\n")
    }

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
