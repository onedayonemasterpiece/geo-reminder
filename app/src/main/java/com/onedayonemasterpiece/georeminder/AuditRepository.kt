package com.onedayonemasterpiece.georeminder

import android.content.Context

class AuditRepository(context: Context) {
    internal val database = AuditDatabase.get(context.applicationContext)
}
