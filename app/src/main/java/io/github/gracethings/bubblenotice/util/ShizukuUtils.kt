package io.github.gracethings.bubblenotice.util

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuUtils {

    // 检查 Shizuku 是否处于可用状态 / Check if Shizuku binder is active.
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    // 检查是否已获得 Shizuku 授权 / Check if Shizuku permissions are granted.
    fun isShizukuPermissionGranted(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            false
        }
    }

    // 利用 Shizuku Shell 权限以小窗模式启动目标应用并设置大小 / Launch the package in freeform window mode via Shizuku with custom bounds.
    fun launchInFreeform(context: Context, packageName: String, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        if (!isShizukuAvailable() || !isShizukuPermissionGranted()) {
            return false
        }
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
        val component = launchIntent.component ?: return false
        val componentString = component.flattenToShortString() // "package/activity"

        return try {
            // 通过 shell 链式命令启动，延迟 0.3 秒待任务创建后获取 Task ID 并缩放
            // Launch via shell command chain, sleep 0.3s, extract Task ID, and resize task.
            val shellCmd = "am start --windowingMode 5 -n $componentString && sleep 0.3 && TASK_ID=\$(am stack list | grep -E 'taskId=[0-9]+: $packageName/' | head -n 1 | sed -E 's/.*taskId=([0-9]+):.*/\\1/') && if [ ! -z \"\$TASK_ID\" ]; then am task resizeable \"\$TASK_ID\" 2 && am task resize \"\$TASK_ID\" $left $top $right $bottom; fi"
            val cmd = arrayOf("sh", "-c", shellCmd)

            // 使用反射调用隐藏的 Shizuku.newProcess 方法来执行 shell 命令
            // Call hidden Shizuku.newProcess method via reflection to execute shell commands.
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, cmd, null, null) as java.lang.Process
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
