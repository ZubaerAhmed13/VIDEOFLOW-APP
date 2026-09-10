package com.videoflow.app.util
import java.util.Locale
fun formatBytes(v:Long?):String{ if(v==null)return "Unknown"; val units=arrayOf("B","KB","MB","GB","TB"); var n=v.toDouble(); var i=0; while(n>=1024&&i<units.lastIndex){n/=1024;i++}; return if(i==0)"$v B" else String.format(Locale.US,"%.2f %s",n,units[i]) }
fun formatDurationUs(us:Long?):String{ if(us==null)return "Unknown"; val s=us/1_000_000; return "%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) }


fun formatHumanDurationUs(us: Long?): String {
    if (us == null) return "Unknown"
    val safe = us.coerceAtLeast(0L)
    if (safe < 60_000_000L) {
        val tenths = ((safe + 50_000L) / 100_000L) / 10.0
        return if (tenths % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f sec", tenths)
        } else {
            String.format(Locale.US, "%.1f sec", tenths)
        }
    }
    val totalSeconds = (safe + 500_000L) / 1_000_000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        if (seconds == 0L) String.format(Locale.US, "%d hr %02d min", hours, minutes)
        else String.format(Locale.US, "%d hr %02d min %02d sec", hours, minutes, seconds)
    } else String.format(Locale.US, "%d min %02d sec", minutes, seconds)
}
