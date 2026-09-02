package com.videoflow.app.util
import java.util.Locale
fun formatBytes(v:Long?):String{ if(v==null)return "Unknown"; val units=arrayOf("B","KB","MB","GB","TB"); var n=v.toDouble(); var i=0; while(n>=1024&&i<units.lastIndex){n/=1024;i++}; return if(i==0)"$v B" else String.format(Locale.US,"%.2f %s",n,units[i]) }
fun formatDurationUs(us:Long?):String{ if(us==null)return "Unknown"; val s=us/1_000_000; return "%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) }
