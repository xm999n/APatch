package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Debug
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.topjohnwu.superuser.CallbackList
import me.bmax.apatch.ui.CrashHandleActivity
import me.bmax.apatch.util.APatchCli
import me.bmax.apatch.util.APatchKeyHelper
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.getRootShell
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.verifyAppSignature
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.random.Random

lateinit var apApp: APApplication

const val TAG = "APatch"

class APApplication : Application(), Thread.UncaughtExceptionHandler {
    lateinit var okhttpClient: OkHttpClient
    
    private var securityToken: Long = 0L
    private var verificationPassed = false
    private val checkInterval = 30000L // 30秒
    
    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    // 反调试检测
    private fun antiDebug(): Boolean {
        // 检测 1: Debug 标志
        if (Debug.isDebuggerConnected()) return false
        
        // 检测 2: TracerPid (检测 ptrace)
        try {
            val status = File("/proc/self/status")
            if (status.exists()) {
                BufferedReader(FileReader(status)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.startsWith("TracerPid:")) {
                            val pid = line!!.substring(10).trim().toIntOrNull() ?: 0
                            if (pid != 0) return false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return false
        }
        
        // 检测 3: Frida 端口
        try {
            val tcp = File("/proc/net/tcp")
            if (tcp.exists()) {
                val content = tcp.readText()
                // Frida 默认端口 27042 (0x6992)
                if (content.contains(":6992") || content.contains(":D6F2")) {
                    return false
                }
            }
        } catch (e: Exception) {
            return false
        }
        
        // 检测 4: Frida 相关文件
        val suspiciousFiles = arrayOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/sdcard/frida-server",
            "/system/bin/frida-server",
            "/system/xbin/frida-server"
        )
        
        for (file in suspiciousFiles) {
            if (File(file).exists()) return false
        }
        
        // 检测 5: Frida 相关库
        try {
            val maps = File("/proc/self/maps")
            if (maps.exists()) {
                val content = maps.readText()
                if (content.contains("frida") || 
                    content.contains("gum-js") || 
                    content.contains("gum_js")) {
                    return false
                }
            }
        } catch (e: Exception) {
            return false
        }
        
        return true
    }

    // 分段加密的签名 (三重加密)
    private fun d1(): String {
        val s = arrayOf(
            byteArrayOf(0x68, 0x49, 0x4a, 0x62),
            byteArrayOf(0x72, 0x56, 0x47, 0x59),
            byteArrayOf(0x44, 0x54, 0x46, 0x6e),
            byteArrayOf(0x68, 0x46, 0x4d, 0x6a),
            byteArrayOf(0x4f, 0x47, 0x35, 0x69),
            byteArrayOf(0x56, 0x6a, 0x42, 0x49),
            byteArrayOf(0x64, 0x48, 0x56, 0x77),
            byteArrayOf(0x64, 0x58, 0x70, 0x72),
            byteArrayOf(0x55, 0x47, 0x78, 0x34),
            byteArrayOf(0x56, 0x46, 0x56, 0x6f),
            byteArrayOf(0x63, 0x6a, 0x59, 0x39),
            byteArrayOf(0x5a, 0x30, 0x49, 0x72),
            byteArrayOf(0x57, 0x58, 0x6b, 0x39)
        )
        
        val m = intArrayOf(10, 3, 1, 6, 8, 4, 9, 0, 7, 2, 5, 11, 12)
        val k1 = 0x2a
        val combined = m.flatMap { idx ->
            s[idx].map { b -> (b.toInt() xor k1).toByte() }
        }.toByteArray()
        
        val stage1 = String(combined, Charsets.UTF_8)
        val k2 = 0x15
        val finalBytes = stage1.toByteArray().map { b ->
            (b.toInt() xor k2).toByte()
        }.toByteArray()
        
        return String(finalBytes, Charsets.UTF_8)
    }

    // 备用解密方法 (不同的加密方式)
    private fun d2(): String {
        val encoded = "REH3eiyyMUGbrtupH8F10GNbTUhcjXkvMTgaPLxgB+Y="
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(encoded.toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }
        
        // 返回原始签名的 hash,用于交叉验证
        return hash
    }

    // 生成安全令牌
    private fun generateToken(signature: String): Long {
        val data = "$signature${System.currentTimeMillis() / 10000}${packageName}"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .take(8)
            .fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
    }

    // 验证安全令牌
    private fun verifyToken(token: Long): Boolean {
        val currentToken = generateToken(d1())
        val diff = Math.abs(currentToken - token)
        // 允许一定的时间偏差 (10秒窗口)
        return diff < 1000
    }

    // 主验证逻辑 (不直接返回 boolean,使用状态码)
    private fun v1(): Int {
        if (!antiDebug()) return 0x01
        
        val sig1 = d1()
        if (!verifyAppSignature(sig1)) return 0x02
        
        // 生成并存储令牌
        securityToken = generateToken(sig1)
        verificationPassed = true
        
        return 0xFF // 成功标志
    }

    // 二次验证 (使用不同的检查方式)
    private fun v2(): Int {
        if (!verificationPassed) return 0x03
        if (!verifyToken(securityToken)) return 0x04
        if (!antiDebug()) return 0x05
        
        // 验证签名 hash
        val sig = d1()
        val expectedHash = "5c8a8c7e8f7e4f4a9b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a"
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(sig.toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }
        
        if (actualHash != expectedHash) return 0x06
        
        return 0xFF
    }

    // 随机时间的后台验证
    private fun startBackgroundVerification() {
        thread(isDaemon = true) {
            while (true) {
                try {
                    // 随机延迟 (防止被预测)
                    Thread.sleep(checkInterval + Random.nextLong(-5000, 5000))
                    
                    if (v2() != 0xFF) {
                        Log.e(TAG, "Security verification failed")
                        // 静默退出,不给任何提示
                        Process.killProcess(Process.myPid())
                        exitProcess(0)
                    }
                } catch (e: Exception) {
                    Process.killProcess(Process.myPid())
                    exitProcess(0)
                }
            }
        }
    }

    // 混淆的失败处理
    private fun handleFailure(code: Int) {
        // 不要立即退出,添加一些干扰
        thread {
            repeat(Random.nextInt(3, 8)) {
                Thread.sleep(Random.nextLong(100, 500))
                // 执行一些无意义的操作
                val dummy = System.currentTimeMillis()
                Log.d(TAG, "Check: ${dummy % 1000}")
            }
            
            // 多种退出方式随机选择
            when (Random.nextInt(3)) {
                0 -> {
                    val intent = Intent(Intent.ACTION_DELETE)
                    intent.data = "package:$packageName".toUri()
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    startActivity(intent)
                }
                1 -> Process.killProcess(Process.myPid())
                else -> exitProcess(0)
            }
        }
    }

    enum class State {
        UNKNOWN_STATE,
        KERNELPATCH_INSTALLED, KERNELPATCH_NEED_UPDATE, KERNELPATCH_NEED_REBOOT, KERNELPATCH_UNINSTALLING,
        ANDROIDPATCH_NOT_INSTALLED, ANDROIDPATCH_INSTALLED, ANDROIDPATCH_INSTALLING, ANDROIDPATCH_NEED_UPDATE, ANDROIDPATCH_UNINSTALLING,
    }

    companion object {
        const val APD_PATH = "/data/adb/apd"

        @Deprecated("No more KPatch ELF from 0.11.0-dev")
        const val KPATCH_PATH = "/data/adb/kpatch"
        const val SUPERCMD = "/system/bin/truncate"
        const val APATCH_FOLDER = "/data/adb/ap/"
        private const val APATCH_BIN_FOLDER = APATCH_FOLDER + "bin/"
        private const val APATCH_LOG_FOLDER = APATCH_FOLDER + "log/"
        private const val APD_LINK_PATH = APATCH_BIN_FOLDER + "apd"
        const val PACKAGE_CONFIG_FILE = APATCH_FOLDER + "package_config"
        const val SU_PATH_FILE = APATCH_FOLDER + "su_path"
        const val SAFEMODE_FILE = "/dev/.safemode"
        private const val NEED_REBOOT_FILE = "/dev/.need_reboot"
        const val GLOBAL_NAMESPACE_FILE = "/data/adb/.global_namespace_enable"
        const val LITE_MODE_FILE = "/data/adb/.litemode_enable"
        const val FORCE_OVERLAYFS_FILE = "/data/adb/.overlayfs_enable"
        const val KPMS_DIR = APATCH_FOLDER + "kpms/"

        @Deprecated("Use 'apd -V'")
        const val APATCH_VERSION_PATH = APATCH_FOLDER + "version"
        private const val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FOLDER + "magiskpolicy"
        private const val BUSYBOX_BIN_PATH = APATCH_BIN_FOLDER + "busybox"
        private const val RESETPROP_BIN_PATH = APATCH_BIN_FOLDER + "resetprop"
        private const val MAGISKBOOT_BIN_PATH = APATCH_BIN_FOLDER + "magiskboot"
        const val DEFAULT_SCONTEXT = "u:r:untrusted_app:s0"
        const val MAGISK_SCONTEXT = "u:r:magisk:s0"

        private const val DEFAULT_SU_PATH = "/system/bin/kp"
        private const val LEGACY_SU_PATH = "/system/bin/su"

        const val SP_NAME = "config"
        private const val SHOW_BACKUP_WARN = "show_backup_warning"
        lateinit var sharedPreferences: SharedPreferences

        private val logCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                Log.d(TAG, s.toString())
            }
        }

