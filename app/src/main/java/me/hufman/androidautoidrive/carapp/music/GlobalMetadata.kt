package me.hufman.androidautoidrive.carapp.music

import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.UnicodeCleaner
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.music.MusicAction
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.hasSameQueueDescriptions
import me.hufman.androidautoidrive.music.hasSameQueueItems
import java.io.IOException

class GlobalMetadata(app: RHMIApplication, var controller: MusicController) {
	val multimediaInfoEvent: RHMIEvent.MultimediaInfoEvent
	val statusbarEvent: RHMIEvent.StatusbarEvent
	val instrumentCluster: RHMIComponent.InstrumentCluster

	var displayedApp: MusicAppInfo? = null
	var displayedSong: MusicMetadata? = null
	var displayedQueue: List<MusicMetadata>? = null
	var icQueue: List<MusicMetadata> = ArrayList()
	private var hasPublishedQueue = false

	init {
		multimediaInfoEvent = app.events.values.filterIsInstance<RHMIEvent.MultimediaInfoEvent>().first()
		statusbarEvent = app.events.values.filterIsInstance<RHMIEvent.StatusbarEvent>().first()
		instrumentCluster = app.components.values.filterIsInstance<RHMIComponent.InstrumentCluster>().first()
	}

	companion object {
		val QUEUE_SKIPPREVIOUS = MusicMetadata(mediaId = "__QUEUE_SKIPBACK__", title="< ${L.MUSIC_SKIP_PREVIOUS}")
		val QUEUE_SKIPNEXT = MusicMetadata(mediaId = "__QUEUE_SKIPNEXT__", title="${L.MUSIC_SKIP_NEXT} >")
	}

	fun initWidgets() {
		instrumentCluster.getSetTrackAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { onClick(it) }
	}

	fun forgetDisplayedInfo() {
		displayedApp = null
		displayedSong = null
		displayedQueue = null
	}

	fun redraw() {
		val app = controller.currentAppInfo
		if (app != null && app != displayedApp) {
			showApp(app)
		}

		val song = controller.getMetadata()
		val songChanged = !(displayedSong === song || displayedSong?.hasSameQueueDescription(song) == true)
		if (song != null && songChanged) {
			showSong(song)
		}

		val queue = controller.getQueue()?.songs
		val queueChanged = !displayedQueue.hasSameQueueDescriptions(queue)
		if (queueChanged || songChanged) {
			icQueue = prepareQueue(queue, song)
			showQueue(icQueue, song)
			hasPublishedQueue = !queue.isNullOrEmpty()
		} else if (!displayedQueue.hasSameQueueItems(queue) && !queue.isNullOrEmpty()) {
			// Artwork or action extras may change without requiring a playlist transmission.
			icQueue = queue.toList()
		}

		displayedApp = app
		displayedSong = song
		if (!displayedQueue.hasSameQueueItems(queue)) displayedQueue = queue?.toList()
	}

	private fun showApp(app: MusicAppInfo) {
		// set the name of the app
		statusbarEvent.getTextModel()?.asRaDataModel()?.value = app.name
		statusbarEvent.triggerEvent()
	}

	private fun showSong(song: MusicMetadata) {
		// show in the sidebar
		val artistModel = multimediaInfoEvent.getTextModel1()?.asRaDataModel()
		val trackModel = multimediaInfoEvent.getTextModel2()?.asRaDataModel()
		artistModel?.value = UnicodeCleaner.clean(song.artist ?: "")
		trackModel?.value = UnicodeCleaner.clean(song.title ?: "")

		// show in the IC
		instrumentCluster.getTextModel()?.asRaDataModel()?.value = UnicodeCleaner.clean(song.title ?: "")

		// actually tell the car to load the data
		multimediaInfoEvent.triggerEvent()
	}

	/**
	 * Show the complete published queue. Use transport controls only when no queue is exposed.
	 */
	fun prepareQueue(songQueue: List<MusicMetadata>?, currentSong: MusicMetadata?): List<MusicMetadata> {
		if (!songQueue.isNullOrEmpty()) return songQueue.toList()
		val queue = ArrayList<MusicMetadata>(3)
		if (controller.isSupportedAction(MusicAction.SKIP_TO_PREVIOUS)) queue.add(QUEUE_SKIPPREVIOUS)
		if (currentSong != null) queue.add(currentSong)
		if (controller.isSupportedAction(MusicAction.SKIP_TO_NEXT)) queue.add(QUEUE_SKIPNEXT)
		return queue
	}

	private fun showQueue(queue: List<MusicMetadata>, currentSong: MusicMetadata?) {
		val adapter = object: RHMIModel.RaListModel.RHMIListAdapter<MusicMetadata>(7, queue) {
			override fun convertRow(index: Int, item: MusicMetadata): Array<Any> {
				val selected = item.matchesQueueItem(currentSong) || item === currentSong
				return arrayOf(
						index,  // index
						UnicodeCleaner.clean(item.title ?: ""),   // title
						UnicodeCleaner.clean(item.artist ?: ""),  // artist
						UnicodeCleaner.clean(item.album ?: ""),   // album
						-1,
						if (selected) 1 else 0, // checked
						true
				)
			}
		}

		try {
			instrumentCluster.getUseCaseModel()?.asRaDataModel()?.value = "EntICPlaylist"
			instrumentCluster.getPlaylistModel()?.asRaListModel()?.value = adapter
		} catch (e: IOException) {
			// This playlist model call has been observed to crash, for some reason
		}
	}

	private fun onClick(index: Int) {
		val song = icQueue.getOrNull(index) ?: return
		when {
			hasPublishedQueue -> controller.playQueue(song)
			song == QUEUE_SKIPPREVIOUS -> controller.skipToPrevious()
			song == QUEUE_SKIPNEXT -> controller.skipToNext()
			song == controller.getMetadata() -> controller.seekTo(0)
		}
	}

}
