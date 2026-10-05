package net.rslvd.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.io.File

/**
 * Owns the single PTY-backed shell session. The session outlives the Activity's
 * composition so rotating or backgrounding does not kill running programs; a
 * [TerminalView] attaches/detaches as the UI comes and goes.
 */
class TerminalHost private constructor(private val app: Context) : TerminalSessionClient {

    private val main = Handler(Looper.getMainLooper())

    /** The view currently displaying the session, if any. */
    var view: TerminalView? = null
        set(value) {
            field = value
            value?.attachSession(session)
        }

    var onFinished: ((exitStatus: Int) -> Unit)? = null

    var session: TerminalSession = create()
        private set

    fun restart() {
        session.finishIfRunning()
        session = create()
        view?.attachSession(session)
        view?.onScreenUpdated()
    }

    private fun create(): TerminalSession {
        val prefix = BootstrapInstaller.prefixDir(app)
        val shell = listOf("bin/bash", "bin/sh", "bin/dash")
            .map { File(prefix, it) }
            .firstOrNull { it.exists() }
            ?.absolutePath
        val env: MutableMap<String, String>
        val cwd: String
        val login: String
        val exe: String
        if (shell != null) {
            exe = shell
            env = BootstrapInstaller.environment(app).toMutableMap()
            cwd = env["HOME"] ?: app.filesDir.absolutePath
            login = "-l"
        } else {
            // Environment not (yet) installed: fall back to the Android system shell.
            exe = "/system/bin/sh"
            env = systemEnvironment()
            cwd = app.filesDir.absolutePath
            login = ""
        }
        File(cwd).mkdirs()
        env["COLORTERM"] = "truecolor"
        for (k in PASSTHROUGH) if (k !in env) System.getenv(k)?.let { env[k] = it }
        val envArray = env.map { (k, v) -> "$k=$v" }.toTypedArray()
        val args = arrayOf(exe, "-c", "exec \"$exe\" $login")
        return TerminalSession(exe, cwd, args, envArray, TRANSCRIPT_ROWS, this)
    }

    private fun systemEnvironment(): MutableMap<String, String> = mutableMapOf(
        "HOME" to app.filesDir.absolutePath,
        "TMPDIR" to app.cacheDir.absolutePath,
        "PATH" to "/system/bin:/system/xbin",
        "TERM" to "xterm-256color",
        "LANG" to "en_US.UTF-8",
    )

    // ---- TerminalSessionClient ----

    override fun onTextChanged(changedSession: TerminalSession) {
        view?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val status = finishedSession.exitStatus
        main.post { onFinished?.invoke(status) }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("rslvd terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() ?: return
        session?.emulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {
        view?.onScreenUpdated()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        view?.setTerminalCursorBlinkerState(state, true)
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }

    companion object {
        private const val TAG = "TerminalHost"
        private const val TRANSCRIPT_ROWS = 4000

        private val PASSTHROUGH = listOf(
            "ANDROID_DATA", "ANDROID_ROOT", "ANDROID_ART_ROOT", "ANDROID_I18N_ROOT",
            "ANDROID_RUNTIME_ROOT", "ANDROID_TZDATA_ROOT", "BOOTCLASSPATH",
            "DEX2OATBOOTCLASSPATH", "EXTERNAL_STORAGE",
        )

        @Volatile
        private var instance: TerminalHost? = null

        fun get(context: Context): TerminalHost =
            instance ?: synchronized(this) {
                instance ?: TerminalHost(context.applicationContext).also { instance = it }
            }
    }
}
