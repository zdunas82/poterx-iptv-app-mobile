package tv.own.owntv.mobile.reminder

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.ReminderEntity
import tv.own.owntv.core.reminder.ReminderManager
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import java.text.DateFormat
import java.util.Date

/**
 * Programme reminders reaching a phone that is not looking.
 *
 * Core wakes the app when a reminder is due ([ReminderManager.due]); the television draws its prompt
 * over whatever is on screen, because a television is always looking. A phone is mostly in a pocket, so
 * while no screen of the app is open — or when the setting is Notify only — the reminder becomes a
 * notification. A tap opens the app, where the shell asks (or switches) as the setting says.
 */
object ReminderNotifier {

    private const val CHANNEL_ID = "owntv_reminders"
    private var started = 0

    /** Whether a screen of the app is open; the shell's prompt covers that case. */
    val inForeground: Boolean get() = started > 0

    private val notified = HashSet<Long>()

    fun start(app: Application, scope: CoroutineScope, reminders: ReminderManager, settings: SettingsRepository) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                // Left with a prompt unanswered: what is still due goes to the notification shade.
                if (started == 0) reminders.due.value.filter { it.id !in notified }.forEach { r ->
                    post(app, r)
                    notified += r.id
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        scope.launch {
            reminders.due.collect { due ->
                val mode = settings.reminderMode.first()
                due.filter { it.id !in notified }.forEach { r ->
                    if (mode == SettingsRepository.ReminderMode.NOTIFY) {
                        post(app, r)
                        notified += r.id
                        reminders.dismiss(r.id)
                    } else if (!inForeground) {
                        post(app, r)
                        notified += r.id
                    }
                }
            }
        }
    }

    /** The prompt was answered in the app: its notification goes too. */
    fun cancel(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toInt())

    private fun post(context: Context, r: ReminderEntity) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.settings_reminders), NotificationManager.IMPORTANCE_HIGH),
        )
        val times = DateFormat.getTimeInstance(DateFormat.SHORT)
        val text = listOf(
            context.getString(R.string.content_live_time_range_plain, times.format(Date(r.startMs)), times.format(Date(r.stopMs))),
            r.channelName,
        ).joinToString(context.getString(R.string.content_epg_bits_separator))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, r.id.toInt(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.owntv_notification)
            .setContentTitle(r.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // Refused notification permission: the reminder still comes up in the app when it is opened.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(r.id.toInt(), notification)
    }
}
