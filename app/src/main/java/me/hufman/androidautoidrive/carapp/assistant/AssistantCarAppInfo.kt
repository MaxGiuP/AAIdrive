package me.hufman.androidautoidrive.carapp.assistant

import me.hufman.androidautoidrive.carapp.AMAppInfo
import me.hufman.androidautoidrive.carapp.L

/** Clarifies the car shortcut's function without changing the phone's app label or icon. */
class AssistantCarAppInfo(val assistant: AssistantAppInfo): AMAppInfo by assistant {
	override val name = L.ASSISTANT_APP_NAME.format(assistant.name)
}
