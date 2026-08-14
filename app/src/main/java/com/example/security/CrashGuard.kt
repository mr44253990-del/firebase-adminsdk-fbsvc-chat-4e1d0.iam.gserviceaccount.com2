package com.example.security

import android.content.Context
import com.example.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter

object CrashGuard {
    private const val PREF="convo_crash_guard"
    private const val KEY="last_crash"
    fun install(context:Context){
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler{thread,error->
            runCatching{val out=StringWriter();error.printStackTrace(PrintWriter(out));context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,"Convo ${BuildConfig.VERSION_NAME}\n${error.javaClass.name}: ${error.message}\n${out.toString().take(12000)}").putLong("time",System.currentTimeMillis()).commit()}
            previous?.uncaughtException(thread,error)
        }
    }
    fun lastCrash(context:Context):String?{val p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);val time=p.getLong("time",0);return p.getString(KEY,null)?.takeIf{System.currentTimeMillis()-time<24*60*60*1000L}}
    fun clear(context:Context){context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear().apply()}
}
