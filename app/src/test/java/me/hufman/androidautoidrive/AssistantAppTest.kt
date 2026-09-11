package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.mockito.kotlin.*
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.carapp.assistant.AssistantApp
import me.hufman.androidautoidrive.carapp.assistant.AssistantAppInfo
import me.hufman.androidautoidrive.carapp.assistant.AssistantController
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.androidautoidrive.carapp.AMCategory
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import java.io.ByteArrayInputStream

class AssistantAppTest {
	val assistantController = mock<AssistantController>()

	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_onlineservices_v2.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	val phoneAppResources = mock<PhoneAppResources> {
		on { getAppName(any()) } doReturn "Test AppName"
		on { getAppIcon(any()) } doReturn mock<Drawable>()
		on { getIconDrawable(any()) } doReturn mock<Drawable>()
	}

	val graphicsHelpers = mock<GraphicsHelpers> {
		on { isDark(any()) } doReturn false
		on { compress(isA<Drawable>(), any(), any(), any(), any()) } doAnswer { "Drawable{${it.arguments[1]}x${it.arguments[2]}}".toByteArray() }
		on { compress(isA<Bitmap>(), any(), any(), any(), any()) } doAnswer { "Bitmap{${it.arguments[1]}x${it.arguments[2]}}".toByteArray() }
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer

		val app = AssistantApp(iDriveConnectionStatus, securityAccess, carAppResources, assistantController, graphicsHelpers)
		app.onCreate()

		// verify the right icons were added
		assertEquals(0, mockServer.amApps.size)

		// add an assistant and try again
		whenever(assistantController.getAssistants()) doReturn setOf(
				AssistantAppInfo("Test App", mock(), "me.test.app")
		)
		app.onCreate()

		assertEquals(1, mockServer.amApps.size)
		assertEquals("androidautoidrive.assistant.me.test.app", mockServer.amApps[0])
	}

	@Test
	fun testAppClick() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer

		val assistant = AssistantAppInfo("Test App", mock(), "me.test.app")
		whenever(assistantController.getAssistants()) doReturn setOf(assistant)

		val app = AssistantApp(iDriveConnectionStatus, securityAccess, carAppResources, assistantController, graphicsHelpers)
		app.onCreate()

		assertEquals(1, mockServer.amApps.size)
		val appName = mockServer.amApps[0]
		IDriveConnection.mockRemotingClient?.am_onAppEvent(0, "", appName, BMWRemoting.AMEvent.AM_APP_START)

		verify(assistantController).triggerAssistant(assistant)
	}

	@Test
	fun assistantEqualityDeduplicatesAppIdentityDespiteDifferentLoadedIcons() {
		val first = AssistantAppInfo("Google", mock(), "com.google.android.googlequicksearchbox")
		val duplicate = AssistantAppInfo("Google", mock(), first.packageName)
		assertEquals(first, duplicate)
		assertEquals(duplicate, first)
		assertEquals(first.hashCode(), duplicate.hashCode())
		assertEquals(1, setOf(first, duplicate).size)
		assertNotEquals(first, first.copy(name = "Renamed assistant"))
		assertNotEquals(first, first.copy(packageName = "other.assistant"))
		assertFalse(first.equals(null))
		assertFalse(first.equals("Google"))
	}

	@Test
	fun carLabelClarifiesVoiceFunctionAndPreservesOriginalAppMetadata() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val assistant = AssistantAppInfo("Google", mock(), "com.google.android.googlequicksearchbox")
		whenever(assistantController.getAssistants()) doReturn setOf(assistant)
		val app = AssistantApp(iDriveConnectionStatus, securityAccess, carAppResources, assistantController, graphicsHelpers)
		app.onCreate()

		val shortcut = app.amAppList.getAppInfo(mockServer.amApps.single())!!
		assertEquals("Google (voice assistant)", shortcut.name)
		assertEquals("Google", assistant.name)
		assertSame(assistant, shortcut.assistant)
		assertSame(assistant.icon, shortcut.icon)
		assertEquals(assistant.packageName, shortcut.packageName)
		assertEquals(assistant.amAppIdentifier, shortcut.amAppIdentifier)
		assertEquals(AMCategory.ONLINE_SERVICES, shortcut.category)
		val metadata = app.amAppList.getAMInfo(shortcut)
		assertEquals("Google (voice assistant)", metadata[1])
		for (language in 101..123) assertEquals(shortcut.name, metadata[language])
	}

	@Test
	fun multipleVoiceCommandActivitiesProduceOneShortcutAndOneIconLoadPerPackage() {
		val packageName = "com.google.android.googlequicksearchbox"
		fun activity(packageName: String, className: String): ResolveInfo {
			val application = mock<ApplicationInfo>().apply { this.packageName = packageName }
			val activity = mock<ActivityInfo>().apply {
				this.packageName = packageName
				name = className
				applicationInfo = application
			}
			return mock<ResolveInfo>().apply { activityInfo = activity }
		}
		val activities = listOf(activity(packageName, "First"), activity(packageName, "Second"),
			activity("other.assistant", "Other"))
		val packageManager = mock<PackageManager> {
			on { queryIntentActivities(any(), any<Int>()) } doReturn activities
			on { getApplicationLabel(any()) } doReturn "Same label"
		}
		val notifications = mock<NotificationManager>()
		val context = mock<Context> {
			on { this.packageManager } doReturn packageManager
			on { getString(R.string.notification_channel_assistant) } doReturn "Assistant Launcher"
			on { getSystemService(NotificationManager::class.java) } doReturn notifications
		}
		mockConstruction(NotificationChannel::class.java).use {
			val controller = AssistantControllerAndroid(context, phoneAppResources)
			val assistants = controller.getAssistants()
			assertEquals(setOf(packageName, "other.assistant"), assistants.map { it.packageName }.toSet())
			assertEquals(2, assistants.size)
			assertTrue(assistants.all { it.name == "Same label" })
		}
		verify(phoneAppResources, times(1)).getAppIcon(packageName)
		verify(phoneAppResources, times(1)).getAppIcon("other.assistant")
	}
}
