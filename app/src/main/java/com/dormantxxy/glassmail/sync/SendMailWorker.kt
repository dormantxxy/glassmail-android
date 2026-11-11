package com.dormantxxy.glassmail.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dormantxxy.glassmail.GlassMailApplication

class SendMailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(DRAFT_ID) ?: return Result.failure()
        val repository = (applicationContext as GlassMailApplication).repository
        return if (repository.sendQueued(id)) Result.success()
        else if (runAttemptCount < 4) Result.retry() else Result.failure()
    }

    companion object { const val DRAFT_ID = "draft_id" }
}
