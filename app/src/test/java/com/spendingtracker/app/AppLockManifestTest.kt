package com.spendingtracker.app

import android.app.Application
import android.content.pm.PackageManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app declares itself exempt from automatic enrollment/protection by
 * compatible AppLock implementations through application-level manifest
 * metadata. This is a self-declared opt-out, not a security feature.
 *
 * These tests verify the declaration two ways:
 *  - the source manifest has the exact metadata name and Boolean value;
 *  - Robolectric loads the MERGED manifest of the tested variant, so the
 *    packaged metadata is what the shipped APK contains (the CI build also
 *    greps the built APK manifests with aapt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class AppLockManifestTest {

    private val marker = "dev.pranav.applock.AUTOLOCK_EXEMPT"

    @Test
    fun sourceManifestDeclaresExemptionWithExactNameAndBooleanTrue() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest not found at ${manifest.absolutePath}", manifest.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val applications = doc.getElementsByTagName("application")
        assertTrue("no <application> element", applications.length > 0)
        val metaNodes = doc.getElementsByTagName("meta-data")
        var found = false
        for (i in 0 until metaNodes.length) {
            val node = metaNodes.item(i)
            val attrs = node.attributes
            val name = attrs.getNamedItem("android:name")?.nodeValue
            if (name == marker) {
                found = true
                // Must sit inside <application>, not the manifest root.
                var parent = node.parentNode
                var inApplication = false
                while (parent != null) {
                    if (parent.nodeName == "application") { inApplication = true; break }
                    parent = parent.parentNode
                }
                assertTrue("$marker must be inside <application>", inApplication)
                val value = attrs.getNamedItem("android:value")?.nodeValue
                assertTrue("$marker must be android:value=\"true\" (was: $value)", value == "true")
            }
        }
        assertTrue("$marker missing from src/main/AndroidManifest.xml", found)
    }

    @Test
    fun mergedManifestCarriesTheExemptionMetadata() {
        val context = RuntimeEnvironment.getApplication()
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val meta = appInfo.metaData
        assertNotNull("the merged manifest must carry application metadata", meta)
        assertTrue(
            "$marker must be present in the merged manifest",
            meta!!.containsKey(marker)
        )
        // The value must be truthy in whichever representation the test
        // harness stores manifest values (the shipped APK compiles it to a
        // typed Boolean true; the CI aapt check inspects the built artifacts).
        val value = meta.get(marker)
        assertTrue("$marker must be true (was: $value)", value == true || value == "true")
    }
}
