package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration130to131_PlannedPaymentEndDate : Migration(130, 131) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE planned_payment_rules ADD COLUMN endDate INTEGER")
    }
} 