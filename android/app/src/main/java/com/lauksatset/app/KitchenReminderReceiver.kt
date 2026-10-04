package com.lauksatset.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.net.Uri

class KitchenReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("kitchen", "Pengingat dapur", NotificationManager.IMPORTANCE_HIGH))
        val id = intent.getIntExtra("id", 200)
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("lauksatset://kitchen")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(context, "kitchen").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(intent.getStringExtra("title") ?: "Pengingat dapur").setContentText(intent.getStringExtra("body") ?: "Periksa jadwal produksi.").setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra("body"))).setContentIntent(open).setAutoCancel(true).build())
    }
}