        private val _kpStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val kpStateLiveData: LiveData<State> = _kpStateLiveData

        private val _apStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val apStateLiveData: LiveData<State> = _apStateLiveData

        @Suppress("DEPRECATION")
        fun uninstallApatch() {
            if (_apStateLiveData.value != State.ANDROIDPATCH_INSTALLED) return
            _apStateLiveData.value = State.ANDROIDPATCH_UNINSTALLING

            Natives.resetSuPath(DEFAULT_SU_PATH)

            val cmds = arrayOf(
                "rm -f $APD_PATH",
                "rm -f $KPATCH_PATH",
                "rm -rf $APATCH_BIN_FOLDER",
                "rm -rf $APATCH_LOG_FOLDER",
                "rm -rf $APATCH_VERSION_PATH",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            Log.d(TAG, "APatch uninstalled...")
            if (_kpStateLiveData.value == State.UNKNOWN_STATE) {
                _apStateLiveData.postValue(State.UNKNOWN_STATE)
            } else {
                _apStateLiveData.postValue(State.ANDROIDPATCH_NOT_INSTALLED)
            }
        }

        @Suppress("DEPRECATION")
        fun installApatch() {
            val state = _apStateLiveData.value
            if (state != State.ANDROIDPATCH_NOT_INSTALLED && state != State.ANDROIDPATCH_NEED_UPDATE) {
                return
            }
            _apStateLiveData.value = State.ANDROIDPATCH_INSTALLING
            val nativeDir = apApp.applicationInfo.nativeLibraryDir

            Natives.resetSuPath(LEGACY_SU_PATH)

            val cmds = arrayOf(
                "mkdir -p $APATCH_BIN_FOLDER",
                "mkdir -p $APATCH_LOG_FOLDER",

                "cp -f ${nativeDir}/libapd.so $APD_PATH",
                "chmod +x $APD_PATH",
                "ln -s $APD_PATH $APD_LINK_PATH",
                "restorecon $APD_PATH",

                "cp -f ${nativeDir}/libmagiskpolicy.so $MAGISKPOLICY_BIN_PATH",
                "chmod +x $MAGISKPOLICY_BIN_PATH",
                "cp -f ${nativeDir}/libresetprop.so $RESETPROP_BIN_PATH",
                "chmod +x $RESETPROP_BIN_PATH",
                "cp -f ${nativeDir}/libbusybox.so $BUSYBOX_BIN_PATH",
                "chmod +x $BUSYBOX_BIN_PATH",
                "cp -f ${nativeDir}/libmagiskboot.so $MAGISKBOOT_BIN_PATH",
                "chmod +x $MAGISKBOOT_BIN_PATH",

                "touch $PACKAGE_CONFIG_FILE",
                "touch $SU_PATH_FILE",
                "[ -s $SU_PATH_FILE ] || echo $LEGACY_SU_PATH > $SU_PATH_FILE",
                "echo ${Version.getManagerVersion().second} > $APATCH_VERSION_PATH",
                "restorecon -R $APATCH_FOLDER",

                "${nativeDir}/libmagiskpolicy.so --magisk --live",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            APatchCli.refresh()

            Log.d(TAG, "APatch installed...")
            _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
        }

        fun markNeedReboot() {
            val result = rootShellForResult("touch $NEED_REBOOT_FILE")
            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
            Log.d(TAG, "mark reboot ${result.code}")
        }

        var superKey: String = ""
            set(value) {
                field = value
                val ready = Natives.nativeReady(value)
                _kpStateLiveData.value =
                    if (ready) State.KERNELPATCH_INSTALLED else State.UNKNOWN_STATE
                _apStateLiveData.value =
                    if (ready) State.ANDROIDPATCH_NOT_INSTALLED else State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _kpStateLiveData.value)
                if (!ready) return

                APatchKeyHelper.writeSPSuperKey(value)

                thread {
                    val rc = Natives.su(0, null)
                    if (!rc) {
                        Log.e(TAG, "Native.su failed")
                        return@thread
                    }

                    val buildV = Version.getKpImg()
                    val installedV = Version.installedKPTime()

                    Log.d(TAG, "kp installed version: ${installedV}, build version: $buildV")

                    if (buildV != installedV) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_UPDATE)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    if (File(NEED_REBOOT_FILE).exists()) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    val mgv = Version.getManagerVersion().second
                    val installedApdVInt = Version.installedApdVUInt()
                    Log.d(TAG, "manager version: $mgv, installed apd version: $installedApdVInt")

                    if (Version.installedApdVInt > 0) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
                    }

                    if (Version.installedApdVInt > 0 && mgv.toInt() != Version.installedApdVInt) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_NEED_UPDATE)
                        val suPathFile = File(SU_PATH_FILE)
                        if (suPathFile.exists()) {
                            val suPath = suPathFile.readLines()[0].trim()
                            if (Natives.suPath() != suPath) {
                                Log.d(TAG, "su path: $suPath")
                                Natives.resetSuPath(suPath)
                            }
                        }
                    }
                    Log.d(TAG, "ap state: " + _apStateLiveData.value)

                    return@thread
                }
            }
    }

    override fun onCreate() {
        super.onCreate()
        apApp = this

        val isArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        if (!isArm64) {
            Toast.makeText(applicationContext, "Unsupported architecture!", Toast.LENGTH_LONG).show()
            Thread.sleep(5000)
            exitProcess(0)
        }

        // 多点验证
        if (!BuildConfig.DEBUG) {
            val result = v1()
            if (result != 0xFF) {
                handleFailure(result)
                return
            }
            
            // 延迟二次验证
            thread {
                Thread.sleep(Random.nextLong(2000, 5000))
                val result2 = v2()
                if (result2 != 0xFF) {
                    handleFailure(result2)
                    return@thread
                }
                
                // 启动后台持续验证
                startBackgroundVerification()
            }
        }

        sharedPreferences = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        APatchKeyHelper.setSharedPreferences(sharedPreferences)
        superKey = APatchKeyHelper.readSPSuperKey()

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "APatch/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
    }

    fun getBackupWarningState(): Boolean {
        return sharedPreferences.getBoolean(SHOW_BACKUP_WARN, true)
    }

    fun updateBackupWarningState(state: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_BACKUP_WARN, state) }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val exceptionMessage = Log.getStackTraceString(e)
        val threadName = t.name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")
        val intent = Intent(this, CrashHandleActivity::class.java).apply {
            putExtra("exception_message", exceptionMessage)
            putExtra("thread", threadName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        exitProcess(10)
    }
}