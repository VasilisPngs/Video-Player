package com.vasilispngs.videoplayer.baselineprofile

import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.textAsString
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test

internal const val PACKAGE_NAME = "com.vasilispngs.videoplayer"
private const val VIDEO = "sample.mp4"
private const val WAIT_MS = 20_000L
private const val POLL_MS = 500L
private const val POLLS = 60

class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun screens() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()
        uiAutomator {
            onElement { textAsString() == "Ρυθμίσεις" }.click()
            onElement { textAsString() == "Θέμα" }.click()
            onElement { textAsString() == "Ακύρωση" }.click()
            pressBack()
        }
    }

    @Test
    fun playback() {
        val drive = mountDrive()
        rule.collect(packageName = PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            uiAutomator {
                onElement { textAsString() == drive }.click()
                onElement { textAsString() == VIDEO }.click()
            }
            device.wait(Until.gone(By.text(VIDEO)), WAIT_MS)
            device.wait(Until.hasObject(By.text(VIDEO)), WAIT_MS)
            device.pressBack()
        }
    }
}

private fun mountDrive(): String {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    shell("sm set-virtual-disk true")
    val disk = poll { shell("sm list-disks adoptable").lineSequence().map(String::trim).firstOrNull(String::isNotEmpty) }
    shell("sm partition $disk public")
    val storage = instrumentation.context.getSystemService(StorageManager::class.java)
    val volume = poll { storage.storageVolumes.firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED && it.directory != null } }
    val (output, input) = automation.executeShellCommandRw("dd of=${volume.directory}/$VIDEO")
    ParcelFileDescriptor.AutoCloseOutputStream(input).use { sink -> instrumentation.context.assets.open(VIDEO).use { it.copyTo(sink) } }
    val _ = ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    shell("appops set $PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow")
    return volume.getDescription(instrumentation.context)
}

private fun <T : Any> poll(read: () -> T?): T {
    repeat(POLLS) {
        read()?.let { return it }
        SystemClock.sleep(POLL_MS)
    }
    error("Nothing after ${POLLS * POLL_MS} ms")
}
